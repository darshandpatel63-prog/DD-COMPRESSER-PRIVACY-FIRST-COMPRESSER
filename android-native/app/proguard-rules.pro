# Library-provided R8 rules are used for Media3 and PdfBox-Android.
-dontwarn org.bouncycastle.**
-dontwarn org.apache.fontbox.**

# PdfBox-Android treats JPEG2000/JPX decoding as an optional dependency.
# The app does not bundle JP2Android, so R8 must not fail on its optional reference.
-dontwarn com.gemalto.jp2.**
