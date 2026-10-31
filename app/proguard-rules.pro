# ---- NexLock release rules ----
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, SourceFile, LineNumberTable
-keepnames class com.nexlock.** { *; }

# Kotlinx serialization (backup envelopes)
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Hilt / Dagger generated code is handled by its own consumer rules.

# Never strip crypto entry points referenced via reflection-free JCE names.
-keep class javax.crypto.** { *; }
