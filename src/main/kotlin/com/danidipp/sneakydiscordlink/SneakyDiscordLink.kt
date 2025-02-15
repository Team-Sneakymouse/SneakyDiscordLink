package com.danidipp.sneakydiscordlink

import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class SneakyDiscordLink : JavaPlugin() {
    lateinit var pbHandler: PocketbaseHandler
    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onLoad() {
        instance = this

        saveDefaultConfig()
        val pbProtocol = config.getString("pocketbase.protocol", "http")!!
        val pbHost = config.getString("pocketbase.host")
        val pbUser = config.getString("pocketbase.user")
        val pbPassword = config.getString("pocketbase.password")

        if (pbHost.isNullOrEmpty() || pbUser.isNullOrEmpty() || pbPassword.isNullOrEmpty()) {
            logger.severe("Missing Pocketbase configuration")
            server.pluginManager.disablePlugin(this)
            return
        }
        pbHandler = PocketbaseHandler(logger, pbProtocol, pbHost, pbUser, pbPassword)
    }

    override fun onEnable() {
//        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
//            pbHandler.runRealtime()
//        })
        Bukkit.getServer().commandMap.registerAll(IDENTIFIER, listOf(
            LinkCommand()
        ))
    }

    override fun onDisable() {
        logger.warning("Disabling SneakyDiscordLink")
        coroutineScope.cancel()

        logger.warning("Shutting down Pocketbase")
        runBlocking {
            logger.warning("Disconnecting from Pocketbase Realtime")
            pbHandler.pocketbase.realtime.disconnect()
        }
    }

    companion object {
        const val IDENTIFIER = "sneakydiscordlink"
        const val AUTHORS = "Team Sneakymouse"
        const val VERSION = "1.0"
        private lateinit var instance: SneakyDiscordLink

        fun getInstance(): SneakyDiscordLink {
            return instance
        }
        fun pb(): PocketbaseClient {
            return instance.pbHandler.pocketbase
        }
    }
}
