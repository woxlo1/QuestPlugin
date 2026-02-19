package com.woxloi.questplugin.listeners

import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.manager.PlayerQuestManager
import com.woxloi.questplugin.model.QuestType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerPickupItemEvent
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent

/**
 * 民間クエストの進行状況を追跡するリスナー
 * 既存クエストの進行とは独立して動作
 */
class PlayerQuestProgressListener : Listener {

    @EventHandler
    fun onEntityDeath(e: EntityDeathEvent) {
        val killer = e.entity.killer ?: return
        checkAndAddProgress(killer, QuestType.KILL, e.entity.type.name)
    }

    @EventHandler
    fun onMythicMobKill(e: MythicMobDeathEvent) {
        val killer = e.killer as? Player ?: return
        checkAndAddProgress(killer, QuestType.MYTHIC_KILL, e.mobType.internalName)
    }

    @EventHandler
    fun onPickup(e: PlayerPickupItemEvent) {
        checkAndAddProgress(e.player, QuestType.COLLECT, e.item.itemStack.type.name, e.item.itemStack.amount)
    }

    @EventHandler
    fun onBlockBreak(e: BlockBreakEvent) {
        checkAndAddProgress(e.player, QuestType.MINE, e.block.type.name)
        checkAndAddProgress(e.player, QuestType.BREAK, e.block.type.name)
    }

    @EventHandler
    fun onBlockPlace(e: BlockPlaceEvent) {
        checkAndAddProgress(e.player, QuestType.PLACE, e.block.type.name)
    }

    @EventHandler
    fun onCraft(e: CraftItemEvent) {
        val player = e.whoClicked as? Player ?: return
        val item = e.currentItem ?: return
        checkAndAddProgress(player, QuestType.CRAFT, item.type.name)
    }

    @EventHandler
    fun onFish(e: PlayerFishEvent) {
        if (e.state != PlayerFishEvent.State.CAUGHT_FISH) return
        val caught = e.caught ?: return
        checkAndAddProgress(e.player, QuestType.FISH, caught.type.name)
    }

    private fun checkAndAddProgress(player: Player, type: QuestType, target: String, amount: Int = 1) {
        val acceptedIds = PlayerQuestManager.getAcceptedQuestIds(player)
        if (acceptedIds.isEmpty()) return

        for (questId in acceptedIds) {
            val quest = PlayerQuestManager.getQuest(questId) ?: continue
            if (quest.type != type) continue
            if (quest.target.lowercase() != "any" && quest.target.lowercase() != target.lowercase()) continue

            val added = PlayerQuestManager.addProgress(player, questId, amount)
            if (!added) continue

            val progress = PlayerQuestManager.getProgress(player, questId)
            player.sendMessage(QuestPlugin.prefix + "§e§l[民間] §f${quest.title} §7進捗: §a$progress/${quest.amount}")

            if (progress >= quest.amount) {
                val result = PlayerQuestManager.completeQuest(player, questId)
                result.onSuccess {
                    player.sendMessage(QuestPlugin.prefix + "§a§l[民間クエスト] §e§l${quest.title}§a§l を完了しました！")
                }.onFailure {
                    player.sendMessage(QuestPlugin.prefix + "§c§l完了処理エラー: ${it.message}")
                }
            }
        }
    }
}
