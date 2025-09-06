package com.izzy2lost.psx2;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.view.Surface;
import java.io.File;
import java.lang.ref.WeakReference;

public class NativeApp {
	static {
		try {
			System.loadLibrary("emucore");
			hasNoNativeBinary = false;
		} catch (UnsatisfiedLinkError e) {
			hasNoNativeBinary = true;
		}
	}

	public static boolean hasNoNativeBinary;


	protected static WeakReference<Context> mContext;
	public static Context getContext() {
		return mContext.get();
	}

	public static void initializeOnce(Context context) {
		mContext = new WeakReference<>(context);
		File externalFilesDir = context.getExternalFilesDir(null);
		if (externalFilesDir == null) {
			externalFilesDir = context.getDataDir();
		}
		initialize(externalFilesDir.getAbsolutePath(), android.os.Build.VERSION.SDK_INT);
	}

	public static native void initialize(String path, int apiVer);
    public static native String getGameTitle(String path);
    public static native String getGameTitleFromUri(String gameUri);
	public static native String getGameSerial();
	public static native float getFPS();

	public static native String getPauseGameTitle();
	public static native String getPauseGameSerial();

	public static native void setPadVibration(boolean isonoff);
	public static native void setPadButton(int index, int range, boolean iskeypressed);
	public static native void resetKeyStatus();

	public static native void setAspectRatio(int type);
	public static native void speedhackLimitermode(int value);
	public static native void speedhackEecyclerate(int value);
	public static native void speedhackEecycleskip(int value);

	public static native void renderUpscalemultiplier(float value);
	public static native void renderMipmap(int value);
	public static native void renderHalfpixeloffset(int value);
	public static native void renderGpu(int value);
	public static native void renderPreloading(int value);

	// HUD/OSD visibility toggle
	public static native void setHudVisible(boolean visible);
	
	// Widescreen and interlacing patches
	    public static native void setWidescreenPatches(boolean enabled);
    public static native void setNoInterlacingPatches(boolean enabled);
    
    // Texture loading options for texture packs
    public static native void setLoadTextures(boolean enabled);
    public static native void setAsyncTextureLoading(boolean enabled);
    public static native void setBlendingAccuracy(int level);
    
    // Shade Boost (brightness/contrast/saturation)
    public static native void setShadeBoost(boolean enabled);
    public static native void setShadeBoostBrightness(int brightness);
    public static native void setShadeBoostContrast(int contrast);
    public static native void setShadeBoostSaturation(int saturation);

    // Apply multiple settings in one atomic batch (safer live updates)
    public static native void applyGlobalSettingsBatch(int renderer,
                                                       float upscaleMultiplier,
                                                       int aspectRatio,
                                                       int blendingAccuracy,
                                                       boolean widescreenPatches,
                                                       boolean noInterlacingPatches,
                                                       boolean loadTextures,
                                                       boolean asyncTextureLoading,
                                                       boolean hudVisible);
    
    // Apply per-game settings (subset) in one batch
    public static native void applyPerGameSettingsBatch(int renderer,
                                                        float upscaleMultiplier,
                                                        int blendingAccuracy,
                                                        boolean widescreenPatches,
                                                        boolean noInterlacingPatches,
                                                        boolean enablePatches,
                                                        boolean enableCheats);

    // Query current runtime renderer from the core (reflects global/per-game)
    public static native int getCurrentRenderer();

    // Per-game settings
    public static native void saveGameSettings(String filename, int blendingAccuracy, int renderer, 
                                              int resolution, boolean widescreenPatches, 
                                              boolean noInterlacingPatches, boolean enablePatches, 
                                              boolean enableCheats);
    public static native void saveGameSettingsToPath(String fullPath, int blendingAccuracy, int renderer, 
                                                     int resolution, boolean widescreenPatches, 
                                                     boolean noInterlacingPatches, boolean enablePatches, 
                                                     boolean enableCheats);
    public static native void deleteGameSettings(String filename);
    public static native String getGameSerial(String gameUri);
    public static native String getGameCrc(String gameUri);
    public static native String getCurrentGameSerial();
    
    // Synchronization object for CDVD operations to prevent crashes
    private static final Object CDVD_LOCK = new Object();
    
    // Synchronized wrapper for getGameSerial to prevent CDVD race conditions
    public static String getGameSerialSafe(String gameUri) {
        synchronized (CDVD_LOCK) {
            try {
                return getGameSerial(gameUri);
            } catch (Exception e) {
                return "";
            }
        }
    }
    
    // Synchronized wrapper for getGameTitleFromUri to prevent CDVD race conditions
    public static String getGameTitleFromUriSafe(String gameUri) {
        synchronized (CDVD_LOCK) {
            try {
                return getGameTitleFromUri(gameUri);
            } catch (Exception e) {
                return "";
            }
        }
    }
    
    // Synchronized wrapper for getGameCrc to prevent CDVD race conditions
    public static String getGameCrcSafe(String gameUri) {
        synchronized (CDVD_LOCK) {
            try {
                return getGameCrc(gameUri);
            } catch (Exception e) {
                return "";
            }
        }
    }

	public static native void onNativeSurfaceCreated();
	public static native void onNativeSurfaceChanged(Surface surface, int w, int h);
	public static native void onNativeSurfaceDestroyed();

	public static native boolean runVMThread(String path);

	public static native void pause();
	public static native void resume();
	public static native void shutdown();

	public static native boolean saveStateToSlot(int slot);
	public static native boolean loadStateFromSlot(int slot);
	public static native String getGamePathSlot(int slot);
	public static native byte[] getImageSlot(int slot);

	// Call jni
	public static int openContentUri(String uriString) {
		Context _context = getContext();
		if(_context != null) {
			ContentResolver _contentResolver = _context.getContentResolver();
			try {
				ParcelFileDescriptor filePfd = _contentResolver.openFileDescriptor(Uri.parse(uriString), "r");
				if (filePfd != null) {
					return filePfd.detachFd();  // Take ownership of the fd.
				}
			} catch (Exception ignored) {}
		}
		return -1;
	}
}
