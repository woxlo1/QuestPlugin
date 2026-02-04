package com.woxloi.questplugin.commands.subcommands

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.database.DatabaseManager
import java.io.File

class QuestLogOpCommand(private val plugin: QuestPlugin) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage(QuestPlugin.prefix + "§c§l使用方法: /quest logop <プレイヤー名> [ページ]")
            return true
        }

        val targetName = args[1]
        val targetPlayer = Bukkit.getOfflinePlayer(targetName)

        if (targetPlayer == null || (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline)) {
            sender.sendMessage(QuestPlugin.prefix + "§c§l${targetName}は存在しません")
            return true
        }

        val page = if (args.size >= 3) args[2].toIntOrNull() ?: 1 else 1
        if (page <= 0) {
            sender.sendMessage(QuestPlugin.prefix + "§c§lページ番号は1以上を指定してください")
            return true
        }

        val uuid = targetPlayer.uniqueId

        // データベースが有効な場合はデータベースから取得
        if (DatabaseManager.isEnabled()) {
            loadHistoryFromDatabase(sender, targetName, uuid, page)
        } else {
            loadHistoryFromYaml(sender, targetName, uuid, page)
        }

        return true
    }

    /**
     * データベースから履歴を取得（非同期）
     */
    private fun loadHistoryFromDatabase(sender: CommandSender, targetName: String, uuid: java.util.UUID, page: Int) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val pageSize = 10
                val histories = DatabaseManager.loadHistory(uuid, page, pageSize)
                val totalCount = DatabaseManager.getHistoryCount(uuid)
                val maxPage = (totalCount + pageSize - 1) / pageSize

                if (totalCount == 0) {
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        sender.sendMessage(QuestPlugin.prefix + "§e§l${targetName}のクエスト履歴はありません")
                    })
                    return@Runnable
                }

                if (page > maxPage) {
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        sender.sendMessage(QuestPlugin.prefix + "§c§lそのようなページ番号は存在しません（最大: $maxPage）")
                    })
                    return@Runnable
                }

                // メインスレッドで表示
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    sender.sendMessage("§a§l===== ${targetName}のクエスト履歴 (ページ $page / $maxPage) =====")

                    for (history in histories) {
                        val status = if (history.success) "§a成功" else "§c失敗"
                        val date = java.time.Instant.ofEpochMilli(history.completedAt)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

                        sender.sendMessage("§e§l・ [$date] ${history.questName} ($status) 進捗:${history.progress} 死亡:${history.deathCount}")
                    }

                    displayNavigation(sender, targetName, page, maxPage)
                })
            } catch (e: Exception) {
                plugin.logger.warning("履歴取得エラー: ${e.message}")
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    sender.sendMessage(QuestPlugin.prefix + "§c§l履歴の取得に失敗しました")
                })
            }
        })
    }

    /**
     * YAMLファイルから履歴を取得
     */
    private fun loadHistoryFromYaml(sender: CommandSender, targetName: String, uuid: java.util.UUID, page: Int) {
        val historyFile = File(plugin.dataFolder, "quest_histories.yml")

        if (!historyFile.exists()) {
            sender.sendMessage(QuestPlugin.prefix + "§e§lクエスト履歴はありません")
            return
        }

        val historyConfig = YamlConfiguration.loadConfiguration(historyFile)
        val historiesSection = historyConfig.getConfigurationSection("histories")

        if (historiesSection == null) {
            sender.sendMessage(QuestPlugin.prefix + "§e§lクエスト履歴はありません")
            return
        }

        val uuidStr = uuid.toString()
        val questLogsRaw = historiesSection.getMapList(uuidStr)

        if (questLogsRaw.isNullOrEmpty()) {
            sender.sendMessage(QuestPlugin.prefix + "§e§l${targetName}のクエスト履歴はありません")
            return
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
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            val status = if (success) "§a成功" else "§c失敗"
            "[$date] $questName ($status) 進捗:$progress 死亡:$deathCount"
        }

        val pageSize = 10
        val maxPage = (logs.size + pageSize - 1) / pageSize

        if (page > maxPage) {
            sender.sendMessage(QuestPlugin.prefix + "§c§lそのようなページ番号は存在しません（最大: $maxPage）")
            return
        }

        sender.sendMessage("§a§l===== ${targetName}のクエスト履歴 (ページ $page / $maxPage) =====")

        val startIndex = (page - 1) * pageSize
        val endIndex = (startIndex + pageSize).coerceAtMost(logs.size)

        for (i in startIndex until endIndex) {
            sender.sendMessage("§e§l・ ${logs[i]}")
        }

        displayNavigation(sender, targetName, page, maxPage)
    }

    /**
     * ページネーション表示
     */
    private fun displayNavigation(sender: CommandSender, targetName: String, page: Int, maxPage: Int) {
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

            if (components.isNotEmpty()) {
                val navigation = Component.join(Component.text("     "), components)
                sender.sendMessage(navigation)
            }
        } else {
            if (page > 1) {
                sender.sendMessage("§7§l前のページを見るには /quest logop $targetName ${page - 1} を入力してください")
            }
            if (page < maxPage) {
                sender.sendMessage("§7§l次のページを見るには /quest logop $targetName ${page + 1} を入力してください")
            }
        }
    }
}