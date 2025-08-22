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
import android.widget.Switch;
import android.widget.TextView;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_game_settings, null, false);

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

        // Renderer Spinner
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
        Switch swWidescreenPatches = view.findViewById(R.id.sw_widescreen_patches);
        Switch swNoInterlacingPatches = view.findViewById(R.id.sw_no_interlacing_patches);
        Switch swEnablePatchCodes = view.findViewById(R.id.sw_enable_patch_codes);
        Switch swEnableCheats = view.findViewById(R.id.sw_enable_cheats);

        // Load existing per-game settings from INI and prefill widgets
        try {
            String serial = gameSerial;
            if (serial == null || serial.isEmpty()) {
                serial = NativeApp.getCurrentGameSerial();
            }
            if (serial != null && !serial.isEmpty()) {
                // Build INI path
                String dataRoot = getContext().getExternalFilesDir(null).getAbsolutePath();
                java.io.File ini = new java.io.File(new java.io.File(dataRoot, "gamesettings"), serial + ".ini");
                if (ini.exists()) {
                    String content = new String(java.nio.file.Files.readAllBytes(ini.toPath()));
                    // Very light parsing
                    java.util.regex.Matcher m;
                    m = java.util.regex.Pattern.compile("(?m)^Renderer=\\s*(.+)$").matcher(content);
                    if (m.find()) {
                        String rv = m.group(1).trim();
                        int idx = 0;
                        if ("Vulkan".equalsIgnoreCase(rv)) idx = 1;
                        else if ("OpenGL".equalsIgnoreCase(rv)) idx = 2;
                        else if ("Software".equalsIgnoreCase(rv)) idx = 3;
                        spRenderer.setSelection(idx);
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
            }
        } catch (Throwable ignored) {
            // Fallback to defaults if loading fails
            spBlendingAccuracy.setSelection(1);
            spRenderer.setSelection(0);
            spResolution.setSelection(0);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle("Per-Game Settings")
               .setView(view)
               .setNegativeButton("Cancel", (d, w) -> d.dismiss())
               .setPositiveButton("Save", (d, w) -> {
                   // Apply blending to runtime as well for immediate effect
                   NativeApp.setBlendingAccuracy(spBlendingAccuracy.getSelectedItemPosition());

                   saveGameSettings(gameSerial, gameCrc,
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
                   // TODO: Delete game-specific settings file
                   deleteGameSettings(gameSerial, gameCrc);
                   d.dismiss();
               });

        // Import PNACH button wiring
        com.google.android.material.button.MaterialButton btnImport = view.findViewById(R.id.btn_import_pnach);
        if (btnImport != null) {
            btnImport.setOnClickListener(v -> {
                final String[] choices = new String[]{"Import as Cheats", "Import as Patch Codes"};
                new AlertDialog.Builder(ctx)
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
}
