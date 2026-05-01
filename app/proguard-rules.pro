# Keep data classes for Gson serialization
-keepclassmembers class com.eduspecial.data.remote.dto.** { *; }
-keepclassmembers class com.eduspecial.data.local.entities.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes *Annotation*

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Cloudinary
-keep class com.cloudinary.** { *; }

# Algolia
-keep class com.algolia.** { *; }
-keep class io.ktor.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Cloudinary optional integrations not used
-dontwarn com.bumptech.glide.**
-dontwarn com.squareup.picasso.**

# Ktor + slf4j optional dependencies
-dontwarn java.lang.management.**
-dontwarn org.slf4j.impl.**
-dontwarn org.slf4j.**

# WorkManager (Hilt managed)
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keep class androidx.hilt.work.** { *; }
