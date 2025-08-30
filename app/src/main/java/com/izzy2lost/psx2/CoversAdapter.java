package com.izzy2lost.psx2;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import java.io.File;

public class CoversAdapter extends RecyclerView.Adapter<CoversAdapter.VH> {
    public interface OnItemClick {
        void onClick(int position);
    }

    public interface OnItemLongClick {
        void onLongClick(int position);
    }

    private final Context context;
    private final String[] titles;
    private final String[] coverUrls;
    private final String[] localPaths; // absolute file paths for cached covers (may be null)
    private final OnItemClick onItemClick;
    private final OnItemLongClick onItemLongClick;

    public CoversAdapter(Context context, String[] titles, String[] coverUrls, String[] localPaths, OnItemClick click) {
        this(context, titles, coverUrls, localPaths, click, null);
    }

    public CoversAdapter(Context context, String[] titles, String[] coverUrls, String[] localPaths, OnItemClick click, OnItemLongClick longClick) {
        this.context = context;
        this.titles = titles;
        this.coverUrls = coverUrls;
        this.localPaths = localPaths;
        this.onItemClick = click;
        this.onItemLongClick = longClick;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cover, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.title.setText(titles[position]);
        String local = (localPaths != null && position < localPaths.length) ? localPaths[position] : null;
        File localFile = null;
        if (local != null) {
            File f = new File(local);
            if (f.exists() && f.length() > 0) localFile = f;
        }

        if (localFile != null) {
            Glide.with(context)
                    .load(localFile)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .fitCenter()
                    .placeholder(android.R.color.transparent)
                    .error(android.R.color.transparent)
                    .into(holder.cover);
        } else {
            // Do not load from network automatically; wait for explicit download
            holder.cover.setImageDrawable(null);
        }
        holder.itemView.setOnClickListener(v -> {
            if (onItemClick != null) onItemClick.onClick(position);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (onItemLongClick != null) {
                onItemLongClick.onLongClick(position);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return titles.length;
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView cover;
        final TextView title;
        VH(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.image_cover);
            title = itemView.findViewById(R.id.text_title);
        }
    }
}
