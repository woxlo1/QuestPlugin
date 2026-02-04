package com.woxloi.questplugin.integrations

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.manager.ActiveQuestManager
import com.woxloi.questplugin.manager.QuestConfigManager
import net.citizensnpcs.api.event.NPCRightClickEvent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * Citizens NPCとクエストシステムの連携
 */
class CitizensIntegration : Listener {

    /**
     * NPCを右クリックした時の処理
     */
    @EventHandler
    fun onNPCRightClick(event: NPCRightClickEvent) {
        val npc = event.npc
        val player = event.clicker

        // NPCにクエストIDが設定されているか確認
        val questId = npc.data().get<String>("quest_id") ?: return
        val quest = QuestConfigManager.getQuest(questId)

        if (quest == null) {
            player.sendMessage(QuestPlugin.prefix + "§c§lこのNPCのクエストが見つかりません（ID: $questId）")
            return
        }

        // 既にクエスト進行中の場合
        if (ActiveQuestManager.isQuesting(player)) {
            val currentQuest = ActiveQuestManager.getQuest(player)
            if (currentQuest?.id == questId) {
                showQuestProgress(player, npc, quest)
            } else {
                player.sendMessage(QuestPlugin.prefix + "§c§l別のクエストが進行中です")
            }
            return
        }

        // クエストダイアログを表示
        showQuestDialog(player, npc, quest)
    }

    /**
     * クエスト受注ダイアログ表示
     */
    private fun showQuestDialog(player: Player, npc: net.citizensnpcs.api.npc.NPC, quest: com.woxloi.questplugin.model.QuestData) {
        val npcName = npc.name
        val greeting = npc.data().get<String>("quest_greeting") ?: "やあ、冒険者！"

        player.sendMessage("§7§l━━━━━━━━━━━━━━━━━━━━━━━━")
        player.sendMessage("§e§l${npcName}§7: $greeting")
        player.sendMessage("")
        player.sendMessage("§6§lクエスト: §f§l${quest.name}")
        player.sendMessage("§7${quest.type.displayName}: ${quest.target} を ${quest.amount}個")

        // 制限時間表示
        quest.timeLimitSeconds?.let {
            val minutes = it / 60
            player.sendMessage("§c§l制限時間: §f§l${minutes}分")
        }

        // 報酬表示
        if (quest.rewards.isNotEmpty()) {
            player.sendMessage("")
            player.sendMessage("§e§l報酬:")
            quest.rewards.take(3).forEach { reward ->
                player.sendMessage("  §7- §f${reward}")
            }
        }

        player.sendMessage("")

        // クリック可能なボタン
        player.sendMessage(
            Component.text("§a§l[クエストを受ける]")
                .clickEvent(ClickEvent.runCommand("/quest start ${quest.id}"))
                .append(Component.text("  "))
                .append(
                    Component.text("§c§l[断る]")
                        .clickEvent(ClickEvent.runCommand("/quest npc cancel"))
                )
        )
        player.sendMessage("§7§l━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    /**
     * クエスト進行状況表示
     */
    private fun showQuestProgress(player: Player, npc: net.citizensnpcs.api.npc.NPC, quest: com.woxloi.questplugin.model.QuestData) {
        val data = ActiveQuestManager.getPlayerData(player.uniqueId) ?: return
        val npcName = npc.name

        player.sendMessage("§7§l━━━━━━━━━━━━━━━━━━━━━━━━")
        player.sendMessage("§e§l${npcName}§7: 頑張っているようだね！")
        player.sendMessage("")
        player.sendMessage("§6§l進行状況: §f§l${data.progress} / ${quest.amount}")

        val percentage = (data.progress.toDouble() / quest.amount * 100).toInt()
        val bar = buildProgressBar(percentage)
        player.sendMessage(bar)

        if (data.progress >= quest.amount) {
            player.sendMessage("")
            player.sendMessage("§a§lよくやった！クエスト完了だ！")
        } else {
            player.sendMessage("")
            player.sendMessage("§7まだ ${quest.amount - data.progress} 必要だ。頑張れ！")
        }
        player.sendMessage("§7§l━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    /**
     * プログレスバー生成
     */
    private fun buildProgressBar(percentage: Int): String {
        val total = 20
        val filled = (percentage / 5).coerceIn(0, total)
        val empty = total - filled

        return "§a" + "█".repeat(filled) + "§7" + "█".repeat(empty) + " §f${percentage}%"
    }
}

/**
 * NPCクエストギバーのGUI
 */
class NPCQuestGUI(private val player: Player, private val npc: net.citizensnpcs.api.npc.NPC) {

    fun open() {
        val questId = npc.data().get<String>("quest_id") ?: return
        val quest = QuestConfigManager.getQuest(questId) ?: return

        val inv = Bukkit.createInventory(null, 27, "§6${npc.name} - クエスト")

        // クエスト情報アイテム
        val questItem = ItemStack(Material.WRITABLE_BOOK)
        val meta = questItem.itemMeta
        meta.displayName(Component.text("§e§l${quest.name}"))
        meta.lore(listOf(
            Component.text("§7タイプ: ${quest.type.displayName}"),
            Component.text("§7目標: ${quest.target} x${quest.amount}"),
            Component.text(""),
            Component.text("§aクリックで受注")
        ))
        questItem.itemMeta = meta
        inv.setItem(13, questItem)

        // 報酬アイテム
        if (quest.rewards.isNotEmpty()) {
            val rewardItem = ItemStack(Material.GOLD_INGOT)
            val rewardMeta = rewardItem.itemMeta
            rewardMeta.displayName(Component.text("§6§l報酬"))
            rewardMeta.lore(quest.rewards.map { Component.text("§7$it") })
            rewardItem.itemMeta = rewardMeta
            inv.setItem(22, rewardItem)
        }

        player.openInventory(inv)
    }
}