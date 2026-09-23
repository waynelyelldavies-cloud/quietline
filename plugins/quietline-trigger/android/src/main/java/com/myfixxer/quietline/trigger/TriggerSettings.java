package com.myfixxer.quietline.trigger;

import android.content.Context;
import android.content.SharedPreferences;

import com.getcapacitor.JSObject;

/** Trigger settings, shared between the app UI and the background services. */
public class TriggerSettings {
    private static final String PREFS = "quietline_trigger";

    public boolean enabled = true;
    public String mode = "presses";      // "presses" or "hold"
    public int pressCount = 3;           // presses needed in "presses" mode
    public int windowMs = 1500;          // time window for the presses
    public int holdMs = 2000;            // hold time in "hold" mode
    public boolean vibrate = true;       // short buzz when recording starts/stops
    public boolean patternStops = false; // repeat the pattern to stop recording
    public int maxMinutes = 60;          // auto-stop after this long

    public static TriggerSettings load(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        TriggerSettings s = new TriggerSettings();
        s.enabled = p.getBoolean("enabled", s.enabled);
        s.mode = p.getString("mode", s.mode);
        s.pressCount = p.getInt("pressCount", s.pressCount);
        s.windowMs = p.getInt("windowMs", s.windowMs);
        s.holdMs = p.getInt("holdMs", s.holdMs);
        s.vibrate = p.getBoolean("vibrate", s.vibrate);
        s.patternStops = p.getBoolean("patternStops", s.patternStops);
        s.maxMinutes = p.getInt("maxMinutes", s.maxMinutes);
        return s;
    }

    public void save(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", enabled)
            .putString("mode", mode)
            .putInt("pressCount", pressCount)
            .putInt("windowMs", windowMs)
            .putInt("holdMs", holdMs)
            .putBoolean("vibrate", vibrate)
            .putBoolean("patternStops", patternStops)
            .putInt("maxMinutes", maxMinutes)
            .apply();
    }

    /** Applies any values present in the object, clamped to safe ranges. */
    public void merge(JSObject o) {
        if (o == null) return;
        enabled = bool(o, "enabled", enabled);
        if (o.has("mode")) {
            String m = o.getString("mode");
            if ("hold".equals(m) || "presses".equals(m)) mode = m;
        }
        pressCount = clamp(o.getInteger("pressCount"), 2, 6, pressCount);
        windowMs = clamp(o.getInteger("windowMs"), 600, 4000, windowMs);
        holdMs = clamp(o.getInteger("holdMs"), 1000, 6000, holdMs);
        vibrate = bool(o, "vibrate", vibrate);
        patternStops = bool(o, "patternStops", patternStops);
        maxMinutes = clamp(o.getInteger("maxMinutes"), 1, 240, maxMinutes);
    }

    public JSObject toJson() {
        JSObject o = new JSObject();
        o.put("enabled", enabled);
        o.put("mode", mode);
        o.put("pressCount", pressCount);
        o.put("windowMs", windowMs);
        o.put("holdMs", holdMs);
        o.put("vibrate", vibrate);
        o.put("patternStops", patternStops);
        o.put("maxMinutes", maxMinutes);
        return o;
    }

    private static boolean bool(JSObject o, String key, boolean fallback) {
        Boolean b = o.getBool(key);
        return b != null ? b : fallback;
    }

    private static int clamp(Integer v, int min, int max, int fallback) {
        if (v == null) return fallback;
        return Math.max(min, Math.min(max, v));
    }
}
