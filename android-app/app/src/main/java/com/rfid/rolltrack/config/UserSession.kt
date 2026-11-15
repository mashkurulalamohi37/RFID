package com.rfid.rolltrack.config

import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val userId: String = "",
    val userName: String = "",
    val fullName: String = "",
    val roles: List<String> = emptyList()
) {
    val isLoggedIn: Boolean get() = userId.isNotBlank()
}
