package com.eduspecial.di

import com.eduspecial.update.GitHubUpdateService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    @Named("github_okhttp")
    fun provideGitHubOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            // GitHub API requires a User-Agent header — without it returns 403
            .addInterceptor { chain ->
                val request: Request = chain.request().newBuilder()
                    .header("User-Agent", "EduSpecial-Android")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .build()
                chain.proceed(request)
            }
            .build()

    @Provides
    @Singleton
    @Named("github")
    fun provideGitHubRetrofit(@Named("github_okhttp") okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideGitHubUpdateService(@Named("github") retrofit: Retrofit): GitHubUpdateService =
        retrofit.create(GitHubUpdateService::class.java)
}
