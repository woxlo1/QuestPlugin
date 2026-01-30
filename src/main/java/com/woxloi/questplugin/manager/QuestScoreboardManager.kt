package com.woxloi.questplugin.manager

import com.shojabon.mcutils.Utils.SScoreboard
import com.woxloi.questplugin.model.QuestData
import com.woxloi.questplugin.party.PartyManager
import org.bukkit.GameMode
import org.bukkit.entity.Player

class QuestScoreboard(private val player: Player, private val quest: QuestData) {

    private val scoreboard = SScoreboard("TEST")

    private var currentAmount: Int = 0
    private var remainingTimeSeconds: Long? = null

    fun show() {
        scoreboard.setTitle("§4§lQuestPlugin")
        update()
        scoreboard.addPlayer(player)
    }

    fun updateProgress(newAmount: Int) {
        currentAmount = newAmount.coerceAtMost(quest.amount)
        update()
    }

    fun updateRemainingTime(seconds: Long?) {
        remainingTimeSeconds = seconds
        update()
    }

    fun update() {
        // スコアボードをクリア（SScoreboardにclearメソッドがある場合）

        var index = 0
        scoreboard.setText(index++, "§a§lクエスト名: ${quest.name}")
        scoreboard.setText(index++, "§c§lクエスト進行中")
        scoreboard.setText(index++, "§e§l目標: §f§l${quest.target} x${quest.amount}")
        scoreboard.setText(index++, "§a§l進行状況: §e$currentAmount / ${quest.amount}")

        // ライフ残り 合計
        if (quest.maxLives != null) {
            // パーティーメンバー全員(自分含む)
            val partyMembers = PartyManager.getPartyMembers(player).toMutableList()
            if (!partyMembers.contains(player)) partyMembers.add(player)

            val totalMaxLives = quest.maxLives!! * partyMembers.distinctBy { it.uniqueId }.size
            val totalDeaths = partyMembers.distinctBy { it.uniqueId }.sumOf { member ->
                com.woxloi.questplugin.manager.ActiveQuestManager.getPlayerData(member.uniqueId)?.deathCount ?: 0
            }
            val remainingLives = (totalMaxLives - totalDeaths).coerceAtLeast(0)

            scoreboard.setText(index++, "§d§l総ライフ数: §f$remainingLives")
        }

        // パーティーメンバーの表示
        val partyMembers = PartyManager.getPartyMembers(player)
            .filter { it.gameMode != GameMode.SPECTATOR }

        if (partyMembers.isNotEmpty()) {
            scoreboard.setText(index++, "§b§lパーティーメンバー:")
            for (member in partyMembers) {
                val currentHP = member.health.toInt()
                val maxHP = member.maxHealth.toInt()
                scoreboard.setText(index++, "§f§l${member.name} §d§l♥§7§l$currentHP")
            }
        }

        // 制限時間の表示（最後に表示）
        if (remainingTimeSeconds != null) {
            val min = remainingTimeSeconds!! / 60
            val sec = remainingTimeSeconds!! % 60
            scoreboard.setText(index++, "§c§l残り時間: §f%02d:%02d".format(min, sec))
        }

        scoreboard.renderText()
    }

    fun hide() {
        scoreboard.remove()
    }
}