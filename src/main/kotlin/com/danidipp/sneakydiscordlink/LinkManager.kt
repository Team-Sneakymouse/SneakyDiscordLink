package com.danidipp.sneakydiscordlink

import com.danidipp.sneakypocketbase.PBRunnable
import com.danidipp.sneakypocketbase.SneakyPocketbase
import io.github.agrevster.pocketbaseKotlin.dsl.query.Filter
import io.github.agrevster.pocketbaseKotlin.dsl.query.SortFields
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import java.util.UUID

class LinkManager {
    enum class Status {
        OK,
        ERROR,
    }

    companion object {
        private val inFlight = mutableSetOf<UUID>()

        fun createToken(uuid: UUID, callback: (Status, String) -> Unit) {
            Bukkit.getScheduler().runTaskAsynchronously(SneakyDiscordLink.getInstance(), PBRunnable {
                if (inFlight.contains(uuid)) {
                    callback(Status.ERROR, "Linking is already in progress")
                    return@PBRunnable
                }
                inFlight.add(uuid)
                val callbackParams: Pair<Status, String> = run {
                    // Check if the Minecraft account is already linked
                    val accountResult = runCatching {
                        SneakyPocketbase.getInstance().pb().records.getFullList<AccountRecord>(
                            "lom2_accounts",
                            1,
                            SortFields(),
                            Filter("uuid=\"$uuid\"")
                        )
                    }.getOrNull()
                    if (accountResult == null) // PB error
                        return@run Pair(Status.ERROR, "Failed to check account status. Please tell Dani.")

                    var accountRecord = accountResult.firstOrNull()
                    if (accountRecord == null) { // Account does not exist
                        accountRecord = runCatching {
                            SneakyPocketbase.getInstance().pb().records.create<AccountRecord>(
                                "lom2_accounts",
                                Json.encodeToString(AccountRecord.serializer(), AccountRecord(
                                    name = Bukkit.getOfflinePlayer(uuid).name?: "",
                                    uuid = uuid.toString(),
                                    owner = "",
                                    main = false
                                ))
                            )
                        }.getOrNull()
                        if (accountRecord == null) // PB error
                            return@run Pair(Status.ERROR, "Failed to create account. Please tell Dani.")
                    }

                    // Check if the account is already linked
                    if (accountRecord.owner.isNotEmpty())
                        return@run Pair(Status.ERROR, "This account is already linked to a Discord account")

                    // Check if a token has already been issued
                    val findTokenResult = runCatching {
                        SneakyPocketbase.getInstance().pb().records.getFullList<LinkRecord>(
                            "minecraft_link",
                            1,
                            SortFields(),
                            Filter("account=\"${accountRecord.id}\"")
                        )
                    }.getOrNull()
                    if (findTokenResult == null) // PB error
                        return@run Pair(Status.ERROR, "Failed to check link token. Please tell Dani.")

                    val findTokenRecord = findTokenResult.firstOrNull()
                    if (findTokenRecord != null) { // Found existing link token
                        return@run Pair(Status.OK, findTokenRecord.link_token)
                    }

                    // Generate a new link token
                    var token = ""
                    for (i in 0..5) {
                        val testToken = (10000..99999).random().toString()
                        // Check for token collision
                        val checkTokenResult = runCatching {
                            SneakyPocketbase.getInstance().pb().records.getFullList<LinkRecord>(
                                "minecraft_link",
                                1,
                                SortFields(),
                                Filter("link_token=\"$testToken\"")
                            )
                        }.getOrNull()
                        if (checkTokenResult == null) // PB error
                            return@run Pair(Status.ERROR, "Failed to check link token. Please tell Dani.")

                        if (checkTokenResult.isEmpty()) { // Token is unique, use it
                            token = testToken
                            break
                        }
                    }
                    if (token.isEmpty()) // Exhausted attempts to generate a unique token
                        return@run Pair(Status.ERROR, "Failed to generate link token. Please tell Dani.")

                    // Save the new link token to the database
                    val updateResult = runCatching {
                        SneakyPocketbase.getInstance().pb().records.create<LinkRecord>(
                            "minecraft_link",
                            Json.encodeToString(LinkRecord.serializer(), LinkRecord(
                                user = "",
                                account = accountRecord.id!!,
                                link_token = token
                            ))
                        )
                    }.getOrNull()
                    if (updateResult == null) // PB error
                        return@run Pair(Status.ERROR, "Failed to save link token. Please tell Dani.")

                    // Tell the user the new link token
                    return@run Pair(Status.OK, updateResult.link_token)
                }
                callback(callbackParams.first, callbackParams.second)
                inFlight.remove(uuid)
            })
        }

        fun useToken(token: String, uuid: UUID, callback: (Status, String) -> Unit) {
            // Quick validation
            if (token.startsWith("token:"))
                return callback(Status.ERROR, "Please use the link command in Discord instead!")
            if (token.length != 5)
                return callback(Status.ERROR, "Invalid link token")

            Bukkit.getScheduler().runTaskAsynchronously(SneakyDiscordLink.getInstance(), PBRunnable {
                if (inFlight.contains(uuid)) return@PBRunnable
                inFlight.add(uuid)

                val callbackParams: Pair<Status, String> = run {
                    // Check if the Minecraft account is already linked
                    val accountResult = runCatching {
                        SneakyPocketbase.getInstance().pb().records.getFullList<AccountRecord>(
                            "lom2_accounts",
                            1,
                            SortFields(),
                            Filter("uuid=\"$uuid\"")
                        )
                    }.getOrNull()
                    if (accountResult == null) // PB error
                        return@run Pair(Status.ERROR, "Failed to check account status. Please tell Dani.")

                    var accountRecord = accountResult.firstOrNull()
                    if (accountRecord == null) { // Account does not exist
                        accountRecord = runCatching {
                            SneakyPocketbase.getInstance().pb().records.create<AccountRecord>(
                                "lom2_accounts",
                                Json.encodeToString(AccountRecord.serializer(), AccountRecord(
                                    name = Bukkit.getOfflinePlayer(uuid).name?: "",
                                    uuid = uuid.toString(),
                                    owner = "",
                                    main = false
                                ))
                            )
                        }.getOrNull()
                        if (accountRecord == null) // PB error
                            return@run Pair(Status.ERROR, "Failed to create account. Please tell Dani.")
                    }

                    // Check if the account is already linked
                    if (accountRecord.owner.isNotEmpty())
                        return@run Pair(Status.ERROR, "This account is already linked to a Discord account")

                    // Check if the link token exists in the database
                    val tokenResponse = runCatching {
                        SneakyPocketbase.getInstance().pb().records.getFullList<LinkRecord>(
                            "minecraft_link",
                            1,
                            SortFields(),
                            Filter("link_token=\"$token\"")
                        )
                    }.getOrNull()
                    if (tokenResponse == null) // PB error
                        return@run Pair(Status.ERROR, "Failed to check link token. Please tell Dani.")

                    val tokenRecord = tokenResponse.firstOrNull()
                    if (tokenRecord == null) // Token not found
                        return@run Pair(Status.ERROR, "Invalid link token")

                    if (tokenRecord.user.isEmpty()) { // Token is meant to be used in Discord
                        if (tokenRecord.account != accountRecord.id) // Token is not for this account
                            return@run Pair(Status.ERROR, "This token is meant for another user's Discord account. What are you trying to pull?")
                        else
                            return@run Pair(Status.ERROR, "Please use the link command in Discord instead!")
                    }

                    // Check if there are other accounts linked to the Discord account
                    val otherAccountResult = runCatching {
                        SneakyPocketbase.getInstance().pb().records.getFullList<AccountRecord>(
                            "lom2_accounts",
                            100,
                            SortFields(),
                            Filter("owner=\"${tokenRecord.user}\"")
                        )
                    }.getOrNull()
                    if (otherAccountResult == null) // PB error
                        return@run Pair(Status.ERROR, "Failed to check other accounts. Please tell Dani.")
                    val otherMain = otherAccountResult.any { it.main }

                    // Register the Minecraft account to the Discord account
                    // Update the account record
                    accountRecord.owner = tokenRecord.user
                    accountRecord.main = !otherMain
                    val updateResult = runCatching {
                        SneakyPocketbase.getInstance().pb().records.update<AccountRecord>(
                            "lom2_accounts",
                            accountRecord.id!!,
                            Json.encodeToString(AccountRecord.serializer(), accountRecord)
                        )
                    }.getOrNull()
                    if (updateResult == null)
                        return@run Pair(Status.ERROR, "Failed to link account. Please tell Dani.")

                    // Delete the link token
                    val deleteResult = runCatching {
                        SneakyPocketbase.getInstance().pb().records.delete(
                            "minecraft_link",
                            tokenRecord.id!!
                        )
                    }.getOrNull()
                    if (deleteResult == null)
                        return@run Pair(Status.ERROR, "Failed to clean up. Please tell Dani.")

                    return@run Pair(Status.OK, "Successfully linked account")
                }
                callback(callbackParams.first, callbackParams.second)
                inFlight.remove(uuid)
            })
        }
    }
}