# --- kotlinx.serialization ---------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }

# App @Serializable models (incl. sealed PlaylistSource and its polymorphic subclasses)
-keep,includedescriptorclasses class dev.iptv.player.**$$serializer { *; }
-keepclassmembers class dev.iptv.player.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class dev.iptv.player.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class dev.iptv.player.data.PlaylistSource { *; }
-keep class dev.iptv.player.data.PlaylistSource$* { *; }

# --- Media3 / ExoPlayer (consumer rules ship with the libs; keep reflective bits) ---
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.hls.** { *; }

# --- OkHttp / Okio -------------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Coil ------------------------------------------------------------------------
-dontwarn coil.**

# --- Crash reports stay readable ----------------------------------------------------
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
