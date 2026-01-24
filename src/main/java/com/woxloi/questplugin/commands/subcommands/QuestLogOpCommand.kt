package com.woxloi.questplugin.commands.subcommands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import com.woxloi.questplugin.QuestPlugin
import java.io.File

class QuestLogOpCommand(private val plugin: QuestPlugin) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val targetName = args[1]
        val targetPlayer = Bukkit.getOfflinePlayer(targetName)
        if (targetPlayer == null || (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline)) {
            sender.sendMessage(QuestPlugin.prefix + "§c§lプレイヤー" + targetName + "は存在しません。")
            return true
        }

        val page = if (args.size >= 3) args[2].toIntOrNull() ?: 1 else 1
        if (page <= 0) {
            sender.sendMessage(QuestPlugin.prefix + "§c§lページ番号は1以上を指定してください。")
            return true
        }

        val uuid = targetPlayer.uniqueId
        val historyFile = File(plugin.dataFolder, "quest_histories.yml")
        if (!historyFile.exists()) {
            sender.sendMessage(QuestPlugin.prefix + "§e§lクエスト履歴はありません。")
            return true
        }

        val historyConfig = YamlConfiguration.loadConfiguration(historyFile)
        val historiesSection = historyConfig.getConfigurationSection("histories")
        if (historiesSection == null) {
            sender.sendMessage(QuestPlugin.prefix + "§e§lクエスト履歴はありません。")
            return true
        }

        val uuidStr = uuid.toString()
        val questLogsRaw = historiesSection.getMapList(uuidStr)
        if (questLogsRaw.isNullOrEmpty()) {
            sender.sendMessage(QuestPlugin.prefix + "§e§l" + targetName + "のクエスト履歴はありません。")
            return true
        }

        val logs = questLogsRaw.map { entry ->
            val questName = entry["questName"] ?: "不明なクエスト"
            val success = entry["success"] as? Boolean ?: false
            val completedAt = (entry["completedAt"] as? Number)?.toLong() ?: 0L
            val progress = entry["progress"] ?: 0
            val deathCount = entry["deathCount"] ?: 0
            val date = java.time.Instant.ofEpochMilli(completedAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
                .toString()

            val status = if (success) "成功" else "失敗"
            "[$date] $questName ($status) 進捗:$progress 死亡:$deathCount"
        }

        val pageSize = 10
        val maxPage = (logs.size + pageSize - 1) / pageSize
        if (page > maxPage) {
            sender.sendMessage(QuestPlugin.prefix + "§c§lそのようなページ番号は存在しません。")
            return true
        }

        sender.sendMessage("§a§l===== $targetName のクエスト履歴 (ページ $page / $maxPage) =====")

        val startIndex = (page - 1) * pageSize
        val endIndex = (startIndex + pageSize).coerceAtMost(logs.size)

        for (i in startIndex until endIndex) {
            sender.sendMessage("§e§l・ ${logs[i]}")
        }

        if (sender is org.bukkit.entity.Player) {
            val components = mutableListOf<Component>()

            if (page > 1) {
                val prevPageCmd = "/quest logop $targetName ${page - 1}"
                val prev = Component.text("§b§l<<< 前のページへ")
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(prevPageCmd))
                components.add(prev)
            }

            if (page < maxPage) {
                val nextPageCmd = "/quest logop $targetName ${page + 1}"
                val next = Component.text("§b§l次のページへ >>>")
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(nextPageCmd))
                components.add(next)
            }

            // スペースで区切って表示
            val navigation = Component.join(Component.text("     "), components)
            sender.sendMessage(navigation)
        } else {
            if (page > 1) {
                sender.sendMessage("§7§l前のページを見るには /quest logop $targetName ${page - 1} を入力してください。")
            }
            if (page < maxPage) {
                sender.sendMessage("§7§l次のページを見るには /quest logop $targetName ${page + 1} を入力してください。")
            }
        }

        return true
    }
}