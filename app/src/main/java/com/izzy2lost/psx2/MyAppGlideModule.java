package com.izzy2lost.psx2;

import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;

@GlideModule
public final class MyAppGlideModule extends AppGlideModule {
    @Override
    public boolean isManifestParsingEnabled() {
        // Avoid manifest parsing to speed up initialization and prevent double modules
        return false;
    }
}

