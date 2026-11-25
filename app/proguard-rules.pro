# App-level R8 rules. The extension host's ABI lives in :extensions' consumer-rules.pro; JSON uses
# compile-time kotlinx.serialization serializers, so no model classes need keeping for reflection.

# No renaming: class and method names in stack traces stay readable (R8 still remaps line
# numbers — retrace with build/outputs/mapping/<type>/mapping.txt), and nothing a runtime-loaded
# extension resolves by name can be renamed by accident.
-dontobfuscate
