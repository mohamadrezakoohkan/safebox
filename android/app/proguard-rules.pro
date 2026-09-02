# Release builds strip debug logging mechanically (no-logging rule, §8.6).
# Lock internals never write w/e logs in the first place.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# kotlinx-serialization and Room ship consumer rules; nothing extra needed here.
