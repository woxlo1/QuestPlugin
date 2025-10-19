package com.woxloi.questplugin

object QuestManager {
    private val quests = mutableMapOf<String, QuestData>()

    fun registerQuest(quest: QuestData) {
        quests[quest.id] = quest
    }

    fun getQuestById(id: String): QuestData? {
        return quests[id]
    }

    fun getAllQuests(): Collection<QuestData> {
        return quests.values
    }
}
