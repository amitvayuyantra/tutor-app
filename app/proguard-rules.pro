# kotlinx.serialization: keep generated serializers for the backup file classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.tutordiary.app.data.backup.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.tutordiary.app.data.backup.**$$serializer { *; }
