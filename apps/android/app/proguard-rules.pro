# Keep rules for kotlinx.serialization (needed if minification is enabled).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class fr.fuelradar.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class fr.fuelradar.**$$serializer { *; }
-keep @kotlinx.serialization.Serializable class fr.fuelradar.** { *; }
