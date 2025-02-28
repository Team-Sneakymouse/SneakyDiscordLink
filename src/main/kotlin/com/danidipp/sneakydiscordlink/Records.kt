@file:Suppress("PROVIDED_RUNTIME_TOO_LOW")

package com.danidipp.sneakydiscordlink

import com.danidipp.sneakypocketbase.BaseRecord
import kotlinx.serialization.Serializable

@Serializable
data class UserRecord(
    val discord_id: String,
    val name: String) : BaseRecord()

@Serializable
data class AccountRecord(
    var name: String,
    val uuid: String,
    var owner: String,
    var main: Boolean) : BaseRecord()

@Serializable
data class LinkRecord(
    var user: String,
    var account: String,
    val link_token: String) : BaseRecord()