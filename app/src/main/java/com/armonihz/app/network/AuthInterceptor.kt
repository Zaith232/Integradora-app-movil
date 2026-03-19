package com.armonihz.app.network

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestOriginal = chain.request()

        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            return chain.proceed(requestOriginal)
        }

        return try {
            val token = runBlocking {
                user.getIdToken(true).await().token
            }

            val newRequest = requestOriginal.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()

            chain.proceed(newRequest)

        } catch (e: Exception) {
            chain.proceed(requestOriginal)
        }
    }
}