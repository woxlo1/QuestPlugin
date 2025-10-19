package com.woxloi.questplugin.commands.subcommands

import net.kyori.adventure.text.Component
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import com.woxloi.questplugin.QuestConfigManager
import com.woxloi.questplugin.QuestPlugin

class QuestSaveConfigCommand(private val plugin: JavaPlugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        QuestConfigManager.saveAllQuests()
        sender.sendMessage(QuestPlugin.prefix + "§a§lクエスト設定を保存しました。")
        return true
    }
}
