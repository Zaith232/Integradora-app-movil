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
import com.armonihz.app.network.model.FavoriteMusiciansResponse
import com.armonihz.app.network.model.FcmTokenRequest
import com.armonihz.app.network.model.Genre
import com.armonihz.app.network.model.MusicianProfileDetailResponse
import com.armonihz.app.network.model.MusicianProfileWrapperResponse
import com.armonihz.app.network.model.PaginatedMusiciansWrapper
import com.armonihz.app.network.model.ProfileResponse
import com.armonihz.app.network.model.SyncGooglePhotoRequest

interface ApiService {

    @GET("v1/test")
    suspend fun getTest(): Response<GenericResponse>

    @Multipart
    @POST("client/foto")
    suspend fun uploadProfilePhoto(
        @Part foto: MultipartBody.Part
    ): Response<UploadPhotoResponse>

    @DELETE("client/foto")
    suspend fun deleteProfilePhoto(): Response<DeletePhotoResponse>

    @POST("firebase-login")
    suspend fun firebaseLogin(
        @Body request: FirebaseLoginRequest
    ): Response<AuthResponse>

    // Enviar el FCM Token al servidor para las notificaciones
    // Enviar el FCM Token al servidor
    @POST("client/fcm-token")
    suspend fun updateFcmToken(
        @Body request: com.armonihz.app.network.model.FcmTokenRequest
    ): retrofit2.Response<com.armonihz.app.network.model.GenericResponse>

    @GET("client/profile")
    suspend fun getProfile(): Response<ProfileResponse>

    // ⬅️ Se eliminó el @Header
    @GET("client/profile")
    suspend fun getClientProfile(): Response<ClientProfileResponse>

    // ⬅️ Se eliminó el @Header
    @POST("client/sync-google-photo")
    suspend fun syncGooglePhoto(
        @Body request: SyncGooglePhotoRequest
    ): Response<GenericResponse>

    @DELETE("client/account")
    suspend fun deleteAccount(): Response<Unit>

    // ⬅️ Se eliminó el @Header
    @POST("client/sync")
    suspend fun syncClient(
        @Body data: Map<String, String>
    ): Response<Unit>

    // ⬅️ Se eliminó el @Header
    @POST("client/events")
    suspend fun createEvent(
        @Body request: EventRequest
    ): Response<GenericResponse>

    // 1. Obtener los eventos del cliente
    @GET("client/events")
    suspend fun getMyEvents(): Response<List<EventResponse>>

    // 2. Obtener las propuestas de un evento específico (⬅️ Se eliminó el @Header)
    @GET("client/events/{id}/applications")
    suspend fun getEventApplications(
        @Path("id") eventId: Int
    ): Response<EventApplicationsResponse>

    // 3. Aceptar una propuesta (⬅️ Se eliminó el @Header)
    @POST("client/events/{eventId}/applications/{appId}/accept")
    suspend fun acceptApplication(
        @Path("eventId") eventId: Int,
        @Path("appId") appId: Int
    ): Response<AcceptResponse>

    @POST("client/events/{eventId}/applications/{appId}/cancel")
    suspend fun cancelApplication(
        @Path("eventId") eventId: Int,
        @Path("appId") appId: Int
    ): Response<GenericResponse>

    // 4. ACTUALIZAR UN EVENTO (⬅️ Se eliminó el @Header)
    @PUT("client/events/{id}")
    suspend fun updateEvent(
        @Path("id") eventId: Int,
        @Body request: EventRequest
    ): Response<GenericResponse>

    @DELETE("client/events/{id}")
    suspend fun deleteEvent(
        @Path("id") id: Int
    ): Response<Unit>

    // Obtener el perfil de un músico específico
    @GET("musicians/{id}")
    suspend fun getMusicianProfile(
        @Path("id") musicianId: Int
    ): Response<MusicianProfileWrapperResponse>

    @GET("musicians")
    suspend fun getAllMusicians(): retrofit2.Response<com.google.gson.JsonObject>

    @GET("genres")
    suspend fun getGenres(): Response<List<Genre>>

    // ⬅️ NUEVAS RUTAS PARA FAVORITOS

    // Agregar a favoritos
    @POST("client/favorites/{id}")
    suspend fun addFavorite(
        @Path("id") musicianId: Int
    ): Response<GenericResponse>

    // Quitar de favoritos
    @DELETE("client/favorites/{id}")
    suspend fun removeFavorite(
        @Path("id") musicianId: Int
    ): Response<GenericResponse>
    @GET("client/favorites")
    suspend fun getMyFavorites(): retrofit2.Response<FavoriteMusiciansResponse>
}