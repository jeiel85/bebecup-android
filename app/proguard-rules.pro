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

# TensorFlow Lite (아이 얼굴 구분): InterpreterApi.create() finds its runtime with
# Class.forName("…InterpreterFactoryImpl").getDeclaredConstructor().newInstance() —
# org.tensorflow.lite.* from LiteRT in the github build, com.google.android.gms.tflite.*
# from Play services in the play build. The libraries keep @UsedByReflection classes but,
# in R8 full mode, not their constructors, so release builds failed with
# NoSuchMethodException: InterpreterFactoryImpl.<init> (found on the emulator before release).
-keepclassmembers @org.tensorflow.lite.annotations.UsedByReflection class * {
    <init>(...);
}
-keep class org.tensorflow.lite.InterpreterFactoryImpl {
    <init>();
}
-keep class com.google.android.gms.tflite.InterpreterFactoryImpl {
    <init>();
}
