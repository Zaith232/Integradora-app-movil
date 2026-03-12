package com.armonihz.app.network

import android.content.Context
import com.armonihz.app.auth.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {

        val token = TokenManager.getToken(context)

        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token") // ⬅️ CAMBIO AQUÍ: Usar .header en lugar de .addHeader
                .build()
        } else {
            chain.request()
        }

        return chain.proceed(request)
    }
}