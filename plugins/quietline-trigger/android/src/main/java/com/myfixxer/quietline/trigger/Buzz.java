package com.myfixxer.quietline.trigger;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Discreet haptic feedback: one pulse = started, two = stopped, three = failed. */
final class Buzz {
    private Buzz() {}

    static void pulses(Context ctx, int count) {
        try {
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            long[] pattern = new long[count * 2];
            for (int i = 0; i < count; i++) {
                pattern[i * 2] = i == 0 ? 0 : 120;
                pattern[i * 2 + 1] = 90;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                v.vibrate(pattern, -1);
            }
        } catch (Exception ignored) {}
    }
}
