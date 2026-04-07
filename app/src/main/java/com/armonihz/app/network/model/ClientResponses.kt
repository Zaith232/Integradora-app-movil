package com.armonihz.app.network.model

import java.io.Serializable

data class GenericResponse(
    val message: String
)

data class UploadPhotoResponse(
    val message: String,
    val photoUrl: String
)

data class ClientProfileResponse(
    val nombre: String?,
    val apellido: String?,
    val email: String,
    val telefono : String?,
    val photoUrl: String?
)

data class UpdateProfileRequest(
    val nombre: String,
    val apellido: String,
    val telefono: String
)

data class DeletePhotoResponse(
    val message: String
)

data class ProfileResponse(
    val nombre: String?,
    val apellido: String?,
    val email: String?,
    val telefono: String?,
    val photoUrl: String?
)

data class SyncGooglePhotoRequest(
    val photoUrl: String
)



data class EventRequest(
    val titulo: String,
    val genre_id: Int,
    val fecha: String,
    val duracion: String,
    val ubicacion: String,
    val descripcion: String,
    val presupuesto: Double,
    val email: String? = null,    // NUEVO
    val telefono: String? = null  // NUEVO
)

data class EventResponse(
    val id: Int,
    val titulo: String,
    val tipoMusica: String,
    val fecha: String,
    val ubicacion: String,
    val status: String,
    val propuestas: Int,
    // ⬅️ Nuevos campos
    val duracion: String,
    val descripcion: String?, // Puede ser nulo
    val presupuesto: Double,
    val nombre_cliente: String?
) : Serializable // ⬅️ Agregamos Serializable para enviarlo fácilmente al nuevo Fragment

data class EventApplicationsResponse(
    val event_id: Int,
    val applications: List<ApplicationItem>
)

data class ApplicationItem(
    val id: Int,
    val status: String,
    val proposed_price: String,
    val message: String?,
    val created_at: String,
    val musician: MusicianInfo
)

data class MusicianInfo(
    val id: Int,
    val stage_name: String,
    val location: String?,
    val profile_picture: String?,
    val hourly_rate: String?
)

data class AcceptResponse(
    val message: String,
    val application_id: Int
)

// 1. La envoltura principal
data class MusicianProfileWrapperResponse(
    val success: Boolean,
    val data: MusicianProfileDetailResponse,
    val message: String
)



// 2. Los datos reales del músico actualizados
data class MusicianProfileDetailResponse(
    val id: Int,
    val stage_name: String,
    val location: String?,
    val profile_picture: String?,
    val bio: String?,
    val hourly_rate: String?,


    // ⬅️ NUEVOS CAMPOS AGREGADOS SEGÚN TU MODELO LARAVEL
    val is_verified: Int?, // Laravel suele enviar 0 o 1 para los booleanos
    val phone: String?,
    val instagram: String?,
    val facebook: String?,
    val youtube: String?,
    val coverage_notes: String?,
    val genres: List<GenreResponse>?, // Por si quieres mostrar los géneros después
    val media: MediaDataResponse? = null,
    val is_favorite: Boolean? = false,
    val rating_average: Double?

)

data class MultimediaItem(
    val id: Int,
    val type: String, // Devolverá "image" o "video" desde tu API
    val file_path: String
)

data class GenreResponse(
    val id: Int,
    val name: String
)

// 1. La respuesta principal que envuelve todo
data class PaginatedMusiciansWrapper(
    val success: Boolean,
    val data: PaginatedMusiciansData, // Entramos al primer objeto "data"
    val message: String
)

// 2. El objeto "data" de la paginación que trae la lista real y los metadatos
data class PaginatedMusiciansData(
    val data: List<MusicianProfileDetailResponse>, // ⬅️ AQUÍ está la lista real de músicos
    // val links: Any? (Podrías mapear los links y metas aquí si quieres hacer scroll infinito después)
)

data class FcmTokenRequest(
    val fcm_token: String
)

data class Genre(
    val id: Int,
    val name: String
)

data class MediaDataResponse(
    val photos: List<MediaItemResponse>?,
    val videos: List<MediaItemResponse>?
)

data class MediaItemResponse(
    val id: Int,
    val url: String,
    val title: String? = null,
    val is_featured: Boolean? = null
)

// (Conserva tu clase MultimediaItem que creamos antes, la usaremos para el adaptador)

data class FavoriteMusiciansResponse(
    val success: Boolean,
    val data: List<MusicianProfileDetailResponse>
)

// HiringRequestPayload.kt
data class HiringRequestPayload(
    val musician_profile_id: Int,
    val event_date: String, // Formato "YYYY-MM-DD HH:mm:ss"
    val end_time: String,
    val event_location: String,
    val description: String,
    val budget: Double
)

// AvailabilityResponse.kt
data class AvailabilityResponse(
    val success: Boolean,
    val data: List<BusyDate>
)

data class BusyDate(
    val start: String,
    val end: String
)

data class HiringRequestsListResponse(
    val data: List<HiringRequestItem>
)

data class HiringRequestItem(
    val id: Int,
    val type: String? = "hiring",
    val event_date: String,
    val end_time: String?,
    val event_location: String,
    val description: String,
    val budget: Double,
    val status: String,
    val musician_message: String?, // Para la contraoferta
    val counter_offer: Double?,    // Para el nuevo precio
    val musician_profile: MusicianMiniProfile?,
    val has_review: Boolean? = false
)

data class MusicianMiniProfile(
    val id: Int,
    val stage_name: String
    // Si tu API devuelve foto, puedes agregarla aquí: val photo: String?
)

data class ReviewRequest(
    val musician_profile_id: Int,
    val rating: Int,
    val comment: String?, // Opcional
    val hiring_request_id: Int? = null,        // 🔥 Cambiar a nullable
    val casting_application_id: Int? = null
)
// 1. El Wrapper (Mantiene tu estándar actual)
data class MusicianReviewsResponse(
    val success: Boolean,
    val data: List<ReviewItem>
)

// 2. El Item Principal (NUEVO, porque no existía nada igual)
data class ReviewItem(
    val id: Int,
    val rating: Int,
    val comment: String?,
    val response: String?,
    val created_at: String,
    val client: ClientMiniProfile? // Usamos un mini perfil
)

// 3. El Mini Perfil (NUEVO, por seguridad de los datos del cliente)
data class ClientMiniProfile(
    val id: Int,
    val nombre: String,
    val apellido: String,
    val photoUrl: String? // ✅ Ahora sí coinciden
)

data class MyReviewsResponse(
    val success: Boolean,
    val data: List<MyReviewItem>
)

data class MyReviewItem(
    val id: Int,
    val rating: Int,
    val comment: String?,
    val response: String?,
    val created_at: String,
    val musician: MusicianReviewInfo
)

data class MusicianReviewInfo(
    val id: Int,
    val stage_name: String,
    val profile_picture: String?
)

// Dentro de tu paquete de modelos o data classes
data class ReportRequest(
    val reason: String
)

