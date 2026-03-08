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
    val name: String,
    val email: String,
    val photoUrl: String?
)

data class DeletePhotoResponse(
    val message: String
)

data class ProfileResponse(
    val photoUrl: String?
)

data class SyncGooglePhotoRequest(
    val photoUrl: String
)

data class EventRequest(
    val titulo: String,
    val tipoMusica: String,
    val fecha: String,
    val duracion: String,
    val ubicacion: String,
    val descripcion: String,
    val presupuesto: Double
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
    val presupuesto: Double
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
    val genres: List<GenreResponse>? // Por si quieres mostrar los géneros después
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