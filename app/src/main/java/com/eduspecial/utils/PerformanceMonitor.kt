package com.eduspecial.utils

import android.util.Log
import com.eduspecial.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerformanceMonitor @Inject constructor() {

    private val TAG = "PerformanceMonitor"
    
    // تسجيل زمن استجابة آخر 10 طلبات لحساب المتوسط
    private val responseTimes = mutableListOf<Long>()

    fun getInterceptor() = Interceptor { chain ->
        val request = chain.request()
        val startTime = System.currentTimeMillis()
        
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            throw e
        }
        
        val duration = System.currentTimeMillis() - startTime
        logPerformance(request.url.toString(), duration, response.code)
        
        response
    }

    private fun logPerformance(url: String, duration: Long, code: Int) {
        synchronized(responseTimes) {
            responseTimes.add(duration)
            if (responseTimes.size > 10) responseTimes.removeAt(0)
        }
        
        val average = responseTimes.average()
        
        // إذا كان الاستجابة أبطأ من 2 ثانية، نعتبره أداءً ضعيفاً
        if (BuildConfig.DEBUG && duration > 2000) {
            Log.w(TAG, "⚠️ Slow API Warning: $url took ${duration}ms")
        }
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "API Call: $url | Duration: ${duration}ms | Code: $code | Avg: ${average.toInt()}ms")
        }
    }

    fun getAverageResponseTime(): Double = synchronized(responseTimes) {
        return if (responseTimes.isEmpty()) 0.0 else responseTimes.average()
    }
}
