package com.danidipp.sneakydiscordlink

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
            Bukkit.getScheduler().runTaskAsynchronously(SneakyDiscordLink.getInstance(), Runnable {
                if (inFlight.contains(uuid)) {
                    callback(Status.ERROR, "Linking is already in progress")
                    return@Runnable
                }
                inFlight.add(uuid)
                val callbackParams: Pair<Status, String> = run {
                    // Check if the Minecraft account is already linked
                    var accountRecord = runCatching {
                        PocketbaseRecords.getOne<AccountRecord>("lom2_accounts",uuid.toString())
                    }.getOrNull()
                    if (accountRecord == null) { // Account does not exist
                        accountRecord = runCatching {
                            PocketbaseRecords.create<AccountRecord>(
                                "lom2_accounts",
                                PocketbaseRecords.encodeCreate(AccountRecord(
                                    id = uuid.toString(),
                                    name = Bukkit.getOfflinePlayer(uuid).name?: "",
                                    owner = "",
                                    main = false
                                ))
                            )
                        }.onFailure { it.printStackTrace() }.getOrNull()
                        if (accountRecord == null) // PB error
                            return@run Pair(Status.ERROR, "Failed to create account. Please tell Dani.")
                    }

                    // Check if the account is already linked
                    if (accountRecord.owner.isNotEmpty())
                        return@run Pair(Status.ERROR, "This account is already linked to a Discord account")

                    // Check if a token has already been issued
                    val findTokenResult = runCatching {
                        PocketbaseRecords.getFullList<LinkRecord>(
                            "minecraft_link",
                            1,
                            "account=\"${accountRecord.id}\""
                        )
                    }.onFailure { it.printStackTrace() }.getOrNull()
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
                            PocketbaseRecords.getFullList<LinkRecord>(
                                "minecraft_link",
                                1,
                                "link_token=\"$testToken\""
                            )
                        }.onFailure { it.printStackTrace() }.getOrNull()
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
                        PocketbaseRecords.create<LinkRecord>(
                            "minecraft_link",
                            PocketbaseRecords.encodeCreate(LinkRecord(
                                user = "",
                                account = accountRecord.id!!,
                                link_token = token
                            ))
                        )
                    }.onFailure { it.printStackTrace() }.getOrNull()
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

            Bukkit.getScheduler().runTaskAsynchronously(SneakyDiscordLink.getInstance(), Runnable {
                if (inFlight.contains(uuid)) return@Runnable
                inFlight.add(uuid)

                val callbackParams: Pair<Status, String> = run {
                    // Check if the Minecraft account is already linked
                    var accountRecord = runCatching {
                        PocketbaseRecords.getOne<AccountRecord>("lom2_accounts", uuid.toString())
                    }.onFailure { it.printStackTrace() }.getOrNull()
                    if (accountRecord == null) { // Account does not exist
                        accountRecord = runCatching {
                            PocketbaseRecords.create<AccountRecord>(
                                "lom2_accounts",
                                PocketbaseRecords.encodeCreate(AccountRecord(
                                    id = uuid.toString(),
                                    name = Bukkit.getOfflinePlayer(uuid).name?: "",
                                    owner = "",
                                    main = false
                                ))
                            )
                        }.onFailure { it.printStackTrace() }.getOrNull()
                        if (accountRecord == null) // PB error
                            return@run Pair(Status.ERROR, "Failed to create account. Please tell Dani.")
                    }

                    // Check if the account is already linked
                    if (accountRecord.owner.isNotEmpty())
                        return@run Pair(Status.ERROR, "This account is already linked to a Discord account")

                    // Check if the link token exists in the database
                    val tokenResponse = runCatching {
                        PocketbaseRecords.getFullList<LinkRecord>(
                            "minecraft_link",
                            1,
                            "link_token=\"$token\""
                        )
                    }.onFailure { it.printStackTrace() }.getOrNull()
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
                        PocketbaseRecords.getFullList<AccountRecord>(
                            "lom2_accounts",
                            100,
                            "owner=\"${tokenRecord.user}\""
                        )
                    }.onFailure { it.printStackTrace() }.getOrNull()
                    if (otherAccountResult == null) // PB error
                        return@run Pair(Status.ERROR, "Failed to check other accounts. Please tell Dani.")
                    val otherMain = otherAccountResult.any { it.main }

                    // Register the Minecraft account to the Discord account
                    // Update the account record
                    accountRecord.owner = tokenRecord.user
                    accountRecord.main = !otherMain
                    val updateResult = runCatching {
                        PocketbaseRecords.update<AccountRecord>(
                            "lom2_accounts",
                            accountRecord.id!!,
                            PocketbaseRecords.encodeUpdate(accountRecord)
                        )
                    }.onFailure { it.printStackTrace() }.getOrNull()
                    if (updateResult == null)
                        return@run Pair(Status.ERROR, "Failed to link account. Please tell Dani.")

                    // Delete the link token
                    val deleteResult = runCatching {
                        PocketbaseRecords.delete(
                            "minecraft_link",
                            tokenRecord.id!!
                        )
                    }.onFailure { it.printStackTrace() }.getOrNull()
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
