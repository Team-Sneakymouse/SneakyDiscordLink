@file:Suppress("PROVIDED_RUNTIME_TOO_LOW")

package com.danidipp.sneakydiscordlink

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserRecord(
    @SerialName("id") val id: String? = null,
    val discord_id: String,
    val name: String,
)

@Serializable
data class AccountRecord(
    @SerialName("id") val id: String? = null,
    var name: String,
    var owner: String,
    var main: Boolean,
)

@Serializable
data class LinkRecord(
    @SerialName("id") val id: String? = null,
    var user: String,
    var account: String,
    val link_token: String,
)
