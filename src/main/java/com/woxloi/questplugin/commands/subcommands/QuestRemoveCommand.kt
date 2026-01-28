package com.woxloi.questplugin.commands.subcommands

import net.kyori.adventure.text.Component
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import com.woxloi.questplugin.QuestConfigManager
import com.woxloi.questplugin.QuestPlugin

class QuestRemoveCommand(private val plugin: JavaPlugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {

        val id = args[2]

        if (!QuestConfigManager.exists(id)) {
            sender.sendMessage(QuestPlugin.prefix + "§c§lそのIDのクエストは存在しません")
            return true
        }

        QuestConfigManager.deleteQuest(id)
        sender.sendMessage(QuestPlugin.prefix + "§a§l" + id + "を削除しました")
        return true
    }
}
