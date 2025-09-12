# PSX2 Emulator ProGuard Rules

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep NativeApp class and all its methods (JNI interface)
-keep class com.izzy2lost.psx2.NativeApp {
    public static <methods>;
}

# Keep MainActivity for proper lifecycle
-keep class com.izzy2lost.psx2.MainActivity {
    public <methods>;
}

# Keep all classes that might be referenced from native code
-keep class com.izzy2lost.psx2.** { *; }

# Keep Glide for image loading
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}

# Keep Material Design components
-keep class com.google.android.material.** { *; }
-keep interface com.google.android.material.** { *; }

# Keep AndroidX components
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Optimize and obfuscate
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile