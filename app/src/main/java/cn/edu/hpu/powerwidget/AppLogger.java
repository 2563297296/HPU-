package cn.edu.hpu.powerwidget;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Small rotating persistent log; logging never breaks app operation. */
final class AppLogger {
    private static final Object LOCK = new Object();
    private static final int MAX_BYTES = 128 * 1024;
    private static Context appContext;
    private AppLogger() {}

    static void init(Context context) { synchronized (LOCK) { appContext = context == null ? null : context.getApplicationContext(); } }
    static void info(String message) { append("INFO", message); }
    static void error(String message) { append("ERROR", message); }
    static File logFile(Context context) { return new File(context.getFilesDir(), "app_log.txt"); }

    private static void append(String level, String message) {
        synchronized (LOCK) {
            if (appContext == null) return;
            try {
                File file = logFile(appContext);
                String line = PowerDates.dateTime(System.currentTimeMillis()) + " " + level + " " + (message == null ? "" : message) + "\n";
                byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
                if (file.isFile() && file.length() + bytes.length > MAX_BYTES) rotate(file);
                FileOutputStream output = new FileOutputStream(file, true);
                try { output.write(bytes); } finally { output.close(); }
            } catch (Exception ignored) {}
        }
    }

    private static void rotate(File file) throws IOException {
        File backup = new File(file.getParentFile(), file.getName() + ".old");
        if (backup.isFile()) backup.delete();
        if (!file.renameTo(backup)) file.delete();
    }
}
