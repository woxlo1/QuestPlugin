package com.woxloi.questplugin.manager

import com.woxloi.questplugin.model.QuestData

object QuestManager {
    private val quests = mutableMapOf<String, QuestData>()

    fun getQuestById(id: String): QuestData? = quests[id]

}
