# R8 rules for the release build of אמא על הקו (see docs/RELEASE.md).
#
# The game is plain Java with no reflection, no serialization of class names and no native code.
# The Activity is kept automatically through the manifest, and the save format uses explicit string
# keys ("money=...;day=...;"), so renaming classes and fields cannot break existing saves.
# That is why almost nothing needs an explicit keep rule here.

# Keep file names and line numbers so crash reports in Play Console can be read. Upload
# app/build/outputs/mapping/release/mapping.txt with each release (Play Console does this
# automatically when the mapping file is inside the bundle, which AGP does by default).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The shell calls Game through its public API only; keeping the public surface of the core makes
# stack traces from the field easier to match with the desktop harness. It costs a few kilobytes.
-keep public class com.wizzcraft.ima.core.Game {
    public *;
}

# If the engine ever starts creating classes by name (Class.forName, getDeclaredField, ...) or
# turning enum names into saved text and back, add a rule here, for example:
#   -keep class com.wizzcraft.ima.core.** { *; }
# and run the release APK through a full play session before uploading.

# When the Play Billing Library is added it ships its own consumer rules; nothing is needed here.
