package com.woxloi.questplugin.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerRespawnEvent
import com.woxloi.questplugin.ActiveQuestManager
import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.party.PartyManager
import org.bukkit.event.player.PlayerQuitEvent

class QuestRespawnListener : Listener {

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val data = com.woxloi.questplugin.ActiveQuestManager.getPlayerData(player.uniqueId) ?: return
        val quest = data.quest

        val destination = if (quest.partyEnabled) {
            val aliveMembers = PartyManager.getPartyMembers(player).filter { member ->
                val memberData = com.woxloi.questplugin.ActiveQuestManager.getPlayerData(member.uniqueId)
                memberData != null && (quest.maxLives ?: 0) > memberData.deathCount && member.uniqueId != player.uniqueId
            }

            if (aliveMembers.isNotEmpty()) {
                val target = aliveMembers.random()
                val loc = target.location.clone().add(1.0, 0.0, 1.0)
                loc.y = loc.world.getHighestBlockYAt(loc).toDouble()
                player.sendMessage(QuestPlugin.prefix + "§e§lパーティーメンバーの近くにリスポーンしました ")
                loc
            } else {
                player.sendMessage(QuestPlugin.prefix + "§e§l元いた場所にリスポーンしました ")
                data.originalLocation.clone()
            }
        } else {
            player.sendMessage(QuestPlugin.prefix + "§e§l元いた場所にリスポーンしました ")
            data.originalLocation.clone()
        }

        event.respawnLocation = destination
    }
    @EventHandler

    fun onQuit(e: PlayerQuitEvent) {
        if (ActiveQuestManager.isQuesting(e.player)) {
            ActiveQuestManager.cancelQuest(e.player)
        }
    }
}
