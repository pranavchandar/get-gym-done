# Keep kotlinx.serialization metadata
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.getgymdone.app.**$$serializer { *; }
-keepclassmembers class com.getgymdone.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.getgymdone.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
