package com.izzy2lost.psx2;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.core.widget.CompoundButtonCompat;
import androidx.core.content.ContextCompat;

public class SettingsDialogFragment extends DialogFragment {

    private static final String PREFS = "app_prefs";
    // Renderer constants (match native GSRendererType values used elsewhere)
    private static final int RENDERER_OPENGL = 12;
    private static final int RENDERER_SOFTWARE = 13;
    private static final int RENDERER_VULKAN = 14;

    // Static method to load and apply settings on app startup
    public static void loadAndApplySettings(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        
        int renderer = prefs.getInt("renderer", RENDERER_VULKAN);
        float scale = prefs.getFloat("upscale_multiplier", 1.0f);
        int aspectRatio = prefs.getInt("aspect_ratio", 1);
        int blendingAccuracy = prefs.getInt("blending_accuracy", 1); // 0..5
        boolean widescreenPatches = prefs.getBoolean("widescreen_patches", false);
        boolean noInterlacingPatches = prefs.getBoolean("no_interlacing_patches", false);
        boolean loadTextures = prefs.getBoolean("load_textures", false);
        boolean asyncTextureLoading = prefs.getBoolean("async_texture_loading", true);
        boolean hudVisible = prefs.getBoolean("hud_visible", false);
        
        // Debug logging
        android.util.Log.d("SettingsDialog", "Loading renderer setting: " + renderer + 
            " (12=OpenGL, 13=Software, 14=Vulkan)");
        
        // Apply all settings
        NativeApp.renderGpu(renderer);
        android.util.Log.d("SettingsDialog", "Applied renderer: " + renderer);
        NativeApp.renderUpscalemultiplier(scale);
        NativeApp.setAspectRatio(aspectRatio);
        NativeApp.setBlendingAccuracy(blendingAccuracy);
        NativeApp.setWidescreenPatches(widescreenPatches);
        NativeApp.setNoInterlacingPatches(noInterlacingPatches);
        NativeApp.setLoadTextures(loadTextures);
        NativeApp.setAsyncTextureLoading(asyncTextureLoading);
        NativeApp.setHudVisible(hudVisible);
        
        // Set brighter default brightness (60 instead of 50)
        NativeApp.setShadeBoost(true);
        NativeApp.setShadeBoostBrightness(60);
        NativeApp.setShadeBoostContrast(50);
        NativeApp.setShadeBoostSaturation(50);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_settings, null, false);

        RadioGroup rgRenderer = view.findViewById(R.id.rg_renderer);
        RadioButton rbGl = view.findViewById(R.id.rb_renderer_gl);
        RadioButton rbVk = view.findViewById(R.id.rb_renderer_vk);
        RadioButton rbSw = view.findViewById(R.id.rb_renderer_sw);
        Spinner spScale = view.findViewById(R.id.sp_scale);
        Spinner spBlending = view.findViewById(R.id.sp_blending_accuracy);
        Spinner spAspectRatio = view.findViewById(R.id.sp_aspect_ratio);
        Switch swWidescreen = view.findViewById(R.id.sw_widescreen);
        Switch swNoInterlacing = view.findViewById(R.id.sw_no_interlacing);
        Switch swLoadTextures = view.findViewById(R.id.sw_load_textures);
        Switch swAsyncTextureLoading = view.findViewById(R.id.sw_async_texture_loading);
        Switch swDevHud = view.findViewById(R.id.sw_dev_hud);
        View btnPower = view.findViewById(R.id.btn_power);
        View btnReboot = view.findViewById(R.id.btn_reboot);

        // Brand tints for checked/activated states to replace aqua
        int brand = ContextCompat.getColor(ctx, R.color.brand_primary);
        int outline = ContextCompat.getColor(ctx, R.color.brand_outline);
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        int[] colors = new int[]{
                brand,
                outline
        };
        ColorStateList brandChecked = new ColorStateList(states, colors);

        // RadioButtons
        if (rbGl != null) CompoundButtonCompat.setButtonTintList(rbGl, brandChecked);
        if (rbVk != null) CompoundButtonCompat.setButtonTintList(rbVk, brandChecked);
        if (rbSw != null) CompoundButtonCompat.setButtonTintList(rbSw, brandChecked);

        // Switches (thumb = brand when checked; track = subtle brand when checked)
        ColorStateList thumbTint = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{brand, outline}
        );
        int brandTrack = ColorStateList.valueOf(brand).withAlpha(100).getDefaultColor();
        int outlineTrack = ColorStateList.valueOf(outline).withAlpha(80).getDefaultColor();
        ColorStateList trackTint = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{brandTrack, outlineTrack}
        );
        if (swWidescreen != null) {
            swWidescreen.setThumbTintList(thumbTint);
            swWidescreen.setTrackTintList(trackTint);
        }
        if (swNoInterlacing != null) {
            swNoInterlacing.setThumbTintList(thumbTint);
            swNoInterlacing.setTrackTintList(trackTint);
        }
        if (swLoadTextures != null) {
            swLoadTextures.setThumbTintList(thumbTint);
            swLoadTextures.setTrackTintList(trackTint);
        }
        if (swAsyncTextureLoading != null) {
            swAsyncTextureLoading.setThumbTintList(thumbTint);
            swAsyncTextureLoading.setTrackTintList(trackTint);
        }
        if (swDevHud != null) {
            swDevHud.setThumbTintList(thumbTint);
            swDevHud.setTrackTintList(trackTint);
        }

        if (btnPower != null) {
            btnPower.setOnClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Power Off")
                        .setMessage("Quit the app?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Quit", (d1, w1) -> {
                            // Stop emulator first
                            NativeApp.shutdown();
                            // Close dialog
                            dismissAllowingStateLoss();
                            // Quit the whole app/activity task
                            if (getActivity() != null) {
                                getActivity().finishAffinity();
                                getActivity().finishAndRemoveTask();
                            }
                            // As a fallback ensure process exit
                            System.exit(0);
                        })
                        .show();
            });
        }

        if (btnReboot != null) {
            btnReboot.setOnClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Reboot")
                        .setMessage("Restart the current game?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Reboot", (d1, w1) -> {
                            if (requireActivity() instanceof MainActivity) {
                                ((MainActivity) requireActivity()).rebootEmu();
                            } else {
                                NativeApp.shutdown();
                            }
                            dismissAllowingStateLoss();
                        })
                        .show();
            });
        }

        // Populate scale spinner (1x..8x)
        ArrayAdapter<CharSequence> scaleAdapter = ArrayAdapter.createFromResource(ctx,
                R.array.scale_entries, android.R.layout.simple_spinner_item);
        scaleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spScale.setAdapter(scaleAdapter);

        // Populate blending accuracy spinner (0..5)
        ArrayAdapter<CharSequence> blendAdapter = ArrayAdapter.createFromResource(ctx,
                R.array.blending_accuracy_entries, android.R.layout.simple_spinner_item);
        blendAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBlending.setAdapter(blendAdapter);

        // Populate aspect ratio spinner
        ArrayAdapter<CharSequence> aspectAdapter = ArrayAdapter.createFromResource(ctx,
                R.array.aspect_ratio_entries, android.R.layout.simple_spinner_item);
        aspectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAspectRatio.setAdapter(aspectAdapter);

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int savedRenderer = prefs.getInt("renderer", RENDERER_VULKAN);
        float savedScale = prefs.getFloat("upscale_multiplier", 1.0f);
        int savedAspectRatio = prefs.getInt("aspect_ratio", 1); // 1 = Auto 4:3/3:2 (recommended)
        boolean savedWidescreen = prefs.getBoolean("widescreen_patches", false);
        boolean savedNoInterlacing = prefs.getBoolean("no_interlacing_patches", false);
        boolean savedLoadTextures = prefs.getBoolean("load_textures", false);
        boolean savedAsyncTextureLoading = prefs.getBoolean("async_texture_loading", true);
        boolean savedHud = prefs.getBoolean("hud_visible", false);
        int savedBlending = prefs.getInt("blending_accuracy", 1);

        if (savedRenderer == RENDERER_VULKAN) rbVk.setChecked(true);
        else if (savedRenderer == RENDERER_SOFTWARE) rbSw.setChecked(true);
        else rbGl.setChecked(true);

        int scaleIndex = scaleToIndex(savedScale);
        if (scaleIndex < 0 || scaleIndex >= scaleAdapter.getCount()) scaleIndex = 0;
        spScale.setSelection(scaleIndex);

        if (savedBlending < 0 || savedBlending >= blendAdapter.getCount()) savedBlending = 1;
        spBlending.setSelection(savedBlending);

        if (savedAspectRatio >= 0 && savedAspectRatio < aspectAdapter.getCount()) {
            spAspectRatio.setSelection(savedAspectRatio);
        } else {
            spAspectRatio.setSelection(1); // Default to Auto 4:3/3:2
        }

        swWidescreen.setChecked(savedWidescreen);
        swNoInterlacing.setChecked(savedNoInterlacing);
        swLoadTextures.setChecked(savedLoadTextures);
        swAsyncTextureLoading.setChecked(savedAsyncTextureLoading);
        if (swDevHud != null) swDevHud.setChecked(savedHud);

        AlertDialog.Builder b = new AlertDialog.Builder(requireContext());
        b.setTitle("Graphics Settings")
         .setView(view)
         .setNegativeButton("Cancel", (d, w) -> d.dismiss())
         .setPositiveButton("Save", (d, w) -> {
             int renderer = RENDERER_OPENGL;
             int checked = rgRenderer.getCheckedRadioButtonId();
             if (checked == R.id.rb_renderer_vk) renderer = RENDERER_VULKAN;
             else if (checked == R.id.rb_renderer_sw) renderer = RENDERER_SOFTWARE;

             float scale = indexToScale(spScale.getSelectedItemPosition());
             int aspectRatio = spAspectRatio.getSelectedItemPosition();
             boolean widescreenPatches = swWidescreen.isChecked();
             boolean noInterlacingPatches = swNoInterlacing.isChecked();
             boolean loadTextures = swLoadTextures.isChecked();
             boolean asyncTextureLoading = swAsyncTextureLoading.isChecked();
             boolean hudVisible = (swDevHud != null && swDevHud.isChecked());

             // Debug logging
             android.util.Log.d("SettingsDialog", "Saving renderer setting: " + renderer + 
                 " (12=OpenGL, 13=Software, 14=Vulkan)");
             
             // Save renderer setting first with commit() to ensure immediate write
             prefs.edit().putInt("renderer", renderer).commit();
             android.util.Log.d("SettingsDialog", "Renderer setting saved successfully");
             
             // Small delay to ensure setting is persisted
             try {
                 Thread.sleep(100);
             } catch (InterruptedException ignored) {}
             
             // Apply renderer change (might crash, but setting is already saved)
             try {
                 NativeApp.renderGpu(renderer);
                 android.util.Log.d("SettingsDialog", "Applied renderer change to: " + renderer);
             } catch (Exception e) {
                 android.util.Log.e("SettingsDialog", "Failed to apply renderer: " + e.getMessage());
                 // If renderer change fails, we'll still have the setting saved
                 // for next app restart
             }
             
             // Persist all other settings
            int blendingLevel = spBlending.getSelectedItemPosition();

            prefs.edit()
                    .putFloat("upscale_multiplier", scale)
                    .putInt("aspect_ratio", aspectRatio)
                    .putInt("blending_accuracy", blendingLevel)
                    .putBoolean("widescreen_patches", widescreenPatches)
                    .putBoolean("no_interlacing_patches", noInterlacingPatches)
                    .putBoolean("load_textures", loadTextures)
                    .putBoolean("async_texture_loading", asyncTextureLoading)
                    .putBoolean("hud_visible", hudVisible)
                    .apply();

             // Apply other settings
            NativeApp.renderUpscalemultiplier(scale);
            NativeApp.setAspectRatio(aspectRatio);
            NativeApp.setBlendingAccuracy(blendingLevel);
            NativeApp.setWidescreenPatches(widescreenPatches);
            NativeApp.setNoInterlacingPatches(noInterlacingPatches);
            NativeApp.setLoadTextures(loadTextures);
            NativeApp.setAsyncTextureLoading(asyncTextureLoading);
            NativeApp.setHudVisible(hudVisible);
         });

        return b.create();
    }

    private static int scaleToIndex(float scale) {
        if (scale <= 1.0f) return 0;
        if (scale <= 2.0f) return 1;
        if (scale <= 3.0f) return 2;
        if (scale <= 4.0f) return 3;
        if (scale <= 5.0f) return 4;
        if (scale <= 6.0f) return 5;
        if (scale <= 7.0f) return 6;
        return 7; // 8x or higher
    }

    private static float indexToScale(int index) {
        switch (index) {
            case 1: return 2.0f;
            case 2: return 3.0f;
            case 3: return 4.0f;
            case 4: return 5.0f;
            case 5: return 6.0f;
            case 6: return 7.0f;
            case 7: return 8.0f;
            case 0:
            default: return 1.0f;
        }
    }
}
