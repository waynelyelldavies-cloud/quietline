package com.myfixxer.quietline.trigger;

import androidx.core.content.FileProvider;

/** Own subclass so it doesn't clash with Capacitor's FileProvider. */
public class RecordingsFileProvider extends FileProvider {}
