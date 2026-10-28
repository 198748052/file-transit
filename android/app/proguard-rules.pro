# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.filetransit.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.filetransit.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
