# Performance builds are R8-optimized. Keep the JNI bridge class/member names stable
# because the Rust library exports native entry points against these JVM names.
-keep class com.epicefi.epichub.engine.NativeEngineBridge { *; }

# Keep native method names on any future bridge classes unless they are migrated to
# explicit RegisterNatives-based binding.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
