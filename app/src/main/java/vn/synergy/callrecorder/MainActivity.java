package vn.synergy.callrecorder;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.graphics.Color;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout root,list;
    private TextView toggle,state;
    private MediaPlayer player;
    private File pendingExport;
    private static final int MIC=10, IMPORT=11, EXPORT=12;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){ public void run(){ updateState();handler.postDelayed(this,700); } };
    @Override public void onCreate(Bundle b){super.onCreate(b); build();handler.post(refresh);}
    @Override public void onResume(){super.onResume(); if(list!=null)populate();}
    @Override public void onDestroy(){handler.removeCallbacks(refresh);stopPlayer();super.onDestroy();}
    private int dp(int v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
    private TextView btn(String s, int bg) {
        TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(16);t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14),dp(15),dp(14),dp(15));t.setBackgroundTintList(null);t.setBackgroundColor(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(8));t.setLayoutParams(p);return t;
    }
    private TextView text(String s,int sp) {TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.rgb(30,41,59));t.setPadding(0,dp(8),0,dp(8));return t;}
    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(245,247,251));
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(28),dp(20),dp(30));scroll.addView(root);
        root.addView(text("SYNERGY CALL RECORDER",24));
        root.addView(text("Samsung Galaxy S21 • Giao diện tiếng Việt",14));
        TextView warn=text("Lưu ý: Ứng dụng chỉ ghi micro. Android không cho phép app thường tự ghi âm hai chiều cuộc gọi. Để ghi cuộc gọi, hãy dùng chức năng trong Samsung Phone (nếu máy hỗ trợ).",14);
        warn.setTextColor(Color.rgb(153,76,0));root.addView(warn);
        state=text("Sẵn sàng",17);root.addView(state);
        toggle=btn("● BẮT ĐẦU GHI ÂM MICRO",Color.rgb(30,96,209));toggle.setOnClickListener(v->toggle());root.addView(toggle);
        TextView samsung=btn("MỞ ĐIỆN THOẠI SAMSUNG",Color.rgb(71,85,105));samsung.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_DIAL);
            try{startActivity(i);}catch(Exception e){toast("Không mở được ứng dụng Điện thoại");}
        });root.addView(samsung);
        TextView importButton=btn("NHẬP FILE GHI ÂM",Color.rgb(15,118,110));importButton.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,IMPORT);
        });root.addView(importButton);
        root.addView(text("DANH SÁCH BẢN GHI",20));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);
        setContentView(scroll);populate();updateState();
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void toggle(){
        if(RecordingService.active){Intent i=new Intent(this,RecordingService.class);i.setAction(RecordingService.STOP);startService(i);handler.postDelayed(()->{populate();updateState();},700);return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC);return;
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},20);
        }
        startRecord();
    }
    private void startRecord(){
        Intent i=new Intent(this,RecordingService.class);i.setAction(RecordingService.START);
        try {startForegroundService(i);}catch(Exception e){toast("Không thể khởi động ghi âm: "+e.getMessage());}
        handler.postDelayed(this::updateState,400);
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);
        if(r==MIC){if(g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED)startRecord();else toast("Cần quyền micro để ghi âm");}
    }
    private void updateState(){
        if(toggle==null)return;
        boolean active=RecordingService.active;
        toggle.setText(active?"■ DỪNG VÀ LƯU BẢN GHI":"● BẮT ĐẦU GHI ÂM MICRO");
        toggle.setBackgroundColor(active?Color.rgb(190,35,45):Color.rgb(30,96,209));
        state.setText(active?"ĐANG GHI ÂM MICRO — có thông báo trên điện thoại":(RecordingService.lastError.isEmpty()?"Sẵn sàng ghi âm":RecordingService.lastError));
    }
    private File directory(){File d=new File(getFilesDir(),"recordings");d.mkdirs();return d;}
    private void populate(){
        if(list==null)return;list.removeAllViews();
        File[] all=directory().listFiles((dir,n)->n.toLowerCase(Locale.ROOT).endsWith(".m4a")||n.toLowerCase(Locale.ROOT).endsWith(".mp3")||n.toLowerCase(Locale.ROOT).endsWith(".wav"));
        if(all==null||all.length==0){list.addView(text("Chưa có bản ghi. Bấm Bắt đầu ghi âm để thử.",14));return;}
        Arrays.sort(all,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for(File f:all){
            LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setPadding(dp(12),dp(9),dp(12),dp(12));item.setBackgroundColor(Color.WHITE);
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.setMargins(0,0,0,dp(10));item.setLayoutParams(ip);
            item.addView(text(f.getName()+"  ("+(f.length()/1024)+" KB)",14));
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            TextView play=text("▶ Nghe",14);play.setTextColor(Color.BLUE);play.setPadding(dp(6),dp(9),dp(17),dp(9));play.setOnClickListener(v->play(f));row.addView(play);
            TextView save=text("⇧ Xuất file",14);save.setTextColor(Color.rgb(5,120,90));save.setPadding(dp(6),dp(9),dp(17),dp(9));save.setOnClickListener(v->export(f));row.addView(save);
            TextView del=text("✕ Xóa",14);del.setTextColor(Color.RED);del.setPadding(dp(6),dp(9),dp(9),dp(9));
            del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Xóa bản ghi?").setMessage(f.getName()).setNegativeButton("Hủy",null).setPositiveButton("Xóa",(d,w)->{if(f.delete())populate();else toast("Không thể xóa");}).show());row.addView(del);
            item.addView(row);list.addView(item);
        }
    }
    private void stopPlayer(){if(player!=null){try{player.stop();player.release();}catch(Exception ignored){}player=null;}}
    private void play(File f){
        stopPlayer();try{player=new MediaPlayer();player.setDataSource(f.getAbsolutePath());player.prepare();player.start();player.setOnCompletionListener(m->stopPlayer());}
        catch(Exception e){stopPlayer();toast("Không thể phát: "+e.getMessage());}
    }
    private void export(File f){
        pendingExport=f;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");i.putExtra(Intent.EXTRA_TITLE,f.getName());startActivityForResult(i,EXPORT);
    }
    private void copy(Uri uri,File file,boolean toUri)throws Exception {
        try(InputStream in=toUri?new FileInputStream(file):getContentResolver().openInputStream(uri);
            OutputStream out=toUri?getContentResolver().openOutputStream(uri):new FileOutputStream(file)){
            if(in==null||out==null)throw new IOException("Không thể mở file");
            byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
        }
    }
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(result!=RESULT_OK||data==null)return;
        if(code==EXPORT&&pendingExport!=null&&data.getData()!=null){
            File file=pendingExport;pendingExport=null;
            new Thread(()->{try{copy(data.getData(),file,true);runOnUiThread(()->toast("Đã xuất bản ghi"));}
            catch(Exception e){runOnUiThread(()->toast("Lỗi xuất file: "+e.getMessage()));}}).start();
        }
        if(code==IMPORT){
            ArrayList<Uri> uris=new ArrayList<>();
            if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}
            else if(data.getData()!=null)uris.add(data.getData());
            new Thread(()->{
                int success=0;
                for(Uri uri:uris){try{
                    String ext=".m4a";String path=uri.toString().toLowerCase(Locale.ROOT);
                    if(path.endsWith(".mp3"))ext=".mp3";else if(path.endsWith(".wav"))ext=".wav";
                    File dest=new File(directory(),"NHAP_"+System.currentTimeMillis()+"_"+success+ext);
                    copy(uri,dest,false);success++;
                }catch(Exception ignored){}}
                int total=success;runOnUiThread(()->{toast("Đã nhập "+total+" bản ghi");populate();});
            }).start();
        }
    }
}
