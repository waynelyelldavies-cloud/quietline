package com.myfixxer.quietline.trigger;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.File;
import java.util.Arrays;

@CapacitorPlugin(
    name = "EmergencyTrigger",
    permissions = {
        @Permission(alias = "microphone", strings = { Manifest.permission.RECORD_AUDIO }),
        @Permission(alias = "notifications", strings = { "android.permission.POST_NOTIFICATIONS" })
    }
)
public class EmergencyTriggerPlugin extends Plugin {
    private TriggerState.Listener listener;

    @Override
    public void load() {
        listener = () -> notifyListeners("stateChanged", buildStatus());
        TriggerState.add(listener);
    }

    @Override
    protected void handleOnDestroy() {
        TriggerState.remove(listener);
    }

    @Override
    protected void handleOnResume() {
        // Coming back from Accessibility settings: refresh the UI.
        notifyListeners("stateChanged", buildStatus());
    }

    // ---------- status ----------

    @PluginMethod
    public void getStatus(PluginCall call) {
        call.resolve(buildStatus());
    }

    private JSObject buildStatus() {
        Context ctx = getContext();
        JSObject o = new JSObject();
        o.put("accessibilityEnabled", isAccessibilityEnabled(ctx));
        o.put("triggerConnected", VolumeTriggerService.isConnected());
        o.put("microphone", granted(ctx, Manifest.permission.RECORD_AUDIO));
        o.put("notifications", Build.VERSION.SDK_INT < 33 || granted(ctx, "android.permission.POST_NOTIFICATIONS"));
        o.put("recording", RecordingService.isRecording());
        o.put("recordingStartedAt", RecordingService.startedAt());
        o.put("lastError", TriggerState.lastError);
        o.put("settings", TriggerSettings.load(ctx).toJson());
        return o;
    }

    private static boolean granted(Context ctx, String perm) {
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isAccessibilityEnabled(Context ctx) {
        String enabled = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName mine = new ComponentName(ctx, VolumeTriggerService.class);
        for (String part : enabled.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(part);
            if (mine.equals(cn)) return true;
        }
        return false;
    }

    // ---------- setup ----------

    @PluginMethod
    public void requestAppPermissions(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissionForAliases(new String[] { "microphone", "notifications" }, call, "permissionsDone");
        } else {
            requestPermissionForAlias("microphone", call, "permissionsDone");
        }
    }

    @PermissionCallback
    private void permissionsDone(PluginCall call) {
        call.resolve(buildStatus());
    }

    @PluginMethod
    public void openAccessibilitySettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void openAppSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", getContext().getPackageName(), null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void setSettings(PluginCall call) {
        TriggerSettings s = TriggerSettings.load(getContext());
        s.merge(call.getData());
        s.save(getContext());
        call.resolve(buildStatus());
    }

    // ---------- recording ----------

    @PluginMethod
    public void startRecording(PluginCall call) {
        if (!granted(getContext(), Manifest.permission.RECORD_AUDIO)) {
            call.reject("Microphone permission is off.");
            return;
        }
        Intent i = new Intent(getContext(), RecordingService.class)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_SOURCE, "app");
        try {
            ContextCompat.startForegroundService(getContext(), i);
            call.resolve();
        } catch (Exception e) {
            call.reject("Could not start recording: " + e.getMessage());
        }
    }

    @PluginMethod
    public void stopRecording(PluginCall call) {
        Intent i = new Intent(getContext(), RecordingService.class).setAction(RecordingService.ACTION_STOP);
        try {
            getContext().startService(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("Could not stop recording: " + e.getMessage());
        }
    }

    @PluginMethod
    public void clearError(PluginCall call) {
        TriggerState.lastError = null;
        call.resolve(buildStatus());
    }

    // ---------- recordings list ----------

    @PluginMethod
    public void listRecordings(PluginCall call) {
        File[] files = RecordingStore.dir(getContext()).listFiles();
        JSArray arr = new JSArray();
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            String current = RecordingService.currentName();
            for (File f : files) {
                if (!f.isFile()) continue;
                JSObject o = new JSObject();
                boolean inProgress = f.getName().equals(current);
                o.put("name", f.getName());
                o.put("path", f.getAbsolutePath());
                o.put("size", f.length());
                o.put("modified", f.lastModified());
                o.put("inProgress", inProgress);
                o.put("durationMs", inProgress ? 0 : durationOf(f));
                arr.put(o);
            }
        }
        JSObject res = new JSObject();
        res.put("recordings", arr);
        call.resolve(res);
    }

    private static long durationOf(File f) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(f.getAbsolutePath());
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d != null ? Long.parseLong(d) : 0;
        } catch (Exception e) {
            return 0;
        } finally {
            try { r.release(); } catch (Exception ignored) {}
        }
    }

    @PluginMethod
    public void deleteRecording(PluginCall call) {
        String name = call.getString("name");
        if (name != null && name.equals(RecordingService.currentName())) {
            call.reject("Stop the recording before deleting it.");
            return;
        }
        File f = RecordingStore.resolve(getContext(), name);
        if (f == null) {
            call.reject("Recording not found.");
            return;
        }
        if (f.delete()) call.resolve();
        else call.reject("Could not delete the recording.");
    }

    @PluginMethod
    public void shareRecording(PluginCall call) {
        File f = RecordingStore.resolve(getContext(), call.getString("name"));
        if (f == null) {
            call.reject("Recording not found.");
            return;
        }
        Context ctx = getContext();
        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".quietline.files", f);
        Intent send = new Intent(Intent.ACTION_SEND)
            .setType("audio/mp4")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(send, "Share recording");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ctx.startActivity(chooser);
        call.resolve();
    }
}
