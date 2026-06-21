# Keep Moshi-generated adapters / model classes reflected at runtime.
-keep,allowobfuscation,allowshrinking @com.squareup.moshi.JsonClass class *
-keepclassmembers class * { @com.squareup.moshi.Json <fields>; }
