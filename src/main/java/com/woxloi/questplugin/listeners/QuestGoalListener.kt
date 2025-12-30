package com.woxloi.questplugin.listeners

import com.woxloi.questplugin.ActiveQuestManager
import com.woxloi.questplugin.floor.QuestFloorManager
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent

class QuestGoalListener : Listener {

    @EventHandler
    fun onMove(e: PlayerMoveEvent) {
        val player = e.player

        // クエスト中でなければ無視
        val data = ActiveQuestManager.getPlayerData(player.uniqueId) ?: return

        // ブロック移動なしは無視
        if (e.from.block == e.to?.block) return

        val block = player.location.block

        // フロア取得
        val instanceId = data.floorInstanceId ?: return
        val instance = QuestFloorManager.getInstance(instanceId) ?: return

        val markers = QuestFloorManager.getMarkers(instance)

        when (block.type) {

            // ========================
            // ゴール（クエスト完了）
            // ========================
            Material.DIAMOND_BLOCK -> {
                val goal = markers.firstOrNull {
                    it.type == QuestFloorManager.MarkerType.GOAL
                } ?: return

                if (!sameBlock(block.location, goal.location)) return
                if (data.progress >= data.quest.amount) return

                ActiveQuestManager.completeQuest(player)

                player.sendMessage("§a§lクリア！！")
            }

            // ========================
            // 次のフロアへ
            // ========================
            Material.CRYING_OBSIDIAN -> {
                val next = markers.firstOrNull {
                    it.type == QuestFloorManager.MarkerType.NEXT
                } ?: return

                player.sendMessage("§7転移中...")

                if (!sameBlock(block.location, next.location)) return

                // 現在のフロア解放
                QuestFloorManager.release(instanceId)

                // 次のフロアへ
                val nextInstance = QuestFloorManager.createInstance(
                    data.quest,
                    listOf(player)
                )

                data.floorInstanceId = nextInstance.instanceId
            }

            else -> return
        }
    }

    private fun sameBlock(a: org.bukkit.Location, b: org.bukkit.Location): Boolean {
        return a.blockX == b.blockX &&
                a.blockY == b.blockY &&
                a.blockZ == b.blockZ
    }
}
