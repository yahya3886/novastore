# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class dev.novastore.app.data.** { *; }
-dontwarn sun.misc.**

# General
-keepattributes *Annotation*, InnerClasses, Signature
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
