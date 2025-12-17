package com.woxloi.questplugin.listeners

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.woxloi.questplugin.QuestPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.persistence.PersistentDataType

class QuestWandListener : Listener {

    @EventHandler
    fun onUseWand(e: PlayerInteractEvent) {
        val player = e.player
        val item = e.item ?: return
        val meta = item.itemMeta ?: return

        // QuestWand 判定
        if (!meta.persistentDataContainer.has(
                QuestPlugin.questWandKey,
                PersistentDataType.BYTE
            )
        ) return

        val action = e.action
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return

        val session = WorldEdit.getInstance()
            .sessionManager
            .get(BukkitAdapter.adapt(player))

        val selection = try {
            session.selection
        } catch (ex: Exception) {
            null
        }

        if (selection == null) return

        if (action == Action.LEFT_CLICK_BLOCK) {
            val min = selection.minimumPoint
            player.sendMessage(
                QuestPlugin.prefix +
                        "§a§l始点を設定しました §7(" +
                        "§f${min.blockX}, ${min.blockY}, ${min.blockZ}§7)"
            )
        }

        if (action == Action.RIGHT_CLICK_BLOCK) {
            val max = selection.maximumPoint
            player.sendMessage(
                QuestPlugin.prefix +
                        "§b§l終点を設定しました §7(" +
                        "§f${max.blockX}, ${max.blockY}, ${max.blockZ}§7)"
            )
        }
    }
}
