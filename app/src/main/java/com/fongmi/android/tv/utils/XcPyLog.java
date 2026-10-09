package com.fongmi.android.tv.utils;

import android.util.Log;

import com.fongmi.android.tv.App;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * File-based diagnostic logger for PY playback investigation.
 * Writes timestamped lines to <external-files-dir>/XC-PY.log so the log
 * can be opened with a file manager (no adb needed).
 * TEMP-DIAG: remove after the playback issue is diagnosed.
 */
public class XcPyLog {

    private static final String TAG = "XcPyLog";
    private static final SimpleDateFormat FMT = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    public synchronized static void d(String msg) {
        try {
            File dir = App.get().getExternalFilesDir(null);
            if (dir == null) return;
            FileWriter w = new FileWriter(new File(dir, "XC-PY.log"), true);
            w.write(FMT.format(new Date()) + " " + msg + "\n");
            w.close();
        } catch (Exception e) {
            Log.w(TAG, "write failed", e);
        }
    }
}
