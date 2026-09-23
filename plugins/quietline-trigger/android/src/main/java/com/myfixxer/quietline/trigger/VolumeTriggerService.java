package com.myfixxer.quietline.trigger;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import androidx.core.content.ContextCompat;

import java.util.ArrayDeque;

/**
 * Watches ONLY the volume-down key. Never consumes the key press, so the volume
 * still changes normally and nothing looks different to someone watching.
 */
public class VolumeTriggerService extends AccessibilityService {
    private static final String TAG = "QuietlineTrigger";
    private static volatile boolean connected = false;

    private final ArrayDeque<Long> presses = new ArrayDeque<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable holdRunnable;
    private long lastFiredAt = 0;

    public static boolean isConnected() {
        return connected;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        TriggerState.notifyChanged();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        connected = false;
        cancelHold();
        TriggerState.notifyChanged();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        connected = false;
        cancelHold();
        TriggerState.notifyChanged();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used. We only listen to key events.
    }

    @Override
    public void onInterrupt() {}

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        TriggerSettings s = TriggerSettings.load(this);
        if (!s.enabled) return false;

        if ("hold".equals(s.mode)) {
            handleHold(event, s);
        } else {
            handlePresses(event, s);
        }
        return false; // never swallow the key
    }

    private void handlePresses(KeyEvent event, TriggerSettings s) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) return;
        long now = SystemClock.uptimeMillis();
        presses.addLast(now);
        while (!presses.isEmpty() && now - presses.peekFirst() > s.windowMs) {
            presses.removeFirst();
        }
        if (presses.size() >= s.pressCount) {
            presses.clear();
            fire(s);
        }
    }

    private void handleHold(KeyEvent event, TriggerSettings s) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            cancelHold();
            holdRunnable = () -> fire(TriggerSettings.load(this));
            handler.postDelayed(holdRunnable, s.holdMs);
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            cancelHold();
        }
    }

    private void cancelHold() {
        if (holdRunnable != null) {
            handler.removeCallbacks(holdRunnable);
            holdRunnable = null;
        }
    }

    private void fire(TriggerSettings s) {
        long now = SystemClock.uptimeMillis();
        if (now - lastFiredAt < 1500) return; // debounce
        lastFiredAt = now;

        if (RecordingService.isRecording()) {
            if (s.patternStops) {
                Intent stop = new Intent(this, RecordingService.class).setAction(RecordingService.ACTION_STOP);
                try {
                    startService(stop);
                } catch (Exception e) {
                    Log.w(TAG, "Could not stop recording", e);
                }
            }
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            TriggerState.setError("Microphone permission is off, so the trigger could not record.");
            if (s.vibrate) Buzz.pulses(this, 3);
            return;
        }

        Intent start = new Intent(this, RecordingService.class)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_SOURCE, "trigger");
        try {
            ContextCompat.startForegroundService(this, start);
        } catch (Exception e) {
            Log.e(TAG, "Could not start recording service", e);
            TriggerState.setError("Android blocked the recording from starting: " + e.getClass().getSimpleName());
            if (s.vibrate) Buzz.pulses(this, 3);
        }
    }
}
