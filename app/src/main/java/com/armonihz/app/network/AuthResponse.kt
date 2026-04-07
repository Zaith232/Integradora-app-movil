package com.armonihz.app.network


data class AuthResponse(
    val data: AuthData
)

data class AuthData(
    val user: User,
    val token: String
)