package com.myfixxer.quietline.trigger;

import java.util.concurrent.CopyOnWriteArrayList;

/** Lets the services tell the plugin (and so the UI) that something changed. */
public final class TriggerState {
    public interface Listener {
        void onChanged();
    }

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    public static volatile String lastError = null;

    private TriggerState() {}

    public static void add(Listener l) { if (l != null) listeners.add(l); }

    public static void remove(Listener l) { if (l != null) listeners.remove(l); }

    public static void notifyChanged() {
        for (Listener l : listeners) {
            try { l.onChanged(); } catch (Exception ignored) {}
        }
    }

    public static void setError(String message) {
        lastError = message;
        notifyChanged();
    }
}
