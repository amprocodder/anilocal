# Consumer ProGuard rules contributed by :data to apps that depend on it.
# None needed: JSON (de)serializers are generated at compile time by kotlinx.serialization and are
# referenced directly (`X.serializer()`), so nothing in :data is looked up reflectively.
