# BebeCup R8 rules. The release build is minified (R8 full mode, AGP 9).

# ML Kit (play-services-mlkit-face-detection in the play build, the bundled
# face-detection in the github build) finds its components through
# manifest <meta-data> and creates each ComponentRegistrar with
# Class.getDeclaredConstructor().newInstance(). R8 full mode keeps the class
# names (the library's rules) but drops the unused no-arg constructors, so
# every registrar fails with NoSuchMethodException, FaceDetection.getClient()
# throws a NullPointerException and the whole "베베컵에게 맡기기" scan stops
# (v0.7.1–v0.10.0 release builds; debug builds are not minified and were fine).
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
