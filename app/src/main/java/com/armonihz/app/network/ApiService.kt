package com.armonihz.app.network

import com.armonihz.app.network.model.AcceptResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*
import com.armonihz.app.network.model.ClientProfileResponse
import com.armonihz.app.network.model.UploadPhotoResponse
import com.armonihz.app.network.model.GenericResponse
import com.armonihz.app.network.model.DeletePhotoResponse
import com.armonihz.app.network.model.EventApplicationsResponse
import com.armonihz.app.network.model.EventRequest
import com.armonihz.app.network.model.EventResponse
import com.armonihz.app.network.model.MusicianProfileDetailResponse
import com.armonihz.app.network.model.MusicianProfileWrapperResponse
import com.armonihz.app.network.model.PaginatedMusiciansWrapper
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

    @POST("api/v1/client/sync") // ⬅️ Le agregué api/v1/ aquí para mantener consistencia, revisa si es correcto
    suspend fun syncClient(
        @Header("Authorization") token: String,
        @Body data: Map<String, String>
    ): Response<Unit>

    @POST("api/v1/client/events")
    suspend fun createEvent(
        @Header("Authorization") token: String,
        @Body request: EventRequest
    ): Response<GenericResponse>

    // 1. Obtener los eventos del cliente
    @GET("api/v1/client/events")
    suspend fun getMyEvents(
        @Header("Authorization") token: String
    ): Response<List<EventResponse>>

    // 2. Obtener las propuestas de un evento específico
    @GET("api/v1/client/events/{id}/applications")
    suspend fun getEventApplications(
        @Header("Authorization") token: String,
        @Path("id") eventId: Int
    ): Response<EventApplicationsResponse>

    // 3. Aceptar una propuesta
    @POST("api/v1/client/events/{eventId}/applications/{appId}/accept")
    suspend fun acceptApplication(
        @Header("Authorization") token: String,
        @Path("eventId") eventId: Int,
        @Path("appId") appId: Int
    ): Response<AcceptResponse>

    // 4. ACTUALIZAR UN EVENTO (NUEVO)
    @PUT("api/v1/client/events/{id}")
    suspend fun updateEvent(
        @Header("Authorization") token: String,
        @Path("id") eventId: Int,
        @Body request: EventRequest
    ): Response<GenericResponse>

    // Obtener el perfil de un músico específico
// Obtener el perfil de un músico específico
    @GET("api/v1/musicians/{id}")
    suspend fun getMusicianProfile(
        @Path("id") musicianId: Int
    ): Response<MusicianProfileWrapperResponse> // ⬅️ Cambiamos el tipo aquí

    // Asegúrate de importar esto arriba: import com.google.gson.JsonObject

    @GET("api/v1/musicians")
    suspend fun getAllMusicians(): retrofit2.Response<com.google.gson.JsonObject>
}