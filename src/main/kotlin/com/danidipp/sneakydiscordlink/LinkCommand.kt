package com.danidipp.sneakydiscordlink

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class LinkCommand : Command("link") {
    init {
        description = "Link your Discord account"
        usageMessage = "/link [code]"
//        permission = "sneakydiscordlink.link"
    }

    override fun execute(sender: CommandSender, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("You must be a player to use this command")
            return true
        }
        if (args.isEmpty()) {
            // create new code for discord side
            LinkManager.createToken(sender.uniqueId) { status, result ->
                if (status == LinkManager.Status.OK) {
                    sender.sendMessage(Component.text("In discord, please run ").append(
                        Component.text("/link $result")
                            .color(NamedTextColor.AQUA)
                            .decorate(TextDecoration.UNDERLINED)
                            .hoverEvent(Component.text("Click to copy", NamedTextColor.GRAY))
                            .clickEvent(ClickEvent.copyToClipboard("/link $result"))
                    ))
                } else {
                    sender.sendMessage(Component.text(result, NamedTextColor.RED))
                }
            }
        } else {
            // link discord account
            val token = args[0]
            LinkManager.useToken(token, sender.uniqueId) { status, result ->
                if (status == LinkManager.Status.OK) {
                    sender.sendMessage("Successfully linked your account")
                } else {
                    sender.sendMessage(Component.text(result, NamedTextColor.RED))
                }
            }
        }
        return true
    }
}