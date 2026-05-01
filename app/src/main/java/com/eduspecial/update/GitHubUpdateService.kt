package com.eduspecial.update

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface GitHubUpdateService {
    /**
     * Returns Response<> wrapper so we can inspect HTTP codes manually.
     * 404 = no releases published yet (treat as up-to-date, not an error).
     * 200 = release found, parse body.
     */
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubRelease>
}
