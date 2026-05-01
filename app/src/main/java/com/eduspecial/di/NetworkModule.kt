package com.eduspecial.di

import com.eduspecial.data.remote.api.SupabaseAuthService
import com.eduspecial.data.remote.api.SupabaseRestService
import com.eduspecial.BuildConfig
import com.eduspecial.utils.PerformanceMonitor
import com.eduspecial.utils.RetryInterceptor
import com.eduspecial.utils.UserPreferencesDataStore
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthInterceptor

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RetryInterceptorQualifier

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @AuthInterceptor
    @Provides @Singleton
    fun provideAuthInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request().newBuilder().apply {
            addHeader("Accept", "application/json")
        }.build()
        chain.proceed(request)
    }

    @RetryInterceptorQualifier
    @Provides @Singleton
    fun provideRetryInterceptor(): Interceptor = RetryInterceptor()

    @Provides @Singleton
    fun provideOkHttpClient(
        @AuthInterceptor authInterceptor: Interceptor,
        @RetryInterceptorQualifier retryInterceptor: Interceptor,
        performanceMonitor: PerformanceMonitor
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(retryInterceptor)
            .addInterceptor(performanceMonitor.getInterceptor())
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    @Named("supabase_apikey")
    fun provideSupabaseApiKeyInterceptor(
        prefs: UserPreferencesDataStore
    ): Interceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
            .addHeader("apikey", com.eduspecial.BuildConfig.SUPABASE_ANON_KEY)
            .addHeader("Accept", "application/json")

        val shouldAttachSession = original.url.encodedPath.startsWith("/rest/v1/") &&
            original.header("Authorization").isNullOrBlank()
        if (shouldAttachSession) {
            val token = runBlocking { prefs.authToken.first() }
            if (!token.isNullOrBlank()) {
                builder.addHeader("Authorization", "Bearer $token")
            }
        }

        val request = builder.build()
        chain.proceed(request)
    }

    @Provides
    @Singleton
    @Named("supabase")
    fun provideSupabaseOkHttpClient(
        @Named("supabase_apikey") apiKeyInterceptor: Interceptor,
        @RetryInterceptorQualifier retryInterceptor: Interceptor,
        performanceMonitor: PerformanceMonitor
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(retryInterceptor)
            .addInterceptor(performanceMonitor.getInterceptor())
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    @Named("supabase")
    fun provideSupabaseRetrofit(
        @Named("supabase") client: OkHttpClient
    ): Retrofit {
        val gson = GsonBuilder().setLenient().create()
        val baseUrl = com.eduspecial.BuildConfig.SUPABASE_URL
            .let { if (it.endsWith("/")) it else "$it/" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideSupabaseAuthService(@Named("supabase") retrofit: Retrofit): SupabaseAuthService =
        retrofit.create(SupabaseAuthService::class.java)

    @Provides
    @Singleton
    fun provideSupabaseRestService(@Named("supabase") retrofit: Retrofit): SupabaseRestService =
        retrofit.create(SupabaseRestService::class.java)

}
