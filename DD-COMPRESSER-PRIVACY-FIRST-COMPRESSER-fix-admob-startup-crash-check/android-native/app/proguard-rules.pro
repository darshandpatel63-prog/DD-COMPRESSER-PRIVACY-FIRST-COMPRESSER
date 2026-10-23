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
