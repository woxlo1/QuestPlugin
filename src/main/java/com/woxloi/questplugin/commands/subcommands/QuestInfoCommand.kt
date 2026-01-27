package com.woxloi.questplugin.commands.subcommands

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent.suggestCommand
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import com.woxloi.questplugin.ActiveQuestManager
import com.woxloi.questplugin.QuestConfigManager
import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.party.PartyManager
import net.kyori.adventure.text.event.ClickEvent.runCommand

class QuestInfoCommand(private val plugin: JavaPlugin) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        val questId = args[1]
        val quest = QuestConfigManager.getQuest(questId)

        if (quest == null) {
            sender.sendMessage(QuestPlugin.prefix + "§c§lクエスト${questId}が見つかりません ")
            return true
        }

        sender.sendMessage("§7§l=== クエスト情報 [$questId] ===")
        sender.sendMessage("§e§l名前: §f§l${quest.name}")
        sender.sendMessage("§e§lタイプ: §f§l${quest.type}")
        sender.sendMessage("§e§l目標: §f§l${quest.target} x${quest.amount}")
        // ライフ数表示追加（パーティーメンバー全員分）
        if (quest.maxLives != null) {
            // パーティーが有効ならメンバーを取得、そうでなければ自身のみ
            val partyMembers = if (quest.partyEnabled && sender is org.bukkit.entity.Player) {
                PartyManager.getPartyMembers(sender).toMutableList().also {
                    if (!it.contains(sender)) it.add(sender)
                }
            } else {
                if (sender is org.bukkit.entity.Player) listOf(sender) else emptyList()
            }

            val uniqueMembers = partyMembers.distinctBy { it.uniqueId }
            val totalMaxLives = quest.maxLives!! * uniqueMembers.size
            val totalDeaths = uniqueMembers.sumOf { member ->
                com.woxloi.questplugin.ActiveQuestManager.getPlayerData(member.uniqueId)?.deathCount ?: 0
            }
            val remainingLives = (totalMaxLives - totalDeaths).coerceAtLeast(0)

            sender.sendMessage("§d§lライフ数: §f$remainingLives")
        }
        sender.sendMessage("§e§l報酬:")

        if (quest.rewards.isEmpty()) {
            sender.sendMessage("  §7§l(報酬なし)")
        } else {
            quest.rewards.forEach { reward ->
                sender.sendMessage("  §f§l- $reward")
            }
        }
        if (quest.startCommands.isNotEmpty()) {
            sender.sendMessage("§e§l開始時コマンド:")
            quest.startCommands.forEach { cmd ->
                sender.sendMessage("  §f§l- $cmd")
            }
        }

        quest.timeLimitSeconds?.let {
            sender.sendMessage("§e§l時間制限: §f§l${it}秒")
        }
        quest.cooldownSeconds?.let {
            sender.sendMessage("§e§lクールダウン: §f§l${it}秒")
        }
        quest.maxUseCount?.let {
            sender.sendMessage("§e§l最大回数: §f§l${it}回")
        }
        sender.sendMessage(text("§c§l[ここをクリックでクエスト開始コマンドを自動入力する]").clickEvent(suggestCommand("/quest start $questId")));

        if (quest.partyEnabled) {
            sender.sendMessage("§e§lパーティー対応: §a§l有効")
            sender.sendMessage("  §7§l進捗共有: ${if (quest.shareProgress) "§a§lはい" else "§c§lいいえ"}")
            sender.sendMessage("  §7§l達成共有: ${if (quest.shareCompletion) "§a§lはい" else "§c§lいいえ"}")

            val max = quest.partyMaxMembers
            sender.sendMessage("  §7§l人数上限: ${if (max == null || max <= 0) "§7§l制限なし" else "§f§l${max}人"}")
            sender.sendMessage(text("§c§l[ここをクリックでパーティーコマンドを自動入力する]").clickEvent(suggestCommand("/quest party create")));
        } else {
            sender.sendMessage("§e§lパーティー対応: §c§l無効")
        }


        if (quest.teleportWorld != null) {
            sender.sendMessage("§e§lテレポート先: §f§l${quest.teleportWorld} (${quest.teleportX}, ${quest.teleportY}, ${quest.teleportZ})")
            sender.sendMessage( text("&c&l[ここをクリックでテレポート先に自動でテレポートする]") .clickEvent(runCommand("/execute in ${quest.teleportWorld} run tp ${sender.name} ${quest.teleportX} ${quest.teleportY} ${quest.teleportZ}")) )
        }


        sender.sendMessage("§7§l==============================")
        return true
    }
}
