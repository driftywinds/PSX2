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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
// Use Material 3 dialog builder for per‑game settings dialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.DialogFragment;

public class GameSettingsDialogFragment extends DialogFragment {

    private static final String ARG_GAME_TITLE = "game_title";
    private static final String ARG_GAME_URI = "game_uri";
    private static final String ARG_GAME_SERIAL = "game_serial";
    private static final String ARG_GAME_CRC = "game_crc";

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

        // Renderer Spinner (no Auto; entries: Vulkan, OpenGL, Software)
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

        // Load existing per-game settings from INI and prefill widgets; if missing, use global
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
                        int idx = 0; // 0=Vulkan,1=OpenGL,2=Software
                        if ("Vulkan".equalsIgnoreCase(rv) || "14".equals(rv)) idx = 0;
                        else if ("OpenGL".equalsIgnoreCase(rv) || "12".equals(rv)) idx = 1;
                        else if ("Software".equalsIgnoreCase(rv) || "13".equals(rv)) idx = 2;
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
                    int globalRenderer = prefs.getInt("renderer", 14); // default Vulkan
                    int idx = (globalRenderer == 14) ? 0 : (globalRenderer == 12 ? 1 : 2);
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
            int globalRenderer = prefs.getInt("renderer", 14);
            int idx = (globalRenderer == 14) ? 0 : (globalRenderer == 12 ? 1 : 2);
            spRenderer.setSelection(idx);
            spResolution.setSelection(0);
        }

        // Use MaterialAlertDialogBuilder with Material 3 overlay for the main dialog
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ctx,
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog);
        builder.setTitle("Per-Game Settings")
               .setView(view)
               .setNegativeButton("Cancel", (d, w) -> d.dismiss())
               .setPositiveButton("Save", (d, w) -> {
                   // Apply blending to runtime as well for immediate effect
                   NativeApp.setBlendingAccuracy(spBlendingAccuracy.getSelectedItemPosition());

                   // Persist per-game INI explicitly to mirror global defaults and avoid Auto
                   writeGameSettingsIni(ctx, gameSerial, gameCrc,
                           spBlendingAccuracy.getSelectedItemPosition(),
                           spRenderer.getSelectedItemPosition(),
                           spResolution.getSelectedItemPosition(),
                           swWidescreenPatches.isChecked(),
                           swNoInterlacingPatches.isChecked(),
                           /*enablePatches*/ swEnablePatchCodes.isChecked(),
                           swEnableCheats.isChecked());
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
                        .setTitle("Import PNACH")
                        .setItems(choices, (dlg, which) -> {
                            boolean asCheats = (which == 0);
                            // Prepare picker
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("*/*");
                            // Store choice in tag
                            view.setTag(R.id.btn_import_pnach, asCheats);
                            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                                try {
                                    if (result.getResultCode() != android.app.Activity.RESULT_OK) return;
                                    Intent data = result.getData(); if (data == null) return;
                                    android.net.Uri uri = data.getData(); if (uri == null) return;
                                    boolean importAsCheats = Boolean.TRUE.equals(view.getTag(R.id.btn_import_pnach));
                                    String serialLoad = gameSerial;
                                    if (serialLoad == null || serialLoad.isEmpty()) {
                                        try { serialLoad = NativeApp.getCurrentGameSerial(); } catch (Throwable ignored) {}
                                    }
                                    if (serialLoad == null || serialLoad.isEmpty()) {
                                        android.widget.Toast.makeText(ctx, "Serial unknown; cannot import", android.widget.Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    java.io.File baseDir = ctx.getExternalFilesDir(null);
                                    if (baseDir == null) baseDir = ctx.getFilesDir();
                                    java.io.File targetDir = new java.io.File(baseDir, importAsCheats ? "cheats" : "patches");
                                    if (!targetDir.exists()) targetDir.mkdirs();
                                    java.io.File outFile = new java.io.File(targetDir, serialLoad + ".pnach");
                                    android.content.ContentResolver cr = ctx.getContentResolver();
                                    java.io.InputStream in = cr.openInputStream(uri);
                                    if (in == null) { android.widget.Toast.makeText(ctx, "Failed to open file", android.widget.Toast.LENGTH_SHORT).show(); return; }
                                    java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
                                    byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                                    fos.flush(); fos.close(); in.close();
                                    android.widget.Toast.makeText(ctx, (importAsCheats ? "Cheats" : "Patch Codes") + " imported for " + serialLoad, android.widget.Toast.LENGTH_SHORT).show();
                                } catch (Exception e) {
                                    android.widget.Toast.makeText(ctx, "Import failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                                }
                            }).launch(intent);
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
            String rendererName = (rendererIdx == 0) ? "Vulkan" : (rendererIdx == 1 ? "OpenGL" : "Software");
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
