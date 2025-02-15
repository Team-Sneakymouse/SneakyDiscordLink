package com.danidipp.sneakydiscordlink

import io.github.agrevster.pocketbaseKotlin.dsl.query.Filter
import io.github.agrevster.pocketbaseKotlin.dsl.query.SortFields
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

class LinkManager {
    enum class Status {
        OK,
        ERROR,
    }

    companion object {
        private val inFlight = mutableSetOf<UUID>()

        fun createToken(uuid: UUID, callback: (Status, String) -> Unit) {
            if (inFlight.contains(uuid)) return
            inFlight.add(uuid)

            CoroutineScope(SneakyDiscordLink.getInstance().coroutineScope.coroutineContext).launch {
                val checkUuidResult = runCatching {
                    SneakyDiscordLink.pb().records.getFullList<PocketbaseHandler.LinkRecord>(
                        "minecraft_link",
                        1,
                        SortFields(),
                        Filter("minecraft_id=\"$uuid\"")
                    )
                }.getOrNull()
                if (checkUuidResult == null) {
                    callback(Status.ERROR, "Failed to check link token. Please tell Dani.")
                    inFlight.remove(uuid)
                    return@launch
                }
                val checkUuidRecord = checkUuidResult.firstOrNull()
                if (checkUuidRecord != null) {
                    if(checkUuidRecord.discord_id.isNotEmpty()) {
                        callback(Status.ERROR, "This Minecraft account is already linked to a Discord account")
                        return@launch
                    }
                    callback(Status.OK, checkUuidRecord.link_token)
                    inFlight.remove(uuid)
                    return@launch
                }

                var token = ""
                for (i in 0..5) {
                    val testToken = (10000..99999).random().toString()
                    val checkTokenResult = runCatching {
                        SneakyDiscordLink.pb().records.getFullList<PocketbaseHandler.LinkRecord>(
                            "minecraft_link",
                            1,
                            SortFields(),
                            Filter("link_token=\"$testToken\"")
                        )
                    }.getOrNull()
                    if (checkTokenResult == null) {
                        callback(Status.ERROR, "Failed to check link token. Please tell Dani.")
                        inFlight.remove(uuid)
                        return@launch
                    }
                    if (checkTokenResult.isEmpty()) {
                        token = testToken
                        break
                    }
                }
                if (token.isEmpty()) {
                    callback(Status.ERROR, "Failed to generate link token. Please tell Dani.")
                    inFlight.remove(uuid)
                    return@launch
                }

                val updateResult = runCatching {
                    SneakyDiscordLink.pb().records.create<PocketbaseHandler.LinkRecord>(
                    "minecraft_link",
                        PocketbaseHandler.LinkRecord(
                            discord_id = "",
                            minecraft_id = uuid.toString(),
                            link_token = token
                        ).toJson()
                    )
                }.getOrNull()
                if (updateResult == null) {
                    callback(Status.ERROR, "Failed to save link token. Please tell Dani.")
                    inFlight.remove(uuid)
                    return@launch
                }

                callback(Status.OK, updateResult.link_token)
                inFlight.remove(uuid)
            }
        }

        fun useToken(code: String, uuid: UUID, callback: (Status, String) -> Unit) {
            if (inFlight.contains(uuid)) return
            inFlight.add(uuid)
            CoroutineScope(SneakyDiscordLink.getInstance().coroutineScope.coroutineContext).launch {
                val tokenRecord = runCatching {
                    SneakyDiscordLink.pb().records.getFullList<PocketbaseHandler.LinkRecord>(
                    "minecraft_link",
                    1,
                    SortFields(),
                    Filter("link_token=\"$code\""))
                }.getOrNull()
                if (tokenRecord == null) {
                    callback(Status.ERROR, "Failed to check link token. Please tell Dani.")
                    inFlight.remove(uuid)
                    return@launch
                }

                val record = tokenRecord.firstOrNull()
                if (record == null) {
                    callback(Status.ERROR, "Invalid link token")
                    inFlight.remove(uuid)
                    return@launch
                }

                record.minecraft_id = uuid.toString()
                record.link_token = ""
                val updateResult = runCatching {
                    SneakyDiscordLink.pb().records.update<PocketbaseHandler.LinkRecord>(
                        "minecraft_link",
                        record.id!!,
                        record.toJson()
                    )
                }.getOrNull()
                if (updateResult == null) {
                    callback(Status.ERROR, "Failed to update link token. Please tell Dani.")
                    inFlight.remove(uuid)
                    return@launch
                }

                callback(Status.OK, "")
                inFlight.remove(uuid)
            }
        }
    }
}