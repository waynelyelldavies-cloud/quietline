package com.myfixxer.quietline.trigger;

import android.content.Context;

import java.io.File;

/** Recordings live in app-private internal storage, not visible to other apps or over USB. */
public final class RecordingStore {
    private RecordingStore() {}

    public static File dir(Context ctx) {
        File d = new File(ctx.getFilesDir(), "recordings");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Returns the file only if the name is a plain file name inside the recordings folder. */
    public static File resolve(Context ctx, String name) {
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\") || name.startsWith(".")) {
            return null;
        }
        File f = new File(dir(ctx), name);
        return f.exists() ? f : null;
    }
}
