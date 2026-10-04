# kotlinx.serialization: le regole della libreria bastano per le classi
# annotate; si tengono i serializzatori generati del modulo raccolta.
-keep,includedescriptorclasses class org.costalonga.sportintv.raccolta.**$$serializer { *; }
-keepclassmembers class org.costalonga.sportintv.raccolta.** {
    *** Companion;
}
-keepclasseswithmembers class org.costalonga.sportintv.raccolta.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# OkHttp: avvisi su classi facoltative non presenti su Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
