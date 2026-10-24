# Library-provided R8 rules are used for Media3 and PdfBox-Android.
-dontwarn org.bouncycastle.**
-dontwarn org.apache.fontbox.**
-dontwarn com.gemalto.jp2.**

# Preserve AndroidX Startup / WorkManager / Room classes that may be initialized
# through manifest providers or reflection in release builds.
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod,SourceFile,LineNumberTable
-keep class androidx.startup.** { *; }
-keep class * implements androidx.startup.Initializer { <init>(...); *; }
-keep class androidx.work.** { *; }
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(...); *; }
-keep class * extends androidx.room.RoomOpenHelper$Delegate { <init>(...); *; }

# Google Mobile Ads SDK and UMP reflection/manifest integration.
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.internal.ads.** { *; }
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.gms.**
-dontwarn com.google.android.ump.**
