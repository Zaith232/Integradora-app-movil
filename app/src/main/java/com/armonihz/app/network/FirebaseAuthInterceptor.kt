package com.armonihz.app.network

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

class FirebaseAuthInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {

        val requestBuilder = chain.request().newBuilder()

        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {

            val token = runBlocking {
                user.getIdToken(false).await().token
            }

            if (token != null) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
        }

        return chain.proceed(requestBuilder.build())
    }
}