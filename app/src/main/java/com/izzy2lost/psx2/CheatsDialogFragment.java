package com.izzy2lost.psx2;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.InputStream;
import java.util.ArrayList;

public class CheatsDialogFragment extends DialogFragment {

    private ArrayAdapter<String> adapter;
    private ArrayList<String> items = new ArrayList<>();

    private static final int REQ_IMPORT_PNACH = 1001;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        View view = LayoutInflater.from(ctx).inflate(R.layout.simple_list, null, false);
        ListView lv = view.findViewById(android.R.id.list);
        adapter = new ArrayAdapter<>(ctx, android.R.layout.simple_list_item_1, items);
        lv.setAdapter(adapter);
        lv.setOnItemLongClickListener((parent, v, position, id) -> {
            String name = items.get(position);
            deleteCheat(name);
            return true;
        });

        refreshList();

        return new MaterialAlertDialogBuilder(ctx, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setCustomTitle(UiUtils.centeredDialogTitle(ctx, "Manage Cheats"))
                .setView(view)
                .setNegativeButton("Close", null)
                .setPositiveButton("Import For Game", (d, w) -> startImport())
                .create();
    }

    private void refreshList() {
        items.clear();
        String[] names = NativeApp.listSafFilenames("cheats");
        if (names != null && names.length > 0) {
            for (String n : names) if (n != null && n.endsWith(".pnach")) items.add(n);
        } else {
            java.io.File dir = new java.io.File(requireContext().getExternalFilesDir(null), "cheats");
            if (!dir.exists()) dir.mkdirs();
            java.io.File[] arr = dir.listFiles((f, n) -> n != null && n.endsWith(".pnach"));
            if (arr != null) for (java.io.File f : arr) items.add(f.getName());
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void startImport() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, REQ_IMPORT_PNACH);
        } catch (Throwable t) {
            Toast.makeText(requireContext(), "No file picker available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT_PNACH && resultCode == android.app.Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            String serial = null;
            try { serial = NativeApp.getCurrentGameSerial(); } catch (Throwable ignored) {}
            if (serial == null || serial.isEmpty()) {
                Toast.makeText(requireContext(), "Unknown game serial", Toast.LENGTH_SHORT).show();
                return;
            }
            String outName = serial + ".pnach";
            android.net.Uri dataRoot = SafManager.getDataRootUri(requireContext());
            if (dataRoot != null) {
                androidx.documentfile.provider.DocumentFile target = SafManager.createChild(requireContext(), new String[]{"cheats"}, outName, "application/octet-stream");
                if (target != null) {
                    try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
                        if (in != null && SafManager.copyFromStream(requireContext(), in, target.getUri())) {
                            Toast.makeText(requireContext(), "Imported to Data Folder", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), "Import failed", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Import failed", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                java.io.File dir = new java.io.File(requireContext().getExternalFilesDir(null), "cheats");
                if (!dir.exists()) dir.mkdirs();
                java.io.File out = new java.io.File(dir, outName);
                try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                     java.io.FileOutputStream os = new java.io.FileOutputStream(out)) {
                    if (in != null) {
                        byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
                        os.flush();
                        Toast.makeText(requireContext(), "Imported", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Import failed", Toast.LENGTH_SHORT).show();
                }
            }
            refreshList();
        }
    }

    private void deleteCheat(String name) {
        android.net.Uri dataRoot = SafManager.getDataRootUri(requireContext());
        boolean ok = false;
        if (dataRoot != null) {
            androidx.documentfile.provider.DocumentFile f = SafManager.getChild(requireContext(), new String[]{"cheats"}, name);
            if (f != null) ok = f.delete();
        } else {
            java.io.File dir = new java.io.File(requireContext().getExternalFilesDir(null), "cheats");
            java.io.File f = new java.io.File(dir, name);
            ok = f.delete();
        }
        if (ok) {
            Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show();
            refreshList();
        } else {
            Toast.makeText(requireContext(), "Delete failed", Toast.LENGTH_SHORT).show();
        }
    }
}