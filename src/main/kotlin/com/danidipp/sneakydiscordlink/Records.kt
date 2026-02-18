@file:Suppress("PROVIDED_RUNTIME_TOO_LOW")

package com.danidipp.sneakydiscordlink

import com.danidipp.sneakypocketbase.BaseRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class UserRecord(
    val discord_id: String,
    val name: String) : BaseRecord()

@Serializable
data class AccountRecord(
    @Transient override val recordId: String? = null,
    var name: String,
    var owner: String,
    var main: Boolean
) : BaseRecord(recordId)

@Serializable
data class LinkRecord(
    var user: String,
    var account: String,
    val link_token: String) : BaseRecord()