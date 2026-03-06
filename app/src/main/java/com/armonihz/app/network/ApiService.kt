package com.armonihz.app.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*
import com.armonihz.app.network.model.ClientProfileResponse
import com.armonihz.app.network.model.UploadPhotoResponse
import com.armonihz.app.network.model.GenericResponse
import com.armonihz.app.network.model.DeletePhotoResponse
import com.armonihz.app.network.model.EventRequest
import com.armonihz.app.network.model.EventResponse
import com.armonihz.app.network.model.ProfileResponse
import com.armonihz.app.network.model.SyncGooglePhotoRequest

interface ApiService {

    @GET("v1/test")
    suspend fun getTest(): Response<GenericResponse>

    @Multipart
    @POST("api/v1/client/foto")
    suspend fun uploadProfilePhoto(
        @Part foto: MultipartBody.Part
    ): Response<UploadPhotoResponse>

    @DELETE("api/v1/client/foto")
    suspend fun deleteProfilePhoto(): Response<DeletePhotoResponse>

    @POST("api/v1/firebase-login")
    suspend fun firebaseLogin(
        @Body request: FirebaseLoginRequest
    ): Response<AuthResponse>

    @GET("api/v1/client/profile")
    suspend fun getProfile(): Response<ProfileResponse>

    @GET("api/v1/client/profile")
    suspend fun getClientProfile(
        @Header("Authorization") token: String
    ): Response<ClientProfileResponse>

    @POST("api/v1/client/sync-google-photo")
    suspend fun syncGooglePhoto(
        @Header("Authorization") token: String,
        @Body request: SyncGooglePhotoRequest
    ): Response<GenericResponse>


    @DELETE("api/v1/client/account")
    suspend fun deleteAccount(): Response<Unit>

    @POST("client/sync")
    suspend fun syncClient(
        @Header("Authorization") token: String,
        @Body data: Map<String, String>
    ): Response<Unit>

    // Añade esto a tu ApiService.kt

    @POST("api/v1/client/events")
    suspend fun createEvent(
        @Header("Authorization") token: String,
        @Body request: EventRequest
    ): Response<GenericResponse>

    @GET("api/v1/client/events")
    suspend fun getMyEvents(
        @Header("Authorization") token: String
    ): Response<List<EventResponse>>
}