package com.myfixxer.quietline.trigger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Records microphone audio to app-private storage as a foreground service. */
public class RecordingService extends Service {
    private static final String TAG = "QuietlineRecorder";
    public static final String ACTION_START = "com.myfixxer.quietline.trigger.START";
    public static final String ACTION_STOP = "com.myfixxer.quietline.trigger.STOP";
    public static final String EXTRA_SOURCE = "source";

    private static final String CHANNEL_ID = "quietline_active";
    private static final int NOTIFICATION_ID = 4107;

    private static volatile boolean recording = false;
    private static volatile String currentName = null;
    private static volatile long startedAt = 0;

    private MediaRecorder recorder;
    private File currentFile;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoStop = this::finish;

    public static boolean isRecording() { return recording; }
    public static String currentName() { return currentName; }
    public static long startedAt() { return startedAt; }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            finish();
            return START_NOT_STICKY;
        }

        // Must go foreground straight away when started with startForegroundService.
        try {
            goForeground();
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
            TriggerState.setError("Android did not allow recording to start: " + e.getClass().getSimpleName());
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!recording) begin();
        return START_NOT_STICKY;
    }

    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Background service", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setSound(null, null);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
        }

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Intent stopIntent = new Intent(this, RecordingService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent, piFlags);

        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent openPi = open != null ? PendingIntent.getActivity(this, 2, open, piFlags) : null;

        // Deliberately plain wording so it doesn't draw attention on the lock screen.
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("Service running")
            .setContentText("Tap to open")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(openPi)
            .addAction(0, "Stop", stopPi)
            .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private void begin() {
        TriggerSettings s = TriggerSettings.load(this);
        String name = "rec_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".m4a";
        currentFile = new File(RecordingStore.dir(this), name);

        try {
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(64000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(currentFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            Log.e(TAG, "Recorder failed to start", e);
            releaseRecorder();
            if (currentFile != null) currentFile.delete();
            currentFile = null;
            TriggerState.setError("The microphone could not start recording. Another app may be using it.");
            if (s.vibrate) Buzz.pulses(this, 3);
            stopForegroundCompat();
            stopSelf();
            return;
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "quietline:recording");
            wakeLock.acquire((s.maxMinutes + 1) * 60_000L);
        }

        recording = true;
        currentName = name;
        startedAt = System.currentTimeMillis();
        TriggerState.lastError = null;
        handler.postDelayed(autoStop, s.maxMinutes * 60_000L);
        if (s.vibrate) Buzz.pulses(this, 1);
        TriggerState.notifyChanged();
    }

    private void finish() {
        handler.removeCallbacks(autoStop);
        boolean wasRecording = recording;

        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException e) {
                // Happens if stopped within a fraction of a second: the file has no audio.
                if (currentFile != null) currentFile.delete();
            }
            releaseRecorder();
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;

        recording = false;
        currentName = null;
        startedAt = 0;
        currentFile = null;

        if (wasRecording && TriggerSettings.load(this).vibrate) Buzz.pulses(this, 2);
        stopForegroundCompat();
        stopSelf();
        TriggerState.notifyChanged();
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try { recorder.release(); } catch (Exception ignored) {}
            recorder = null;
        }
    }

    @SuppressWarnings("deprecation")
    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    @Override
    public void onDestroy() {
        if (recording) finish();
        super.onDestroy();
    }
}
