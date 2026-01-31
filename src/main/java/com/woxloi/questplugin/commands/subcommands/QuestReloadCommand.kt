package com.woxloi.questplugin.commands.subcommands

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import com.woxloi.questplugin.manager.ActiveQuestManager
import com.woxloi.questplugin.manager.QuestConfigManager
import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.features.DailyQuestManager
import com.woxloi.questplugin.features.QuestChainManager

class QuestReloadCommand(private val plugin: JavaPlugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        QuestConfigManager.loadAllQuests()

        ActiveQuestManager.init()

        DailyQuestManager.init()

        QuestChainManager.init()

        for (player in Bukkit.getOnlinePlayers()) {
            com.woxloi.questplugin.manager.ActiveQuestManager.cancelQuest(player)
        }
        plugin.reloadConfig()

        sender.sendMessage(QuestPlugin.prefix + "§a§lクエスト設定を再読み込みしました")
        sender.sendMessage(QuestPlugin.prefix + "§a§lすべてのプレイヤーのクエストが停止されました")
        return true
    }
}
