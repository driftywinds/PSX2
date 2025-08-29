package com.izzy2lost.psx2;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class QuickActionsDialogFragment extends DialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_quick_actions, null, false);

        MaterialButton btnOpenGames = view.findViewById(R.id.btn_open_games);
        MaterialButton btnExitToMenu = view.findViewById(R.id.btn_exit_to_menu);
        MaterialButton btnRestartGame = view.findViewById(R.id.btn_restart_game);
        MaterialButton btnQuitApp = view.findViewById(R.id.btn_quit_app);
        MaterialButton btnCancel = view.findViewById(R.id.btn_cancel);

        // Open Games/Covers dialog
        if (btnOpenGames != null) {
            btnOpenGames.setOnClickListener(v -> {
                if (requireActivity() instanceof MainActivity) {
                    ((MainActivity) requireActivity()).openGamesDialog();
                }
                dismissAllowingStateLoss();
            });
        }

        // Exit to Menu - not implemented yet, show placeholder
        if (btnExitToMenu != null) {
            btnExitToMenu.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext(),
                        com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                        .setTitle("Exit to Menu")
                        .setMessage("This feature is not implemented yet. Would you like to quit the app instead?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Quit App", (d, w) -> {
                            quitApp();
                            dismissAllowingStateLoss();
                        })
                        .show();
            });
        }

        // Restart Game - use existing reboot functionality
        if (btnRestartGame != null) {
            btnRestartGame.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext(),
                        com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                        .setTitle("Restart Game")
                        .setMessage("Restart the current game?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Restart", (d, w) -> {
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

        // Quit App - use existing power functionality
        if (btnQuitApp != null) {
            btnQuitApp.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(requireContext(),
                        com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                        .setTitle("Quit App")
                        .setMessage("Quit PSX2?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Quit", (d, w) -> {
                            quitApp();
                            dismissAllowingStateLoss();
                        })
                        .show();
            });
        }

        // Cancel button
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dismissAllowingStateLoss());
        }

        return new MaterialAlertDialogBuilder(requireContext(),
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setView(view)
                .create();
    }

    private void quitApp() {
        // Stop emulator first
        NativeApp.shutdown();
        // Quit the whole app/activity task
        if (getActivity() != null) {
            getActivity().finishAffinity();
            getActivity().finishAndRemoveTask();
        }
        // As a fallback ensure process exit
        System.exit(0);
    }
}
