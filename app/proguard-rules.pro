# Keep kotlinx.serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.nexusforge.app.**$$serializer { *; }
-keepclassmembers class com.nexusforge.app.** { *** Companion; }
-keepclasseswithmembers class com.nexusforge.app.** { kotlinx.serialization.KSerializer serializer(...); }
