package com.izzy2lost.psx2;

import android.content.Context;
import android.view.Gravity;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

class UiUtils {
    static TextView centeredDialogTitle(Context ctx, String title) {
        TextView tv = new TextView(ctx);
        tv.setText(title);
        tv.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (ctx.getResources().getDisplayMetrics().density * 16);
        tv.setPadding(pad, pad, pad, pad / 2);
        tv.setTextAppearance(ctx, com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        // Use brand primary (now mapped to brighter pink/purple) for dialog titles
        try { tv.setTextColor(ContextCompat.getColor(ctx, R.color.brand_primary)); } catch (Throwable ignored) {}
        return tv;
    }
}
