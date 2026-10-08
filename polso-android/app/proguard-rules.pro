# kotlinx.serialization: si tengono i serializzatori generati del motore.
-keep,includedescriptorclasses class org.costalonga.polso.motore.**$$serializer { *; }
-keepclassmembers class org.costalonga.polso.motore.** { *** Companion; }
-keepclasseswithmembers class org.costalonga.polso.motore.** { kotlinx.serialization.KSerializer serializer(...); }
# LiteRT-LM usa JNI e riflessione (gson, kotlin-reflect): non va offuscato.
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
-keep class com.google.gson.** { *; }
-dontwarn kotlin.reflect.jvm.internal.**
-keep,includedescriptorclasses class org.costalonga.polso.**$$serializer { *; }
-keepclassmembers class org.costalonga.polso.** { *** Companion; }
