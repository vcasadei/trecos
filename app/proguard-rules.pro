# App-specific R8 rules. Libraries ship their own consumer rules.

# Release builds drop debug and verbose logging (design, Security & Observability).
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
