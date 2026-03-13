# Pixora Launcher ProGuard rules
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keep class com.orbix.pixora.launcher.data.models.** { *; }
