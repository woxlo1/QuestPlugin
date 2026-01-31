package com.woxloi.questplugin.commands.subcommands

import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.features.DailyQuestManager
import org.bukkit.entity.Player

/**
 * デイリークエストコマンド
 */
class DailyWeeklyQuestCommand : org.bukkit.command.CommandExecutor {

    override fun onCommand(
        sender: org.bukkit.command.CommandSender,
        command: org.bukkit.command.Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage(QuestPlugin.prefix + "§c§lプレイヤーのみ実行可能です")
            return true
        }

        if (args.isEmpty() || args[0].lowercase() == "daily") {
            DailyQuestManager.showDailyProgress(sender)
            return true
        }

        if (args[0].lowercase() == "weekly") {
            showWeeklyProgress(sender)
            return true
        }

        return true
    }

    private fun showWeeklyProgress(player: Player) {
        val quests = DailyQuestManager.getCurrentWeeklyQuests()

        player.sendMessage("§6§l========== ウィークリークエスト ==========")

        if (quests.isEmpty()) {
            player.sendMessage("§7現在利用可能なウィークリークエストはありません")
            return
        }

        quests.forEach { quest ->
            player.sendMessage("§e● §f${quest.name}")
            player.sendMessage("  §7${quest.type.displayName}: ${quest.target} x${quest.amount}")
        }

        player.sendMessage("§6§l=========================================")
    }
}