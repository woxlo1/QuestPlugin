package com.woxloi.questplugin.commands.subcommands

import net.kyori.adventure.text.Component
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import com.woxloi.questplugin.manager.ActiveQuestManager
import com.woxloi.questplugin.QuestPlugin

class QuestLeaveCommand(plugin: QuestPlugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(QuestPlugin.prefix + "§c§lこのコマンドはプレイヤーのみ実行可能です")
            return true
        }
        val player = sender

        if (!com.woxloi.questplugin.manager.ActiveQuestManager.isQuesting(player)) {
            player.sendMessage(QuestPlugin.prefix + "§c§l現在進行中のクエストはありません")
            return true
        }

        com.woxloi.questplugin.manager.ActiveQuestManager.cancelQuest(player)
        player.sendMessage(QuestPlugin.prefix + "§a§lクエストを中断しました")

        return true
    }
}
