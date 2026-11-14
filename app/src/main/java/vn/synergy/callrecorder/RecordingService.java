package vn.synergy.callrecorder;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class RecordingService extends Service {
    public static final String START="vn.synergy.callrecorder.START";
    public static final String STOP="vn.synergy.callrecorder.STOP";
    public static boolean active=false;
    public static String lastError="";
    private MediaRecorder recorder;
    private File output;
    private static final String CHANNEL="recording";
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if (intent!=null && STOP.equals(intent.getAction())) { stopRecording(); stopSelf(); return START_NOT_STICKY; }
        if (active) return START_NOT_STICKY;
        createChannel();
        Notification notice = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("SYNERGY đang ghi âm micro")
            .setContentText("Mở ứng dụng để dừng ghi âm")
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this,0,new Intent(this, MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT))
            .build();
        try {
            if (Build.VERSION.SDK_INT>=29) startForeground(100,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(100,notice);
            File dir=new File(getFilesDir(),"recordings");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Không thể tạo thư mục");
            String name="SYNERGY_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".m4a";
            output=new File(dir,name);
            recorder=new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(output.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            active=true; lastError="";
        } catch(Exception e) {
            lastError="Lỗi micro: "+e.getMessage();
            release(false); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        }
        return START_NOT_STICKY;
    }
    private void createChannel() {
        NotificationManager m=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        m.createNotificationChannel(new NotificationChannel(CHANNEL,"Ghi âm SYNERGY",NotificationManager.IMPORTANCE_LOW));
    }
    private void stopRecording() { release(true); stopForeground(STOP_FOREGROUND_REMOVE); }
    private void release(boolean keep) {
        if(recorder!=null) {
            try {recorder.stop();} catch(Exception e){keep=false;}
            try {recorder.reset();recorder.release();}catch(Exception ignored){}
            recorder=null;
        }
        if(!keep && output!=null) output.delete();
        active=false;
    }
    @Override public void onDestroy() { if(active) stopRecording(); super.onDestroy(); }
    @Override public IBinder onBind(Intent i) {return null;}
}
