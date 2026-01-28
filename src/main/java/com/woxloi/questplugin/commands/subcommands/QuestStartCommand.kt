package com.woxloi.questplugin.commands.subcommands

import net.kyori.adventure.text.Component
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import com.woxloi.questplugin.ActiveQuestManager
import com.woxloi.questplugin.QuestConfigManager
import com.woxloi.questplugin.QuestPlugin

class QuestStartCommand(private val plugin: JavaPlugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§c§lプレイヤーのみ実行可能です")
            return true
        }
        if (args.isEmpty()) {
            sender.sendMessage(QuestPlugin.prefix + "§c§lクエストIDを指定してください")
            return true
        }
        val id = args[1]
        val quest = QuestConfigManager.getQuest(id)
        if (quest == null) {
            sender.sendMessage(QuestPlugin.prefix + "§c§lクエストが見つかりませんでした")
            return true
        }
        val success = com.woxloi.questplugin.ActiveQuestManager.startQuest(sender, quest)
        if (!success) {
            sender.sendMessage(QuestPlugin.prefix + "§c§lクエストをスタートできませんでした")
            return true
        }
        sender.sendMessage(QuestPlugin.prefix + "§a§lクエスト${quest.name}を開始しました！")
        return true
    }
}
