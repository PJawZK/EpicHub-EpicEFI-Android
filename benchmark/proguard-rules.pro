# Macrobenchmark is minified only because its tested EpicHub performance APK is R8-shrunk.
# AndroidX Core retains compatibility references to the API 23 FingerprintManager platform
# surface. EpicHub's benchmark/test APK has minSdk 26, so these platform classes exist on
# every supported benchmark device; suppress only this shrinker classpath warning.
-dontwarn android.hardware.fingerprint.FingerprintManager
-dontwarn android.hardware.fingerprint.FingerprintManager$AuthenticationCallback
-dontwarn android.hardware.fingerprint.FingerprintManager$AuthenticationResult
-dontwarn android.hardware.fingerprint.FingerprintManager$CryptoObject
