# Consumer ProGuard rules contributed by :data to apps that depend on it.
# Moshi reflective adapters / models used across the API DTOs:
-keep,allowobfuscation,allowshrinking @com.squareup.moshi.JsonClass class *
-keepclassmembers class * { @com.squareup.moshi.Json <fields>; }

# KotlinJsonAdapterFactory reflects these DTOs and the persisted offline metadata.
# Keep their constructors and property names while allowing the rest of the app to be optimized.
-keep class com.anilocal.app.data.metadata.anilist.GraphResponse { *; }
-keep class com.anilocal.app.data.metadata.anilist.GraphError { *; }
-keep class com.anilocal.app.data.metadata.anilist.AniListData { *; }
-keep class com.anilocal.app.data.metadata.anilist.PageDto { *; }
-keep class com.anilocal.app.data.metadata.anilist.MediaDto { *; }
-keep class com.anilocal.app.data.metadata.anilist.TitleDto { *; }
-keep class com.anilocal.app.data.metadata.anilist.CoverDto { *; }
-keep class com.anilocal.app.data.metadata.mal.MalLoadEntry { *; }
-keep class com.anilocal.app.data.metadata.tmdb.TmdbSearchResponse { *; }
-keep class com.anilocal.app.data.metadata.tmdb.TmdbShow { *; }
-keep class com.anilocal.app.data.skip.AniSkipResponse { *; }
-keep class com.anilocal.app.data.skip.AniSkipResult { *; }
-keep class com.anilocal.app.data.skip.AniSkipInterval { *; }
-keep class com.anilocal.app.domain.model.Subtitle { *; }
-keep class com.anilocal.app.domain.model.SkipMarker { *; }
-keep class com.anilocal.app.domain.model.SkipMarker$Type { *; }
