# Library-provided R8 rules are used for Media3 and PdfBox-Android.
-dontwarn org.bouncycastle.**
-dontwarn org.apache.fontbox.**

# PdfBox-Android treats JPEG2000/JPX decoding as an optional dependency.
# The app does not bundle JP2Android, so R8 must not fail on its optional reference.
-dontwarn com.gemalto.jp2.**

# Keep metadata that Google Ads / UMP / Media3 need at runtime after R8 shrinking.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Crash safety net (Application class is referenced from the manifest).
-keep class com.darshan.compresser.DDApp { *; }
