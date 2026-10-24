# Library-provided R8 rules are used for Media3 and PdfBox-Android.
-dontwarn org.bouncycastle.**
-dontwarn org.apache.fontbox.**

# PdfBox-Android treats JPEG2000/JPX decoding as an optional dependency.
-dontwarn com.gemalto.jp2.**

# Keep app classes and Java bridge entry points safe under release shrinking.
-keep class com.darshan.compresser.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# TEMPORARY (while the launch crash is being tracked down): keep class names and line numbers so the
# crash report in Downloads / DD Compressor is readable. Remove these 2 lines once the app is stable.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
