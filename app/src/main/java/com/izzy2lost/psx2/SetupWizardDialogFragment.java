package com.izzy2lost.psx2;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import androidx.core.content.ContextCompat;

import java.io.File;

public class SetupWizardDialogFragment extends DialogFragment {
    public static SetupWizardDialogFragment newInstance() { return new SetupWizardDialogFragment(); }

    private MaterialButton btnData;
    private MaterialButton btnGames;
    private MaterialButton btnBios;
    private MaterialButton btnDone;
    private TextView titleView;
    private TextView hintView;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog d = new Dialog(requireContext(), R.style.PSX2_FullScreenDialog);
        d.setContentView(buildContent());
        return d;
    }

    @Override
    public void onResume() {
        super.onResume();
        try { ((MainActivity) requireActivity()).setSetupWizardActive(true); } catch (Throwable ignored) {}
        // Refresh state (in case a step completed while this dialog was covered by a picker)
        try { updateUi(); } catch (Throwable ignored) {}
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        try { ((MainActivity) requireActivity()).setSetupWizardActive(false); } catch (Throwable ignored) {}
    }

    private View buildContent() {
        final LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = (int)(24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Use theme background
        root.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.md_theme_surface));

        titleView = new TextView(requireContext());
        titleView.setText("Welcome! Let's set up PSX2");
        titleView.setTextSize(26f);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        titleView.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_tertiaryFixedDim));
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(titleView);

        // Subtitle removed; using inline hint near the Done button instead.

        int btnHeight = (int)(48 * getResources().getDisplayMetrics().density);

        btnData = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnData.setText("1) Choose Data Folder");
        btnData.setMinimumHeight(btnHeight);
        btnData.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        btnData.setIconPadding((int)(8 * getResources().getDisplayMetrics().density));
        btnData.setOnClickListener(v -> {
            MainActivity a = (MainActivity) requireActivity();
            a.pickDataRootFolder();
        });
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp1.topMargin = (int)(24 * getResources().getDisplayMetrics().density);
        btnData.setLayoutParams(lp1);
        root.addView(btnData);

        btnGames = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnGames.setText("2) Choose Games Folder");
        btnGames.setMinimumHeight(btnHeight);
        btnGames.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        btnGames.setIconPadding((int)(8 * getResources().getDisplayMetrics().density));
        btnGames.setOnClickListener(v -> {
            MainActivity a = (MainActivity) requireActivity();
            a.pickGamesFolder();
        });
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = (int)(12 * getResources().getDisplayMetrics().density);
        btnGames.setLayoutParams(lp2);
        root.addView(btnGames);

        btnBios = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnBios.setText("3) Import BIOS Files");
        btnBios.setMinimumHeight(btnHeight);
        btnBios.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        btnBios.setIconPadding((int)(8 * getResources().getDisplayMetrics().density));
        btnBios.setOnClickListener(v -> {
            // Reuse existing BIOS prompt flow
            MainActivity a = (MainActivity) requireActivity();
            a.showBiosPrompt();
        });
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp3.topMargin = (int)(12 * getResources().getDisplayMetrics().density);
        btnBios.setLayoutParams(lp3);
        root.addView(btnBios);

        btnDone = new MaterialButton(requireContext());
        btnDone.setText("Done");
        btnDone.setMinimumHeight(btnHeight);
        btnDone.setOnClickListener(v -> {
            if (isDataFolderPicked() && isGamesFolderPicked() && isBiosPresent()) {
                requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("first_run_done", true).apply();
                MainActivity a = null;
                try { a = (MainActivity) requireActivity(); a.setSetupWizardActive(false); } catch (Throwable ignored) {}
                dismissAllowingStateLoss();
                if (a != null) {
                    // Open the games dialog just like the old GAMES button
                    final MainActivity act = a;
                    View decor = act.getWindow() != null ? act.getWindow().getDecorView() : null;
                    if (decor != null) {
                        decor.postDelayed(act::openGamesDialog, 150);
                    } else {
                        act.runOnUiThread(act::openGamesDialog);
                    }
                }
            }
        });
        LinearLayout.LayoutParams lp4 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp4.topMargin = (int)(28 * getResources().getDisplayMetrics().density);
        btnDone.setLayoutParams(lp4);
        root.addView(btnDone);

        // Inline hint below Done button
        hintView = new TextView(requireContext());
        hintView.setText("Complete all steps to finish.");
        hintView.setTextSize(14f);
        hintView.setGravity(Gravity.CENTER_HORIZONTAL);
        hintView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        hintView.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_primary));
        hintView.setAlpha(0.9f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = (int)(8 * getResources().getDisplayMetrics().density);
        hintView.setLayoutParams(hintLp);
        root.addView(hintView);

        // Initialize state
        updateUi();

        return root;
    }

    private boolean isDataFolderPicked() {
        return SafManager.getDataRootUri(requireContext()) != null;
    }

    private boolean isGamesFolderPicked() {
        String s = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                .getString("games_folder_uri", null);
        return s != null && !s.isEmpty();
    }

    private boolean isBiosPresent() {
        File biosDir = new File(requireContext().getExternalFilesDir(null), "bios");
        if (biosDir != null && biosDir.isDirectory()) {
            File[] fs = biosDir.listFiles();
            if (fs != null) {
                for (File f : fs) {
                    if (f != null && f.isFile()) {
                        String lower = f.getName().toLowerCase(java.util.Locale.ROOT);
                        boolean isMainBios = lower.startsWith("scph") && (lower.endsWith(".bin") || lower.endsWith(".rom"));
                        boolean isComponentSuffix = lower.endsWith(".rom0") || lower.endsWith(".rom1") || lower.endsWith(".rom2") || lower.endsWith(".erom");
                        boolean isBareComponent = lower.equals("rom0") || lower.equals("rom1") || lower.equals("rom2") || lower.equals("erom");
                        if ((isMainBios && f.length() >= 256 * 1024) || (isComponentSuffix || isBareComponent))
                            return true;
                    }
                }
            }
        }
        return false;
    }

    private void updateUi() {
        boolean step1 = isDataFolderPicked();
        boolean step2 = isGamesFolderPicked();
        boolean step3 = isBiosPresent();

        btnData.setText("1) Choose Data Folder");
        btnGames.setText("2) Choose Games Folder");
        btnBios.setText("3) Import BIOS Files");

        btnData.setIcon(step1 ? ContextCompat.getDrawable(requireContext(), R.drawable.check_circle_24px) : null);
        btnGames.setIcon(step2 ? ContextCompat.getDrawable(requireContext(), R.drawable.check_circle_24px) : null);
        btnBios.setIcon(step3 ? ContextCompat.getDrawable(requireContext(), R.drawable.check_circle_24px) : null);

        // Add theme accent to completed buttons
        int themeAccent = ContextCompat.getColor(requireContext(), R.color.md_theme_tertiary);
        int defaultColor = ContextCompat.getColor(requireContext(), R.color.md_theme_onSurface);
        
        btnData.setIconTint(step1 ? android.content.res.ColorStateList.valueOf(themeAccent) : null);
        btnGames.setIconTint(step2 ? android.content.res.ColorStateList.valueOf(themeAccent) : null);
        btnBios.setIconTint(step3 ? android.content.res.ColorStateList.valueOf(themeAccent) : null);

        btnGames.setEnabled(step1);
        btnBios.setEnabled(step1 && step2);
        boolean doneEnabled = (step1 && step2 && step3);
        btnDone.setEnabled(doneEnabled);
        
        // Style the Done button when ready
        if (doneEnabled) {
            btnDone.setBackgroundTintList(android.content.res.ColorStateList.valueOf(themeAccent));
            btnDone.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_onTertiary));
            if (hintView != null) {
                hintView.setText("🎉 Ready to go! Tap Done to start.");
                hintView.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_tertiaryFixedDim));
            }
        } else {
            btnDone.setBackgroundTintList(null);
            btnDone.setTextColor(defaultColor);
            if (hintView != null) {
                hintView.setText("Complete all steps to finish.");
                hintView.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_primary));
            }
        }
        
        if (hintView != null) hintView.setVisibility(View.VISIBLE);

    }
}

