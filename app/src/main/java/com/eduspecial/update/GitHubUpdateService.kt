package com.eduspecial.update

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface GitHubUpdateService {
    @GET
    suspend fun getJson(@Url url: String): Response<ResponseBody>
}
