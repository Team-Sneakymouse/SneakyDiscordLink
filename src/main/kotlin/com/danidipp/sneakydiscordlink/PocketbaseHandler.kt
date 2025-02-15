package com.danidipp.sneakydiscordlink

import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import io.github.agrevster.pocketbaseKotlin.dsl.login
import io.github.agrevster.pocketbaseKotlin.models.Record
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import java.util.logging.Logger

class PocketbaseHandler {
    val pocketbase: PocketbaseClient
    val logger: Logger
    private lateinit var authWait: Deferred<Unit>

    @OptIn(DelicateCoroutinesApi::class)
    constructor(logger: Logger,
                pbProtocol: String,
                pbHost: String,
                pbUser: String,
                pbPassword: String) {
        this.logger = logger
        pocketbase = PocketbaseClient({
            this.protocol = URLProtocol.byName[pbProtocol]!!
            this.host = pbHost
        })
        authWait = GlobalScope.async {
            val token = kotlin.runCatching { pocketbase.admins.authWithPassword(pbUser, pbPassword).token }
            if (token.isFailure) {
                logger.severe("Failed to authenticate with Pocketbase")
                Bukkit.getServer().pluginManager.disablePlugin(SneakyDiscordLink.getInstance())
            }
            pocketbase.login { this.token = token.getOrNull() }
        }
    }

    @Serializable
    data class LinkRecord(var discord_id: String, var minecraft_id: String, var link_token: String): Record() {
        fun toJson(): String {
            return Json.encodeToString(kotlinx.serialization.serializer(), this)
        }
    }
}