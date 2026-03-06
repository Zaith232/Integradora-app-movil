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