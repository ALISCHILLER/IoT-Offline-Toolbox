# Keep Kotlin serialization metadata used by the offline store.
-keepattributes *Annotation*,InnerClasses,EnclosingMethod
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$Companion Companion;
}
-keepclasseswithmembers class **$$serializer {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.slf4j.**
