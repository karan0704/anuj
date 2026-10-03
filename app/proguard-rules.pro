# SQLCipher is reached from native code by class and method name, so the
# shrinker must not rename or remove it.
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**
