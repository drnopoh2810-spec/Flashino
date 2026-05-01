package com.eduspecial.data.remote.tts

import com.eduspecial.data.remote.config.IFlytekTtsAccountConfig
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IFlytekWebSocketAuthTest {

    @Test
    fun `buildSignatureOrigin matches official format`() {
        val date = "Mon, 01 Jan 2024 00:00:00 GMT"

        val origin = IFlytekWebSocketAuth.buildSignatureOrigin(date)

        assertEquals(
            "host: tts-api-sg.xf-yun.com\ndate: Mon, 01 Jan 2024 00:00:00 GMT\nGET /v2/tts HTTP/1.1",
            origin
        )
    }

    @Test
    fun `buildSignature and authorization remain stable for known input`() {
        val date = "Mon, 01 Jan 2024 00:00:00 GMT"

        val signature = IFlytekWebSocketAuth.buildSignature("secret", date)
        val authorization = IFlytekWebSocketAuth.buildAuthorizationHeader("key", signature)

        assertEquals("hMKPxzlPXIqt/I6MyWbxAg8mN59Opm1T9v2VsNAt50k=", signature)
        assertEquals(
            "YXBpX2tleT0ia2V5IiwgYWxnb3JpdGhtPSJobWFjLXNoYTI1NiIsIGhlYWRlcnM9Imhvc3QgZGF0ZSByZXF1ZXN0LWxpbmUiLCBzaWduYXR1cmU9ImhNS1B4emxQWElxdC9JNk15V2J4QWc4bU41OU9wbTFUOXYyVnNOQXQ1MGs9Ig==",
            authorization
        )
    }

    @Test
    fun `buildWebSocketUrl includes encoded official query params`() {
        val account = IFlytekTtsAccountConfig(
            appId = "app",
            apiKey = "key",
            apiSecret = "secret"
        )
        val date = "Mon, 01 Jan 2024 00:00:00 GMT"

        val url = IFlytekWebSocketAuth.buildWebSocketUrl(account, date)

        assertTrue(url.startsWith("wss://tts-api-sg.xf-yun.com/v2/tts?authorization="))

        val query = url.substringAfter("?")
            .split("&")
            .associate {
                val key = it.substringBefore("=")
                val value = URLDecoder.decode(it.substringAfter("="), StandardCharsets.UTF_8.name())
                key to value
            }

        assertEquals("tts-api-sg.xf-yun.com", query["host"])
        assertEquals(date, query["date"])
        assertEquals(
            "YXBpX2tleT0ia2V5IiwgYWxnb3JpdGhtPSJobWFjLXNoYTI1NiIsIGhlYWRlcnM9Imhvc3QgZGF0ZSByZXF1ZXN0LWxpbmUiLCBzaWduYXR1cmU9ImhNS1B4emxQWElxdC9JNk15V2J4QWc4bU41OU9wbTFUOXYyVnNOQXQ1MGs9Ig==",
            query["authorization"]
        )
    }
}
