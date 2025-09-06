package com.izzy2lost.psx2;

import android.app.Dialog;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.widget.TextView;
import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.DialogFragment;

public class GameSettingsDialogFragment extends DialogFragment {

    private static final String ARG_GAME_TITLE = "game_title";
    private static final String ARG_GAME_URI = "game_uri";
    private static final String ARG_GAME_SERIAL = "game_serial";
    private static final String ARG_GAME_CRC = "game_crc";

    // File picker state
    private ActivityResultLauncher<Intent> mPnachPicker;
    private boolean mImportAsCheats = true;

    public static GameSettingsDialogFragment newInstance(String gameTitle, String gameUri, String gameSerial, String gameCrc) {
        GameSettingsDialogFragment fragment = new GameSettingsDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_GAME_TITLE, gameTitle);
        args.putString(ARG_GAME_URI, gameUri);
        args.putString(ARG_GAME_SERIAL, gameSerial);
        args.putString(ARG_GAME_CRC, gameCrc);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        View view = getLayoutInflater().inflate(R.layout.dialog_game_settings, null, false);

        // Register picker ahead of time to avoid lifecycle crashes
        if (mPnachPicker == null) {
            mPnachPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                try {
                    if (result.getResultCode() != Activity.RESULT_OK) return;
                    Intent data = result.getData(); if (data == null) return;
                    Uri uri = data.getData(); if (uri == null) return;
                    Bundle args = getArguments();
                    String gameSerial = args != null ? args.getString(ARG_GAME_SERIAL, "") : "";
                    if (gameSerial == null || gameSerial.isEmpty()) {
                        try { gameSerial = NativeApp.getCurrentGameSerial(); } catch (Throwable ignored) {}
                    }
                    if (gameSerial == null || gameSerial.isEmpty()) {
                        android.widget.Toast.makeText(ctx, "Serial unknown; cannot import", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    java.io.File baseDir = ctx.getExternalFilesDir(null);
                    if (baseDir == null) baseDir = ctx.getFilesDir();
                    java.io.File targetDir = new java.io.File(baseDir, mImportAsCheats ? "cheats" : "patches");
                    if (!targetDir.exists()) targetDir.mkdirs();
                    java.io.File outFile = new java.io.File(targetDir, gameSerial + ".pnach");
                    android.content.ContentResolver cr = ctx.getContentResolver();
                    java.io.InputStream in = cr.openInputStream(uri);
                    if (in == null) { android.widget.Toast.makeText(ctx, "Failed to open file", android.widget.Toast.LENGTH_SHORT).show(); return; }
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
                    byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                    fos.flush(); fos.close(); in.close();

                    // Mirror to SAF data root if set
                    android.net.Uri dataRoot = SafManager.getDataRootUri(ctx);
                    if (dataRoot != null) {
                        String subdir = mImportAsCheats ? "cheats" : "patches";
                        androidx.documentfile.provider.DocumentFile target = SafManager.createChild(ctx, new String[]{subdir}, gameSerial + ".pnach", "text/plain");
                        if (target != null) {
                            try (java.io.InputStream in2 = cr.openInputStream(android.net.Uri.fromFile(outFile))) {
                                SafManager.copyFromStream(ctx, in2, target.getUri());
                            } catch (Exception ignored) {}
                        }
                    }
                    android.widget.Toast.makeText(ctx, (mImportAsCheats ? "Cheats" : "Patch Codes") + " imported for " + gameSerial, android.widget.Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    android.widget.Toast.makeText(ctx, "Import failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }

        Bundle args = getArguments();
        String gameTitle = args != null ? args.getString(ARG_GAME_TITLE, "Unknown Game") : "Unknown Game";
        String gameUri = args != null ? args.getString(ARG_GAME_URI, "") : "";
        String gameSerial = args != null ? args.getString(ARG_GAME_SERIAL, "") : "";
        String gameCrc = args != null ? args.getString(ARG_GAME_CRC, "") : "";

        // Set title
        TextView titleView = view.findViewById(R.id.tv_game_title);
        titleView.setText(gameTitle);

        TextView serialView = view.findViewById(R.id.tv_game_serial);
        if (!gameSerial.isEmpty() || !gameCrc.isEmpty()) {
            serialView.setText(String.format("Serial: %s | CRC: %s", 
                gameSerial.isEmpty() ? "Unknown" : gameSerial,
                gameCrc.isEmpty() ? "Unknown" : gameCrc));
            serialView.setVisibility(View.VISIBLE);
        } else {
            serialView.setVisibility(View.GONE);
        }

        // Blending Accuracy Spinner
        Spinner spBlendingAccuracy = view.findViewById(R.id.sp_blending_accuracy);
        ArrayAdapter<CharSequence> blendingAdapter = ArrayAdapter.createFromResource(ctx,
                R.array.blending_accuracy_entries, android.R.layout.simple_spinner_item);
        blendingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBlendingAccuracy.setAdapter(blendingAdapter);

        // Renderer Spinner (now includes Auto)
        Spinner spRenderer = view.findViewById(R.id.sp_renderer);
        ArrayAdapter<CharSequence> rendererAdapter = ArrayAdapter.createFromResource(ctx,
                R.array.renderer_entries, android.R.layout.simple_spinner_item);
        rendererAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRenderer.setAdapter(rendererAdapter);

        // Resolution Multiplier Spinner
        Spinner spResolution = view.findViewById(R.id.sp_resolution);
        ArrayAdapter<CharSequence> resolutionAdapter = ArrayAdapter.createFromResource(ctx,
                R.array.scale_entries, android.R.layout.simple_spinner_item);
        resolutionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spResolution.setAdapter(resolutionAdapter);

        // Switches
        MaterialSwitch swWidescreenPatches = view.findViewById(R.id.sw_widescreen_patches);
        MaterialSwitch swNoInterlacingPatches = view.findViewById(R.id.sw_no_interlacing_patches);
        MaterialSwitch swEnablePatchCodes = view.findViewById(R.id.sw_enable_patch_codes);
        MaterialSwitch swEnableCheats = view.findViewById(R.id.sw_enable_cheats);
        MaterialSwitch swLoadTextures = view.findViewById(R.id.sw_load_textures_per_game);
        MaterialSwitch swAsyncTextures = view.findViewById(R.id.sw_async_texture_loading_per_game);

        // Prefill with global defaults
        android.content.SharedPreferences gp = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        int gRenderer = gp.getInt("renderer", -1);
        float gScale = gp.getFloat("upscale_multiplier", 1.0f);
        int gBlend = gp.getInt("blending_accuracy", 1);
        boolean gWide = gp.getBoolean("widescreen_patches", true);
        boolean gNoInt = gp.getBoolean("no_interlacing_patches", true);
        boolean gLoadTex = gp.getBoolean("load_textures", false);
        boolean gAsyncTex = gp.getBoolean("async_texture_loading", true);
        boolean gCheats = gp.getBoolean("enable_cheats", false);

        // Map to indices
        int defaultRendererIdx = (gRenderer == -1 ? 0 : (gRenderer == 14 ? 1 : (gRenderer == 12 ? 2 : 3)));
        int defaultScaleIdx = Math.max(0, Math.min(7, Math.round(gScale) - 1));
        spRenderer.setSelection(defaultRendererIdx);
        spResolution.setSelection(defaultScaleIdx);
        spBlendingAccuracy.setSelection(Math.max(0, Math.min(5, gBlend)));
        swWidescreenPatches.setChecked(gWide);
        swNoInterlacingPatches.setChecked(gNoInt);
        if (swLoadTextures != null) swLoadTextures.setChecked(gLoadTex);
        if (swAsyncTextures != null) swAsyncTextures.setChecked(gAsyncTex);
        swEnableCheats.setChecked(gCheats);

        // Load existing per-game settings from INI and prefill widgets; if present overrides globals
        try {
            String serial = gameSerial;
            if (serial == null || serial.isEmpty()) {
                serial = NativeApp.getCurrentGameSerial();
            }
            if (serial != null && !serial.isEmpty()) {
                // Build INI path
                String dataRoot = getContext().getExternalFilesDir(null).getAbsolutePath();
                java.io.File ini = new java.io.File(new java.io.File(dataRoot, "gamesettings"), serial + ".ini");
                boolean appliedRenderer = false;
                boolean appliedBlend = false;
                if (ini.exists()) {
                    String content = "";
                    try {
                        java.io.FileInputStream fis = new java.io.FileInputStream(ini);
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = fis.read(buf)) != -1) baos.write(buf, 0, n);
                        fis.close();
                        content = baos.toString("UTF-8");
                    } catch (Exception ignored) {}
                    // Very light parsing
                    java.util.regex.Matcher m;
                    m = java.util.regex.Pattern.compile("(?m)^Renderer=\\s*(.+)$").matcher(content);
                    if (m.find()) {
                        String rv = m.group(1).trim();
                        int idx = 0; // 0=Auto,1=Vulkan,2=OpenGL,3=Software
                        if ("Auto".equalsIgnoreCase(rv) || "-1".equals(rv)) idx = 0;
                        else if ("Vulkan".equalsIgnoreCase(rv) || "14".equals(rv)) idx = 1;
                        else if ("OpenGL".equalsIgnoreCase(rv) || "12".equals(rv)) idx = 2;
                        else if ("Software".equalsIgnoreCase(rv) || "13".equals(rv)) idx = 3;
                        spRenderer.setSelection(idx);
                        appliedRenderer = true;
                    }
                    m = java.util.regex.Pattern.compile("(?m)^upscale_multiplier=\\s*([0-9]+(?:\\.[0-9]+)?)$").matcher(content);
                    if (m.find()) {
                        try { float mult = Float.parseFloat(m.group(1)); int sel = Math.max(0, Math.min(7, Math.round(mult - 1))); spResolution.setSelection(sel); } catch (Exception ignored) {}
                    }
                    m = java.util.regex.Pattern.compile("(?m)^accurate_blending_unit=\\s*(.+)$").matcher(content);
                    if (m.find()) {
                        String bv = m.group(1).trim();
                        int idx = 1; // default Basic
                        try {
                            int num = Integer.parseInt(bv);
                            if (num >= 0 && num <= 5) idx = num;
                        } catch (Exception e) {
                            if ("Minimum".equalsIgnoreCase(bv)) idx = 0;
                            else if ("Basic".equalsIgnoreCase(bv)) idx = 1;
                            else if ("Medium".equalsIgnoreCase(bv)) idx = 2;
                            else if ("High".equalsIgnoreCase(bv)) idx = 3;
                            else if ("Full".equalsIgnoreCase(bv)) idx = 4;
                            else if ("Maximum".equalsIgnoreCase(bv)) idx = 5;
                        }
                        spBlendingAccuracy.setSelection(idx);
                        appliedBlend = true;
                    }
                    m = java.util.regex.Pattern.compile("(?m)^EnableWideScreenPatches=\\s*(true|false)$").matcher(content);
                    if (m.find()) swWidescreenPatches.setChecked(Boolean.parseBoolean(m.group(1)));
                    m = java.util.regex.Pattern.compile("(?m)^EnableNoInterlacingPatches=\\s*(true|false)$").matcher(content);
                    if (m.find()) swNoInterlacingPatches.setChecked(Boolean.parseBoolean(m.group(1)));
                    m = java.util.regex.Pattern.compile("(?m)^EnableCheats=\\s*(true|false)$").matcher(content);
                    if (m.find()) swEnableCheats.setChecked(Boolean.parseBoolean(m.group(1)));
                    m = java.util.regex.Pattern.compile("(?m)^EnablePatches=\\s*(true|false)$").matcher(content);
                    if (m.find()) swEnablePatchCodes.setChecked(Boolean.parseBoolean(m.group(1)));
                }

                // If no per-game renderer specified, mirror the global renderer choice
                if (!appliedRenderer) {
                    android.content.SharedPreferences prefs = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                    int globalRenderer = prefs.getInt("renderer", -1); // default Auto
                    int idx;
                    if (globalRenderer == -1) idx = 0; // Auto
                    else if (globalRenderer == 14) idx = 1; // Vulkan
                    else if (globalRenderer == 12) idx = 2; // OpenGL
                    else if (globalRenderer == 13) idx = 3; // Software
                    else idx = 0;
                    spRenderer.setSelection(idx);
                }
                // If no per-game blending specified, mirror the global blending
                if (!appliedBlend) {
                    android.content.SharedPreferences prefs = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                    int globalBlend = prefs.getInt("blending_accuracy", 1);
                    spBlendingAccuracy.setSelection(Math.max(0, Math.min(5, globalBlend)));
                }
            }
        } catch (Throwable ignored) {
            // Fallback to defaults if loading fails
            spBlendingAccuracy.setSelection(1);
            // Mirror global default when error
            android.content.SharedPreferences prefs = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            int globalRenderer = prefs.getInt("renderer", -1);
            int idx;
            if (globalRenderer == -1) idx = 0;
            else if (globalRenderer == 14) idx = 1;
            else if (globalRenderer == 12) idx = 2;
            else if (globalRenderer == 13) idx = 3;
            else idx = 0;
            spRenderer.setSelection(idx);
            spResolution.setSelection(0);
        }

        // Use MaterialAlertDialogBuilder with Material 3 overlay for the main dialog
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ctx,
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog);
        builder.setCustomTitle(UiUtils.centeredDialogTitle(ctx, "Per-Game Settings"))
                .setView(view)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Save", (d, w) -> {
                   final int blendLevel = spBlendingAccuracy.getSelectedItemPosition();
                   final int rendererIdx = spRenderer.getSelectedItemPosition();
                   final int resIdx = spResolution.getSelectedItemPosition();
                   final boolean wide = swWidescreenPatches.isChecked();
                   final boolean noInt = swNoInterlacingPatches.isChecked();
                   final boolean enablePatches = true; // always on
                   final boolean enableCheats = swEnableCheats.isChecked();
                   final boolean loadTex = (swLoadTextures != null && swLoadTextures.isChecked());
                   final boolean asyncTex = (swAsyncTextures != null && swAsyncTextures.isChecked());

                   // Persist per-game INI explicitly (supports Auto as well)
                   writeGameSettingsIni(ctx, gameSerial, gameCrc,
                           blendLevel, rendererIdx, resIdx, wide, noInt,
                           /*enablePatches*/ enablePatches, enableCheats);

                   // Live-apply per-game settings in one batch
                   try {
                       int renderer;
                       // rendererIdx: 0=Auto,1=Vulkan,2=OpenGL,3=Software
                       if (rendererIdx == 0) renderer = -1;
                       else if (rendererIdx == 1) renderer = 14;
                       else if (rendererIdx == 2) renderer = 12;
                       else renderer = 13;

                       float scale = Math.max(1, Math.min(8, resIdx + 1));
                       NativeApp.setLoadTextures(loadTex);
                       NativeApp.setAsyncTextureLoading(asyncTex);
                       NativeApp.applyPerGameSettingsBatch(renderer, scale, blendLevel, wide, noInt, enablePatches, enableCheats);
                   } catch (Throwable t) {
                       android.util.Log.e("GameSettings", "Per-game batch apply failed: " + t.getMessage());
                   }

                   // Refresh quick UI (renderer label) to reflect runtime renderer
                   try {
                       android.app.Activity a = getActivity();
                       if (a instanceof MainActivity) {
                           ((MainActivity) a).runOnUiThread(() -> ((MainActivity) a).refreshQuickUi());
                       }
                   } catch (Throwable ignored) {}

                   d.dismiss();
               })
               .setNeutralButton("Reset to Global", (d, w) -> {
                   // Delete game-specific settings file so globals apply
                   deleteGameSettingsIni(ctx, gameSerial, gameCrc);
                   d.dismiss();
               });

        // Import PNACH button wiring
        com.google.android.material.button.MaterialButton btnImport = view.findViewById(R.id.btn_import_pnach);
        if (btnImport != null) {
            btnImport.setOnClickListener(v -> {
                final String[] choices = new String[]{"Import as Cheats", "Import as Patch Codes"};
                new MaterialAlertDialogBuilder(ctx,
                        com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                        .setCustomTitle(UiUtils.centeredDialogTitle(ctx, "Import PNACH"))
                        .setItems(choices, (dlg, which) -> {
                            mImportAsCheats = (which == 0);
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("*/*");
                            mPnachPicker.launch(intent);
                        })
                        .show();
            });
        }

        return builder.create();
    }

    private void saveGameSettings(String gameSerial, String gameCrc,
                                 int blendingAccuracy, int renderer, int resolution,
                                 boolean widescreenPatches, boolean noInterlacingPatches,
                                 boolean enablePatches, boolean enableCheats) {

        // Create the settings filename based on serial (like SLUS-12345.ini)
        if (gameSerial == null || gameSerial.isEmpty()) {
            android.util.Log.w("GameSettings", "No game serial available, cannot save settings");
            return;
        }
        String filename = gameSerial + ".ini";

        // Save to PCSX2's DataRoot/gamesettings via native helper.
        NativeApp.saveGameSettings(filename, blendingAccuracy, renderer, resolution,
                widescreenPatches, noInterlacingPatches, enablePatches, enableCheats);

        android.widget.Toast.makeText(requireContext(), "Game settings saved: " + filename, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void deleteGameSettings(String gameSerial, String gameCrc) {
        String filename = "";
        if (!gameSerial.isEmpty()) {
            filename = gameSerial + ".ini";
        } else {
            return;
        }

        NativeApp.deleteGameSettings(filename);
    }

    private static String pickSettingsFileName(String gameSerial, String gameCrc) {
        if (gameSerial != null && !gameSerial.isEmpty()) return gameSerial + ".ini";
        if (gameCrc != null && !gameCrc.isEmpty()) return gameCrc + ".ini";
        return null;
    }

    private static void writeGameSettingsIni(Context ctx,
                                             String gameSerial,
                                             String gameCrc,
                                             int blendingAccuracyIdx,
                                             int rendererIdx,
                                             int resolutionIdx,
                                             boolean widescreenPatches,
                                             boolean noInterlacingPatches,
                                             boolean enablePatches,
                                             boolean enableCheats) {
        try {
            String fileName = pickSettingsFileName(gameSerial, gameCrc);
            if (fileName == null) return;
            java.io.File baseDir = new java.io.File(ctx.getExternalFilesDir(null), "gamesettings");
            if (!baseDir.exists()) baseDir.mkdirs();
            java.io.File ini = new java.io.File(baseDir, fileName);

            // Map indices
            String rendererName = (rendererIdx == 0) ? "Auto" : (rendererIdx == 1 ? "Vulkan" : (rendererIdx == 2 ? "OpenGL" : "Software"));
            float upscale = Math.max(1, Math.min(8, resolutionIdx + 1));
            int abl = Math.max(0, Math.min(5, blendingAccuracyIdx));

            StringBuilder sb = new StringBuilder();
            sb.append("[EmuCore/GS]\n");
            sb.append("Renderer=").append(rendererName).append('\n');
            sb.append("upscale_multiplier=").append((int) upscale).append('\n');
            sb.append("accurate_blending_unit=").append(abl).append('\n');
            sb.append('\n');
            sb.append("[EmuCore]\n");
            sb.append("EnableWideScreenPatches=").append(widescreenPatches).append('\n');
            sb.append("EnableNoInterlacingPatches=").append(noInterlacingPatches).append('\n');
            sb.append("EnablePatches=").append(enablePatches).append('\n');
            sb.append("EnableCheats=").append(enableCheats).append('\n');

            try {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(ini, false);
                byte[] data = sb.toString().getBytes("UTF-8");
                fos.write(data);
                fos.flush();
                fos.close();
            } catch (Exception ignored) {}

            // Mirror to SAF data root if set
            android.net.Uri dataRoot = SafManager.getDataRootUri(ctx);
            if (dataRoot != null) {
                try {
                    androidx.documentfile.provider.DocumentFile target = SafManager.createChild(ctx, new String[]{"gamesettings"}, fileName, "text/plain");
                    if (target != null) {
                        byte[] data = sb.toString().getBytes("UTF-8");
                        SafManager.writeBytes(ctx, target.getUri(), data);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Throwable ignored) {
        }
    }

    private static void deleteGameSettingsIni(Context ctx, String gameSerial, String gameCrc) {
        try {
            String fileName = pickSettingsFileName(gameSerial, gameCrc);
            if (fileName == null) return;
            java.io.File ini = new java.io.File(new java.io.File(ctx.getExternalFilesDir(null), "gamesettings"), fileName);
            if (ini.exists()) ini.delete();
        } catch (Throwable ignored) {
        }
    }
}
