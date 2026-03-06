package com.armonihz.app.network.model

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
    val propuestas: Int
)