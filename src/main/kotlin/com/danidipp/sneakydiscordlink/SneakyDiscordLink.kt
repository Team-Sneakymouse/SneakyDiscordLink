package com.danidipp.sneakydiscordlink

import io.github.agrevster.pocketbaseKotlin.PocketbaseClient
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class SneakyDiscordLink : JavaPlugin() {
    override fun onLoad() {
        instance = this
    }

    override fun onEnable() {
        Bukkit.getServer().commandMap.registerAll(IDENTIFIER, listOf(
            LinkCommand()
        ))
//        Bukkit.getServer().pluginManager.registerEvents(LinkManager.listener, this)
    }

    override fun onDisable() {
    }

    companion object {
        const val IDENTIFIER = "sneakydiscordlink"
        const val AUTHORS = "Team Sneakymouse"
        const val VERSION = "1.0"
        private lateinit var instance: SneakyDiscordLink

        fun getInstance(): SneakyDiscordLink {
            return instance
        }
    }
}
