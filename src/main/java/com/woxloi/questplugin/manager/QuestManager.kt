package com.woxloi.questplugin.manager

import com.woxloi.questplugin.model.QuestData

/**
 * QuestConfigManager への委譲ラッパー
 * 旧コードとの互換性維持のため残す
 */
object QuestManager {
    fun getQuestById(id: String): QuestData? = QuestConfigManager.getQuest(id)
}
