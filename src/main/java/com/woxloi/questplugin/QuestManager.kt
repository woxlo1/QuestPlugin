package com.woxloi.questplugin

object QuestManager {
    private val quests = mutableMapOf<String, QuestData>()

    fun getQuestById(id: String): QuestData? = quests[id]

}
