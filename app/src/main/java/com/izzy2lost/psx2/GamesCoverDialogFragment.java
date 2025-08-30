package com.izzy2lost.psx2;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.app.Dialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Locale;

public class GamesCoverDialogFragment extends DialogFragment {
    private boolean didInitialNudge = false;
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_FRAME, R.style.AppTheme);
    }
    private CoversAdapter adapter;
    private String[] titles;
    private String[] uris;
    private String[] coverUrls;
    private String[] localPaths;
    private RecyclerView rv;
    private LinearLayoutManager llm;
    private PagerSnapHelper snapHelper;
    private int lastRvW = -1, lastRvH = -1;
    private boolean pendingResnap = false;

    public interface OnGameSelectedListener {
        void onGameSelected(String gameUri);
    }

    private static final String ARG_TITLES = "titles";
    private static final String ARG_URIS = "uris";

    public static GamesCoverDialogFragment newInstance(String[] titles, String[] uris) {
        GamesCoverDialogFragment f = new GamesCoverDialogFragment();
        Bundle b = new Bundle();
        b.putStringArray(ARG_TITLES, titles);
        b.putStringArray(ARG_URIS, uris);
        f.setArguments(b);
        return f;
    }

    private OnGameSelectedListener listener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnGameSelectedListener) {
            listener = (OnGameSelectedListener) context;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (rv != null) applyCoverflowTransforms(rv);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        // Return a styled dialog; content is provided by onCreateView
        return new Dialog(requireContext(), R.style.PSX2_FullScreenDialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.dialog_covers_grid, container, false);

        rv = root.findViewById(R.id.recycler_covers);
        rv.setHasFixedSize(true);
        llm = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        rv.setLayoutManager(llm);
        rv.setClipToPadding(false);
        int sidePad = (int) (48 * getResources().getDisplayMetrics().density);
        int vertPad = (int) (24 * getResources().getDisplayMetrics().density);
        rv.setPadding(sidePad, vertPad, sidePad, vertPad);
        // Snap to center item
        snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(rv);
        // Scale/alpha transform based on distance from center
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                applyCoverflowTransforms(recyclerView);
            }
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (pendingResnap) {
                        resnapToCenter(recyclerView);
                        pendingResnap = false;
                    }
                    applyCoverflowTransforms(recyclerView);
                }
            }
        });

        titles = getArguments() != null ? getArguments().getStringArray(ARG_TITLES) : new String[0];
        uris = getArguments() != null ? getArguments().getStringArray(ARG_URIS) : new String[0];
        coverUrls = new String[uris.length];
        localPaths = new String[uris.length];
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        for (int i = 0; i < uris.length; i++) {
            String saved = prefs.getString("serial:" + uris[i], null);
            String serial = saved;
            if (serial == null || serial.isEmpty()) {
                try {
                    String nativeSerial = NativeApp.getGameSerial(uris[i]);
                    if (nativeSerial != null && !nativeSerial.isEmpty()) {
                        serial = normalizeSerial(nativeSerial);
                        prefs.edit().putString("serial:" + uris[i], serial).apply();
                    }
                } catch (Throwable ignored) {}
            }
            if (serial == null || serial.isEmpty()) {
                serial = buildSerialFromUri(uris[i]);
            }
            coverUrls[i] = buildCoverUrlFromSerial(serial);
            localPaths[i] = new java.io.File(getCoversDir(), serial + ".png").getAbsolutePath();
        }

        adapter = new CoversAdapter(requireContext(), titles, coverUrls, localPaths, R.layout.item_coverflow,
                position -> {
                    if (listener != null && position >= 0 && position < uris.length) {
                        listener.onGameSelected(uris[position]);
                        dismissAllowingStateLoss();
                    }
                },
                position -> {
                    if (position >= 0 && position < uris.length) {
                        showGameSettings(titles[position], uris[position]);
                    }
                });
        rv.setAdapter(adapter);
        // One-time tiny nudge to force snap/transform on some devices
        rv.post(() -> {
            if (!isAdded() || didInitialNudge) return;
            // Anchor to a large middle position for "infinite" scroll
            int n = titles != null ? titles.length : 0;
            if (n > 0) {
                int center = (1 << 29); // ~536 million
                int startPos = center - (center % n);
                llm.scrollToPosition(startPos);
            }
            rv.scrollBy(1, 0);
            rv.scrollBy(-1, 0);
            applyCoverflowTransforms(rv);
            didInitialNudge = true;
        });
        // Reduce resize flicker and keep a few views ready
        rv.setItemAnimator(null);
        rv.setItemViewCacheSize(12);

        // Ensure initial measurement + transforms run after first layout
        rv.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (!isAdded()) return;
                applyCoverflowTransforms(rv);
                rv.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

        // Resolve proper game titles using local YAML index if available (GameIndex/Redump).
        // Falls back to native URI API, then filename if needed.
        new Thread(() -> {
            boolean changed = false;
            for (int i = 0; i < uris.length; i++) {
                try {
                    String t = TitleResolver.resolveTitleForUri(requireContext(), uris[i], titles[i]);
                    if (t != null && !t.isEmpty() && i < titles.length && !t.equals(titles[i])) {
                        titles[i] = t;
                        changed = true;
                    }
                } catch (Throwable ignored) {}
            }
            if (changed && isAdded()) requireActivity().runOnUiThread(() -> adapter.notifyDataSetChanged());
        }).start();

        // Dynamically size items based on RecyclerView size and orientation
        rv.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            final int rvW = right - left;
            final int rvH = bottom - top;
            if (rvW <= 0 || rvH <= 0) return;
            if (rvW == lastRvW && rvH == lastRvH) return; // no real size change
            lastRvW = rvW;
            lastRvH = rvH;
            rv.post(() -> {
                if (!isAdded()) return;
                boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
                int titleDp = 36;
                int extraDp = 16;
                float density = getResources().getDisplayMetrics().density;
                int reservedH = (int) ((titleDp + extraDp) * density) + (vertPad * 2);
                int availableH = Math.max(0, rvH - reservedH);
                float ratio = 567f / 878f;
                int widthFromHeight = (int) (availableH * ratio);
                int widthFromWidth = (int) (rvW * (landscape ? 0.35f : 0.55f));
                int itemWidth = Math.max(160, Math.min(widthFromHeight, widthFromWidth));
                adapter.setItemWidthPx(itemWidth);
                // If user is scrolling, defer resnap until idle to avoid fighting gesture
                if (rv.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
                    resnapToCenter(rv);
                    applyCoverflowTransforms(rv);
                } else {
                    pendingResnap = true;
                }
            });
        });

        View btnHome = root.findViewById(R.id.btn_home);
        if (btnHome != null) btnHome.setOnClickListener(v -> dismissAllowingStateLoss());
        View btnDownload = root.findViewById(R.id.btn_download);
        if (btnDownload != null) btnDownload.setOnClickListener(v -> startDownloadCovers());

        return root;
    }

    private void applyCoverflowTransforms(@NonNull RecyclerView recyclerView) {
        int rvCenterX = (recyclerView.getLeft() + recyclerView.getRight()) / 2;
        boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        final float maxScale = 1.0f;
        final float minScale = landscape ? 0.70f : 0.82f;
        final float maxAlpha = 1.0f;
        final float minAlpha = landscape ? 0.50f : 0.60f;
        final float maxTiltDeg = landscape ? 22f : 16f;
        final float density = getResources().getDisplayMetrics().density;
        final float maxParallax = (landscape ? 14f : 18f) * density;
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            int childCenterX = (child.getLeft() + child.getRight()) / 2;
            float dx = childCenterX - rvCenterX;
            float dist = Math.abs(dx);
            float norm = Math.min(1f, dist / (recyclerView.getWidth() * 0.5f));
            float scale = maxScale - (maxScale - minScale) * norm;
            float alpha = maxAlpha - (maxAlpha - minAlpha) * norm;
            float tilt = Math.signum(dx) * maxTiltDeg * norm; // tilt away from center
            child.setCameraDistance(8000f * density);
            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(alpha);
            child.setTranslationZ((1f - norm) * 10f);
            child.setRotationY(tilt);

            // Title parallax (gentle)
            View title = child.findViewById(R.id.text_title);
            if (title != null) {
                float parallax = Math.max(-maxParallax, Math.min(maxParallax, -dx / (recyclerView.getWidth() * 0.5f) * maxParallax));
                title.setTranslationX(parallax);
                title.setAlpha(0.85f + 0.15f * (1f - norm));
            }

            // Shadow intensity scales with centeredness
            View shadow = child.findViewById(R.id.view_shadow);
            if (shadow != null) {
                shadow.setAlpha((1f - norm) * 0.7f);
                shadow.setScaleX(scale + 0.2f);
            }
        }
    }

    private void resnapToCenter(@NonNull RecyclerView rv) {
        if (snapHelper == null || llm == null) return;
        View snapView = snapHelper.findSnapView(llm);
        if (snapView == null) return;
        int[] dist = snapHelper.calculateDistanceToFinalSnap(llm, snapView);
        if (dist != null && (dist[0] != 0 || dist[1] != 0)) {
            rv.scrollBy(dist[0], dist[1]);
        }
    }

    public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        if (d == null) return;
        Window w = d.getWindow();
        if (w == null) return;
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = w.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            View decor = w.getDecorView();
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decor.setSystemUiVisibility(flags);
        }
    }

    private int calculateSpanForWidth(int rvWidthPx, int itemDp, int spacingPx) {
        float density = getResources().getDisplayMetrics().density;
        int usable = Math.max(0, rvWidthPx);
        int itemPx = (int) (itemDp * density);
        // Include spacing in the packing calculation to avoid oscillation
        // span = floor((usable + spacing) / (itemPx + spacing))
        int span = (itemPx > 0) ? (int) Math.floor((usable + (double) spacingPx) / (itemPx + (double) spacingPx)) : 1;
        return Math.max(2, Math.max(1, span));
    }

    private void preloadCovers(String[] urls) {
        // Use Glide to warm cache
        for (String url : urls) {
            if (url == null) continue;
            com.bumptech.glide.Glide.with(requireContext()).load(url).preload();
        }
    }

    private void startDownloadCovers() {
        Toast.makeText(requireContext(), "Downloading covers in background", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            // Try to refine serials/URLs by scanning disc contents first
            SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            for (int i = 0; i < uris.length; i++) {
                try {
                    // Prefer native serial extraction so CHDs work
                    String better = null;
                    try { better = NativeApp.getGameSerial(uris[i]); } catch (Throwable ignored) {}
                    if (better == null) better = extractSerialFromUri(uris[i]);
                    if (better != null && !better.equalsIgnoreCase(serialFromUrl(coverUrls[i]))) {
                        String serial = normalizeSerial(better);
                        coverUrls[i] = buildCoverUrlFromSerial(serial);
                        localPaths[i] = new java.io.File(getCoversDir(), serial + ".png").getAbsolutePath();
                        editor.putString("serial:" + uris[i], serial);
                    }
                } catch (Exception ignored) { }
            }
            editor.apply();

            int total = coverUrls.length;
            int ok = 0;
            java.io.File dir = getCoversDir();
            if (!dir.exists()) dir.mkdirs();
            for (int i = 0; i < total; i++) {
                String url = coverUrls[i];
                String outPath = localPaths[i];
                if (isFileValid(outPath)) { ok++; continue; }
                try {
                    if (downloadToFile(url, outPath)) ok++;
                } catch (Exception ignored) { }
            }
            final int downloaded = ok;
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "Covers ready: " + downloaded + "/" + total, Toast.LENGTH_SHORT).show();
                // refresh adapter to prefer local files now
                if (adapter != null) adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private static String serialFromUrl(String url) {
        if (url == null) return null;
        int slash = url.lastIndexOf('/');
        int dot = url.lastIndexOf('.');
        if (slash >= 0 && dot > slash) return url.substring(slash + 1, dot);
        return null;
    }

    private java.io.File getCoversDir() {
        java.io.File base = requireContext().getExternalFilesDir("covers");
        if (base == null) base = new java.io.File(requireContext().getFilesDir(), "covers");
        return base;
    }

    private static boolean isFileValid(String path) {
        if (path == null) return false;
        java.io.File f = new java.io.File(path);
        return f.exists() && f.length() > 0;
    }

    private static boolean downloadToFile(String urlStr, String outPath) throws Exception {
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);
        conn.connect();
        int code = conn.getResponseCode();
        if (code != 200) { conn.disconnect(); return false; }
        java.io.File outFile = new java.io.File(outPath);
        java.io.File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        java.io.InputStream in = conn.getInputStream();
        java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
        fos.flush();
        fos.close();
        in.close();
        conn.disconnect();
        return true;
    }

    private static String buildSerialFromUri(String gameUri) {
        // Try to infer PS2 serial from file name: e.g., SLUS-20312 or SLPS_123.45 style
        String last = Uri.parse(gameUri).getLastPathSegment();
        if (last == null) last = "";
        last = last.replace('_', '-');
        // remove extension
        int dot = last.lastIndexOf('.');
        if (dot > 0) last = last.substring(0, dot);
        String serial = null;
        // Very simple heuristic: find token like XXXX-XXXXX
        String upper = last.toUpperCase(Locale.ROOT);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Z]{4,5}-[0-9]{3,5})").matcher(upper);
        if (m.find()) {
            serial = m.group(1);
        }
        if (serial == null) {
            serial = upper;
        }
        return serial;
    }

    private static String buildCoverUrlFromSerial(String serial) {
        return "https://raw.githubusercontent.com/izzy2lost/ps2-covers/main/covers/3d/" + serial + ".png";
    }

    private String extractSerialFromUri(String gameUri) {
        try {
            java.io.InputStream in = requireContext().getContentResolver().openInputStream(Uri.parse(gameUri));
            if (in == null) return null;
            // Read first 8MB searching for SYSTEM.CNF contents, e.g., "BOOT2 = cdrom0:\\SLUS_203.12;1"
            final int MAX_BYTES = 8 * 1024 * 1024;
            final byte[] buf = new byte[64 * 1024];
            int read;
            int total = 0;
            StringBuilder sb = new StringBuilder();
            while ((read = in.read(buf)) != -1 && total < MAX_BYTES) {
                total += read;
                // append as ASCII
                sb.append(new String(buf, 0, read));
                // try to match as we go to avoid huge strings
                String found = findSerialInString(sb);
                if (found != null) { in.close(); return found; }
                if (sb.length() > 512 * 1024) sb.delete(0, sb.length() - 128 * 1024); // keep window
            }
            in.close();
        } catch (Exception ignored) { }
        return null;
    }

    private static String findSerialInString(CharSequence cs) {
        // Match common forms: SLUS_203.12, SLPM_650.51, SCES_123.45 etc.
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([A-Z]{4,5})[_-]([0-9]{3})\\.([0-9]{2})")
                .matcher(cs);
        if (m.find()) {
            String prefix = m.group(1);
            String part1 = m.group(2);
            String part2 = m.group(3);
            return prefix + "-" + part1 + part2; // SLUS-20312
        }
        return null;
    }

    private static String normalizeSerial(String serial) {
        if (serial == null) return null;
        String s = serial.toUpperCase(Locale.ROOT).replace('_', '-');
        // If form like XXXX-123.45 -> XXXX-12345
        s = s.replaceAll("([A-Z]{4,5})-([0-9]{3})\\.([0-9]{2})", "$1-$2$3");
        return s;
    }

    private void showGameSettings(String gameTitle, String gameUri) {
        // Prefer native extraction so CHDs work
        String gameSerial = null;
        try { gameSerial = NativeApp.getGameSerial(gameUri); } catch (Throwable ignored) {}
        if (gameSerial == null || gameSerial.isEmpty()) {
            gameSerial = extractSerialFromUri(gameUri);
        }
        if (gameSerial == null || gameSerial.isEmpty()) {
            gameSerial = buildSerialFromUri(gameUri);
        }
        gameSerial = normalizeSerial(gameSerial);

        // CRC (native if available)
        String gameCrc = null;
        try { gameCrc = NativeApp.getGameCrc(gameUri); } catch (Throwable ignored) {}
        if (gameCrc == null || gameCrc.isEmpty()) {
            gameCrc = String.format("%08X", Math.abs(gameUri.hashCode()));
        }

        // Debug logging
        android.util.Log.d("GameSettings", "Opening game settings for: " + gameTitle);
        android.util.Log.d("GameSettings", "URI: " + gameUri);
        android.util.Log.d("GameSettings", "Extracted Serial: " + gameSerial);
        android.util.Log.d("GameSettings", "Generated CRC: " + gameCrc);

        GameSettingsDialogFragment dialog = GameSettingsDialogFragment.newInstance(
            gameTitle, gameUri, gameSerial, gameCrc);
        dialog.show(getParentFragmentManager(), "game_settings");
    }
}
