# Consumer ProGuard rules contributed by :data to apps that depend on it.
# Moshi reflective adapters / models used across the API DTOs:
-keep,allowobfuscation,allowshrinking @com.squareup.moshi.JsonClass class *
-keepclassmembers class * { @com.squareup.moshi.Json <fields>; }
