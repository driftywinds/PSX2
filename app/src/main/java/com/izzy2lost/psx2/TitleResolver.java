package com.izzy2lost.psx2;

import android.content.Context;
import android.net.Uri;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Loads game title index from resources/GameIndex.yaml or resources/RedumpDatabase.yaml
 * and resolves human titles from serials extracted via native APIs.
 */
public final class TitleResolver {
    private static Map<String, String> sSerialToTitle; // UPPERCASE SERIAL -> Title
    private static boolean sLoaded;

    private TitleResolver() {}

    public static synchronized void ensureLoaded(Context ctx) {
        if (sLoaded && sSerialToTitle != null) return;
        sSerialToTitle = new HashMap<>();
        File base = ctx.getExternalFilesDir(null);
        if (base == null) base = ctx.getFilesDir();
        File resDir = new File(base, "resources");
        // Prefer JSON cache if available
        File jsonCache = new File(resDir, "gameindex.json");
        if (loadJsonIfPresent(jsonCache, sSerialToTitle)) {
            sLoaded = true;
            return;
        }
        // Try both files if present
        loadYamlSafe(new File(resDir, "GameIndex.yaml"), sSerialToTitle);
        loadYamlSafe(new File(resDir, "RedumpDatabase.yaml"), sSerialToTitle);
        // Write JSON cache for faster subsequent loads
        writeJsonSafe(jsonCache, sSerialToTitle);
        sLoaded = true;
    }

    public static String resolveTitleForUri(Context ctx, String uriString, String fallback) {
        try {
            // 1) Check per-URI cache
            String cached = getCachedTitle(ctx, uriString);
            if (cached != null && !cached.isEmpty()) return cached;

            // 2) Ensure index loaded (JSON fast path or YAML)
            ensureLoaded(ctx);

            // 3) Resolve serial via native; if missing, try filename hint
            String serial = null;
            try { serial = NativeApp.getGameSerial(uriString); } catch (Throwable ignored) {}
            if (serial == null || serial.isEmpty()) {
                Uri u = Uri.parse(uriString);
                String name = u.getLastPathSegment();
                if (name != null) serial = normalizeCandidate(name);
            }

            // 4) Lookup in index
            if (serial != null) {
                serial = normalizeSerial(serial);
                String title = sSerialToTitle.get(serial);
                if (title != null && !title.isEmpty()) {
                    putCachedTitle(ctx, uriString, title);
                    return title;
                }
            }

            // 5) Fallback to native URI title if available
            String nativeTitle = null;
            try { nativeTitle = NativeApp.getGameTitleFromUri(uriString); } catch (Throwable ignored) {}
            if (nativeTitle != null && !nativeTitle.isEmpty()) {
                putCachedTitle(ctx, uriString, nativeTitle);
                return nativeTitle;
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    private static void loadYamlSafe(File file, Map<String, String> out) {
        if (file == null || !file.exists()) return;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            String pendingSerial = null;
            int currentIndent = 0;
            while ((line = br.readLine()) != null) {
                String l = line.trim();
                if (l.isEmpty() || l.startsWith("#")) continue;
                // Detect top-level or map-key serial of the form SERIAL:
                String serialKey = extractSerialMapKey(line);
                if (serialKey != null) {
                    pendingSerial = normalizeSerial(serialKey);
                    currentIndent = leadingSpaces(line);
                    continue;
                }
                // If inside a serial block, parse a name/title field at greater indent
                if (pendingSerial != null) {
                    int indent = leadingSpaces(line);
                    if (indent <= currentIndent) {
                        // Out of this block
                        pendingSerial = null;
                        continue;
                    }
                    String title = extractTitleFromLine(l);
                    if (title != null) {
                        out.put(pendingSerial, title);
                        pendingSerial = null;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static boolean loadJsonIfPresent(File file, Map<String, String> out) {
        if (file == null || !file.exists()) return false;
        try (FileInputStream fis = new FileInputStream(file)) {
            InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder(1 << 20);
            char[] buf = new char[4096];
            int n;
            while ((n = isr.read(buf)) != -1) sb.append(buf, 0, n);
            JSONObject obj = new JSONObject(new JSONTokener(sb.toString()));
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                String v = obj.optString(k, null);
                if (v != null && !v.isEmpty()) out.put(k, v);
            }
            return true;
        } catch (Exception ignored) {}
        return false;
    }

    private static void writeJsonSafe(File file, Map<String, String> map) {
        if (file == null) return;
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) file.getParentFile().mkdirs();
            JSONObject obj = new JSONObject(map);
            byte[] bytes = obj.toString().getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream fos = new FileOutputStream(file, false)) {
                fos.write(bytes);
                fos.flush();
            }
        } catch (Exception ignored) {}
    }

    private static String getCachedTitle(Context ctx, String uri) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences("title_cache", Context.MODE_PRIVATE);
            return prefs.getString(uri, null);
        } catch (Throwable ignored) {}
        return null;
    }

    private static void putCachedTitle(Context ctx, String uri, String title) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences("title_cache", Context.MODE_PRIVATE);
            prefs.edit().putString(uri, title).apply();
        } catch (Throwable ignored) {}
    }

    private static String extractTitleFromLine(String l) {
        // Common YAML keys
        int idx = indexOfKey(l, "name:");
        if (idx < 0) idx = indexOfKey(l, "title:");
        if (idx < 0) return null;
        String v = l.substring(idx).trim();
        // Strip key
        int colon = v.indexOf(':');
        if (colon >= 0) v = v.substring(colon + 1).trim();
        // Trim quotes if present
        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
            v = v.substring(1, v.length() - 1);
        }
        return v.isEmpty() ? null : v;
    }

    private static int indexOfKey(String l, String key) {
        int i = l.toLowerCase(Locale.ROOT).indexOf(key);
        return i;
    }

    private static String extractSerialFromLine(String l) {
        String upper = l.toUpperCase(Locale.ROOT);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([A-Z]{4,5})[- _]?([0-9]{3})[._]?([0-9]{2})")
                .matcher(upper);
        if (m.find()) return m.group(1) + "-" + m.group(2) + m.group(3);
        return null;
    }

    private static String extractSerialMapKey(String rawLine) {
        // Matches: "SLUS-20312:" or "  SLUS_203.12:" (with indentation) at start of key
        int colon = rawLine.indexOf(':');
        if (colon <= 0) return null;
        String key = rawLine.substring(0, colon).trim();
        String s = extractSerialFromLine(key);
        // Ensure the whole key is a serial, not just contains one
        if (s != null) {
            String normalizedKey = key.toUpperCase(Locale.ROOT).replace('_','-').replace(".", "");
            String normalizedSerial = s.toUpperCase(Locale.ROOT).replace('_','-');
            if (normalizedKey.replace("-", "").equals(normalizedSerial.replace("-", ""))) {
                return s;
            }
        }
        return null;
    }

    private static int leadingSpaces(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    private static String normalizeCandidate(String s) {
        if (s == null) return null;
        String upper = s.toUpperCase(Locale.ROOT).replace('_', '-');
        // Try to extract serial
        return extractSerialFromLine(upper);
    }

    private static String normalizeSerial(String serial) {
        String s = serial.toUpperCase(Locale.ROOT).replace('_', '-');
        s = s.replaceAll("([A-Z]{4,5})-([0-9]{3})\\.([0-9]{2})", "$1-$2$3");
        return s;
    }
}
