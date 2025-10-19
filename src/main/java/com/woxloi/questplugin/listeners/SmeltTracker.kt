package com.woxloi.questplugin.listeners

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import com.woxloi.questplugin.ActiveQuestManager
import com.woxloi.questplugin.QuestType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SmeltTracker : Listener {

    private val lastSmelters = ConcurrentHashMap<UUID, Long>()

    /**
     * プレイヤーがかまど系インベントリを開いた時間を記録
     */
    @EventHandler
    fun onInventoryOpen(e: InventoryOpenEvent) {
        val player = e.player as? Player ?: return
        val invType = e.inventory.type
        if (invType == InventoryType.FURNACE || invType == InventoryType.BLAST_FURNACE || invType == InventoryType.SMOKER) {
            lastSmelters[player.uniqueId] = System.currentTimeMillis()
            Bukkit.getLogger().info("[QuestPlugin] Inventory opened by ${player.name} type=$invType")
        }
    }

    /**
     * プレイヤーがかまど系インベントリを操作した時間を記録
     */
    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val player = e.whoClicked as? Player ?: return
        val invType = e.inventory.type
        if (invType == InventoryType.FURNACE || invType == InventoryType.BLAST_FURNACE || invType == InventoryType.SMOKER) {
            lastSmelters[player.uniqueId] = System.currentTimeMillis()
            Bukkit.getLogger().info("[QuestPlugin] Inventory clicked by ${player.name} type=$invType")
        }
    }

    /**
     * FurnaceSmeltEventで直近にかまど操作を行ったプレイヤーを返す
     */
    fun getRecentSmelter(timeoutMillis: Long = 30000): Player? {
        val now = System.currentTimeMillis()
        val recent = lastSmelters.entries
            .filter { now - it.value <= timeoutMillis }
            .maxByOrNull { it.value }
            ?: return null
        return Bukkit.getPlayer(recent.key)
    }

    /**
     * FurnaceSmeltEventを受けてクエスト進行処理
     */
    @EventHandler
    fun onSmeltItem(e: FurnaceSmeltEvent) {
        val player = getRecentSmelter() ?: run {
            Bukkit.getLogger().info("[QuestPlugin] Smelt event: no recent smelter found")
            return
        }
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) {
            Bukkit.getLogger().info("[QuestPlugin] Player ${player.name} is not questing")
            return
        }

        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: run {
            Bukkit.getLogger().info("[QuestPlugin] Player ${player.name} has no active quest")
            return
        }
        if (quest.type != QuestType.SMELT) {
            Bukkit.getLogger().info("[QuestPlugin] Quest type ${quest.type} is not SMELT")
            return
        }

        val targetItem = quest.target.lowercase()
        val smeltedItem = e.result.type.name.lowercase()

        Bukkit.getLogger().info("[QuestPlugin] Player ${player.name} smelted: $smeltedItem, quest target: $targetItem")

        if (targetItem != "any" && targetItem != smeltedItem) return

        com.woxloi.questplugin.ActiveQuestManager.addProgress(player, 1)
        Bukkit.getLogger().info("[QuestPlugin] Progress added for player ${player.name}")
    }
}
