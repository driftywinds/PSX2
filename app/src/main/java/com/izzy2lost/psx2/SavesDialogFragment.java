package com.izzy2lost.psx2;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
// Import MaterialAlertDialogBuilder for Material 3 dialogs
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SavesDialogFragment extends DialogFragment {

    public static class SaveSlot {
        public int slot;
        public String title;
        public String timestamp;
        public byte[] screenshot;
        public boolean isEmpty;
        public String gamePath;

        public SaveSlot(int slot, String gamePath) {
            this.slot = slot;
            this.gamePath = gamePath;
            this.isEmpty = true;
            this.title = "Empty Slot " + slot;
            this.timestamp = "";
        }
    }

    private static class SaveSlotAdapter extends RecyclerView.Adapter<SaveSlotAdapter.ViewHolder> {
        private List<SaveSlot> saveSlots;
        private OnSlotClickListener listener;

        interface OnSlotClickListener {
            void onSave(int slot);
            void onLoad(int slot);
        }

        SaveSlotAdapter(List<SaveSlot> saveSlots, OnSlotClickListener listener) {
            this.saveSlots = saveSlots;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_save_slot, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SaveSlot slot = saveSlots.get(position);
            holder.bind(slot, listener);
        }

        @Override
        public int getItemCount() {
            return saveSlots.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, timestamp;
            ImageView screenshot;
            View saveButton, loadButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tv_slot_title);
                timestamp = itemView.findViewById(R.id.tv_slot_timestamp);
                screenshot = itemView.findViewById(R.id.iv_slot_screenshot);
                saveButton = itemView.findViewById(R.id.btn_slot_save);
                loadButton = itemView.findViewById(R.id.btn_slot_load);
            }

            void bind(SaveSlot slot, OnSlotClickListener listener) {
                title.setText(slot.title);
                timestamp.setText(slot.timestamp);

                // Set screenshot if available
                if (slot.screenshot != null && slot.screenshot.length > 0) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(slot.screenshot, 0, slot.screenshot.length);
                    screenshot.setImageBitmap(bitmap);
                    screenshot.setVisibility(View.VISIBLE);
                    
                    // Make screenshot clickable to show enlarged version
                    screenshot.setOnClickListener(v -> showEnlargedScreenshot(slot.screenshot, slot.title));
                } else {
                    screenshot.setVisibility(View.GONE);
                    screenshot.setOnClickListener(null);
                }

                // Save button - always enabled
                saveButton.setOnClickListener(v -> listener.onSave(slot.slot));

                // Load button - only enabled if slot has data
                loadButton.setEnabled(!slot.isEmpty);
                loadButton.setAlpha(slot.isEmpty ? 0.5f : 1.0f);
                loadButton.setOnClickListener(v -> {
                    if (!slot.isEmpty) {
                        listener.onLoad(slot.slot);
                    }
                });
            }
            
            private void showEnlargedScreenshot(byte[] screenshotData, String title) {
                if (screenshotData == null || screenshotData.length == 0) return;
                
                Context context = itemView.getContext();
                Bitmap bitmap = BitmapFactory.decodeByteArray(screenshotData, 0, screenshotData.length);
                
                // Create enlarged screenshot dialog
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context,
                        com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog);
                View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_screenshot_preview, null);
                
                ImageView enlargedImageView = dialogView.findViewById(R.id.iv_enlarged_screenshot);
                TextView titleView = dialogView.findViewById(R.id.tv_screenshot_title);
                
                enlargedImageView.setImageBitmap(bitmap);
                titleView.setText(title);
                
                builder.setView(dialogView)
                       .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
                       .create()
                       .show();
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        View view = getLayoutInflater().inflate(R.layout.dialog_saves, null, false);

        RecyclerView recyclerView = view.findViewById(R.id.rv_save_slots);
        recyclerView.setLayoutManager(new LinearLayoutManager(ctx));

        // Create save slots (1-10)
        List<SaveSlot> saveSlots = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            SaveSlot slot = new SaveSlot(i, NativeApp.getGamePathSlot(i));
            
            // Check if slot has data and get screenshot
            byte[] screenshot = NativeApp.getImageSlot(i);
            if (screenshot != null && screenshot.length > 0) {
                slot.isEmpty = false;
                slot.screenshot = screenshot;
                slot.title = "Save Slot " + i;
                
                // Create a reasonable timestamp (you might want to get this from native code)
                slot.timestamp = "Saved " + SimpleDateFormat.getDateTimeInstance(
                    SimpleDateFormat.SHORT, SimpleDateFormat.SHORT, Locale.getDefault())
                    .format(new Date());
            }
            
            saveSlots.add(slot);
        }

        SaveSlotAdapter adapter = new SaveSlotAdapter(saveSlots, new SaveSlotAdapter.OnSlotClickListener() {
            @Override
            public void onSave(int slot) {
                if (NativeApp.saveStateToSlot(slot)) {
                    // Success - refresh the dialog or close it
                    dismiss();
                }
                NativeApp.resume();
            }

            @Override
            public void onLoad(int slot) {
                if (NativeApp.loadStateFromSlot(slot)) {
                    // Success
                    dismiss();
                }
                NativeApp.resume();
            }
        });

        recyclerView.setAdapter(adapter);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ctx,
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog);
        builder.setTitle("Save States")
               .setView(view)
               .setNegativeButton("Cancel", (d, w) -> d.dismiss());

        return builder.create();
    }
}
