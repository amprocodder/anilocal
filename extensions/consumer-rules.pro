# The extension host's ABI. Extensions are separate APKs that AnimeExtensionLoader class-loads at
# runtime; they link — by name — against the host's copies of the vendored Aniyomi source API and
# of the libraries they're compiled against but don't bundle. R8 can't see those callers, so every
# public/protected member they could use must survive unrenamed. A member R8 drops surfaces only as
# a source that silently finds nothing (or a NoSuchMethodError inside a runCatching), so this list
# mirrors what Aniyomi itself keeps for the same extensions.

# The vendored source API and helpers. Extensions subclass these (AnimeHttpSource,
# ParsedAnimeHttpSource, ConfigurableAnimeSource…), so they're kept strictly — no optimization that
# could finalize a class or reshape a member.
-keep class eu.kanade.** { public protected *; }
-keep class tachiyomi.** { public protected *; }

# Libraries extensions use from the host (the set Aniyomi keeps).
-keep,allowoptimization class kotlin.** { public protected *; }
-keep,allowoptimization class kotlinx.coroutines.** { public protected *; }
-keep,allowoptimization class kotlinx.serialization.** { public protected *; }
-keep,allowoptimization class okhttp3.** { public protected *; }
-keep,allowoptimization class okio.** { public protected *; }
-keep,allowoptimization class org.jsoup.** { public protected *; }
-keep,allowoptimization class rx.** { public protected *; }
-keep,allowoptimization class uy.kohesive.injekt.** { public protected *; }
-keep,allowoptimization class dev.mihon.injekt.** { public protected *; }
-keep,allowoptimization class app.cash.quickjs.** { public protected *; }
-keep,allowoptimization class androidx.preference.** { public protected *; }

# Injekt keys its registry by the generic type captured in `object : FullTypeReference<T>() {}`
# (read back through getGenericSuperclass). The host's registrations (AniyomiInjektModule) must
# keep that signature or they'd never match the types extensions ask for.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class * extends uy.kohesive.injekt.api.FullTypeReference

# RxJava 1: its Unsafe-backed queues address these fields reflectively.
-dontwarn sun.misc.**
-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
    long producerIndex;
    long consumerIndex;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode producerNode;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode consumerNode;
}
