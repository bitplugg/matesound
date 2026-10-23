# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.bitplugg.matesound.**$$serializer { *; }
-keepclassmembers class dev.bitplugg.matesound.** {
    *** Companion;
}
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
# Media3
-dontwarn androidx.media3.**
