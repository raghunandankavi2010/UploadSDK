# Upload SDK Consumer ProGuard Rules
# These rules are automatically applied to the app that consumes this library.

# Keep models for Gson serialization
-keep class com.uploadsdk.domain.model.** { *; }
-keep class com.uploadsdk.data.remote.dto.** { *; }
-keep class com.uploadsdk.data.local.entity.** { *; }

# Keep Room entities
-keepclassmembers class * {
    @androidx.room.PrimaryKey <fields>;
    @androidx.room.ColumnInfo <fields>;
    @androidx.room.Embedded <fields>;
}

# Keep Retrofit interfaces
-keep interface com.uploadsdk.data.remote.api.UploadApiService { *; }

# Keep Hilt workers
-keepclassmembers class * extends androidx.work.Worker {
    <init>(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coroutines
-dontwarn kotlinx.coroutines.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
