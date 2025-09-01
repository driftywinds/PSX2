package com.izzy2lost.psx2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class LettersAdapter extends RecyclerView.Adapter<LettersAdapter.VH> {
    public interface OnClick {
        void onClick(char letter);
    }

    private final List<Character> letters;
    private final OnClick onClick;

    public LettersAdapter(List<Character> letters, OnClick onClick) {
        this.letters = letters;
        this.onClick = onClick;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        // Ensure uniqueness across the infinite range by including position
        return position;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_letter, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        int n = letters != null ? letters.size() : 0;
        if (n == 0) return;
        int real = position % n;
        char ch = letters.get(real);
        holder.text.setText(String.valueOf(ch));
        holder.itemView.setOnClickListener(v -> {
            android.util.Log.d("LettersAdapter", "Letter clicked: " + ch + " at position " + position);
            if (onClick != null) {
                onClick.onClick(ch);
            } else {
                android.util.Log.d("LettersAdapter", "onClick is null!");
            }
        });
    }

    @Override
    public int getItemCount() {
        return (letters != null && !letters.isEmpty()) ? Integer.MAX_VALUE : 0;
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView text;
        VH(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.text_letter);
        }
    }
}
