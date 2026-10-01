# Keep PDFBox service/resource discovery intact.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class androidx.media3.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.apache.fontbox.**
