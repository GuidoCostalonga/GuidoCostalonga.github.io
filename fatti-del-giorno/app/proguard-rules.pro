# kotlinx.serialization: si tengono i serializzatori generati delle classi
# annotate di questo modulo.
-keep,includedescriptorclasses class org.costalonga.fattidelgiorno.**$$serializer { *; }
-keepclassmembers class org.costalonga.fattidelgiorno.** {
    *** Companion;
}
-keepclasseswithmembers class org.costalonga.fattidelgiorno.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# OkHttp: avvisi su classi facoltative non presenti su Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
