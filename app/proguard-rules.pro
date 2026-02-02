# TideWatch ProGuard Rules

# Keep data models for Room
-keep class com.tidewatch.data.models.** { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# WearOS
-keep class androidx.wear.** { *; }
