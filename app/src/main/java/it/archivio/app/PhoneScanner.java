package it.archivio.app;

import android.os.Environment;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PhoneScanner {
    public static List<File> scan(AtomicBoolean stop) {
        ArrayList<File> out = new ArrayList<>();
        File root = Environment.getExternalStorageDirectory();
        walk(root, out, stop);
        return out;
    }

    private static void walk(File f, List<File> out, AtomicBoolean stop) {
        if (stop.get() || f == null || !f.exists()) return;
        if (f.isFile()) {
            if (supported(f.getName())) out.add(f);
            return;
        }
        String p = f.getAbsolutePath();
        if (p.contains("/Android/data/") || p.contains("/Android/obb/")) return;
        File[] arr;
        try { arr = f.listFiles(); } catch (Exception e) { return; }
        if (arr == null) return;
        for (File x : arr) {
            if (stop.get()) return;
            walk(x, out, stop);
        }
    }

    public static boolean supported(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".pdf") || n.endsWith(".jpg") || n.endsWith(".jpeg") ||
               n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".txt") ||
               n.endsWith(".csv") || n.endsWith(".log") || n.endsWith(".docx") ||
               n.endsWith(".xlsx") || n.endsWith(".rtf") || n.endsWith(".zip") ||
               n.endsWith(".eml");
    }
}
