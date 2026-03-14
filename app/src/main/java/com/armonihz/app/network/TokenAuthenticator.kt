package com.armonihz.app.network

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {

        // Evita loops infinitos si el token sigue fallando
        if (responseCount(response) >= 2) {
            return null
        }

        val user = FirebaseAuth.getInstance().currentUser ?: return null

        val newToken = runBlocking {
            user.getIdToken(true).await().token
        }

        return newToken?.let {
            response.request.newBuilder()
                .header("Authorization", "Bearer $it")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var priorResponse = response.priorResponse

        while (priorResponse != null) {
            result++
            priorResponse = priorResponse.priorResponse
        }

        return result
    }
}