package com.woxloi.questplugin.listeners

import com.woxloi.questplugin.QuestPlugin
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import java.util.*

/**
 * Quest用ワンドの範囲選択リスナー
 */
object QuestWandData {
    // プレイヤーごとに始点・終点を保持
    val startPoint = mutableMapOf<UUID, Vector>()
    val endPoint = mutableMapOf<UUID, Vector>()
}

class QuestWandListener : Listener {

    @EventHandler
    fun onUseWand(e: PlayerInteractEvent) {
        val player: Player = e.player
        val item = e.item ?: return
        val meta = item.itemMeta ?: return

        // QuestWand 判定
        if (!meta.persistentDataContainer.has(
                QuestPlugin.questWandKey,
                PersistentDataType.BYTE
            )
        ) return

        // ブロック以外のクリックは無視
        val clickedBlock = e.clickedBlock ?: return

        when (e.action) {
            Action.LEFT_CLICK_BLOCK -> {
                // 始点を設定
                QuestWandData.startPoint[player.uniqueId] = clickedBlock.location.toVector()
                player.sendMessage(
                    "${QuestPlugin.prefix}§a§l始点を設定しました §7(" +
                            "${clickedBlock.x}, ${clickedBlock.y}, ${clickedBlock.z}§7)"
                )
            }

            Action.RIGHT_CLICK_BLOCK -> {
                // 終点を設定
                QuestWandData.endPoint[player.uniqueId] = clickedBlock.location.toVector()
                player.sendMessage(
                    "${QuestPlugin.prefix}§b§l終点を設定しました §7(" +
                            "${clickedBlock.x}, ${clickedBlock.y}, ${clickedBlock.z}§7)"
                )
            }

            else -> return
        }

        // ブロックを壊したり水を置いたりするのを防ぐ
        e.isCancelled = true
    }

    /**
     * プレイヤーの始点・終点を取得するヘルパー
     */
    fun getSelection(player: Player): Pair<Vector, Vector>? {
        val start = QuestWandData.startPoint[player.uniqueId]
        val end = QuestWandData.endPoint[player.uniqueId]
        return if (start != null && end != null) start to end else null
    }

    /**
     * 選択範囲をBukkit Locationで取得
     */
    fun getSelectionLocations(player: Player): Pair<Location, Location>? {
        val sel = getSelection(player) ?: return null
        return VectorToLocation(player.world, sel.first) to VectorToLocation(player.world, sel.second)
    }

    private fun VectorToLocation(world: org.bukkit.World?, vec: Vector): Location {
        return Location(world, vec.x, vec.y, vec.z)
    }
}
