# Keep Xposed entry point
-keep class com.fakegps.app.xposed.** { *; }

# Keep Xposed API classes (provided at runtime by LSPosed/LSPatch)
-dontwarn de.robv.android.xposed.**
-keep class de.robv.android.xposed.** { *; }

# Keep OSMDroid (used for map tiles)
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Keep Google Play Services location
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.location.**

# General Android rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.app.Service
-keep public class * extends android.app.Activity
