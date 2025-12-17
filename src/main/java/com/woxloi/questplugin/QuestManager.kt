package com.woxloi.questplugin

import org.bukkit.Bukkit
import java.io.File
import java.util.*

object QuestManager {
    private val quests = mutableMapOf<String, QuestData>()

    private val dailyQuests = mutableListOf<QuestData>()
    private val weeklyQuests = mutableListOf<QuestData>()

    private val dailyFile = File(QuestPlugin.plugin.dataFolder, "daily_quests.yml")
    private val weeklyFile = File(QuestPlugin.plugin.dataFolder, "weekly_quests.yml")

    fun registerQuest(quest: QuestData) {
        quests[quest.id] = quest
    }

    fun getQuestById(id: String): QuestData? = quests[id]

    fun getAllQuests(): Collection<QuestData> = quests.values

    fun getDailyQuests(): List<QuestData> = dailyQuests.toList()
    fun getWeeklyQuests(): List<QuestData> = weeklyQuests.toList()

    fun generateRandomDailyQuests() {
        val random = Random()
        val all = quests.values.toList().shuffled(random)
        dailyQuests.clear()
        dailyQuests.addAll(all.take(5))
        saveDailyQuests()
        Bukkit.getLogger().info("[QuestPlugin] Daily quests have been regenerated.")
    }

    fun generateRandomWeeklyQuests() {
        val random = Random()
        val all = quests.values.toList().shuffled(random)
        weeklyQuests.clear()
        weeklyQuests.addAll(all.take(10))
        saveWeeklyQuests()
        Bukkit.getLogger().info("[QuestPlugin] Weekly quests have been regenerated.")
    }

    fun saveDailyQuests() {
        val config = org.bukkit.configuration.file.YamlConfiguration()
        config.set("daily", dailyQuests.map { it.id })
        config.save(dailyFile)
    }

    fun saveWeeklyQuests() {
        val config = org.bukkit.configuration.file.YamlConfiguration()
        config.set("weekly", weeklyQuests.map { it.id })
        config.save(weeklyFile)
    }

    fun loadQuests() {
        if (dailyFile.exists()) {
            val config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dailyFile)
            val ids = config.getStringList("daily")
            dailyQuests.clear()
            ids.mapNotNull { getQuestById(it) }.forEach { dailyQuests.add(it) }
        }

        if (weeklyFile.exists()) {
            val config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(weeklyFile)
            val ids = config.getStringList("weekly")
            weeklyQuests.clear()
            ids.mapNotNull { getQuestById(it) }.forEach { weeklyQuests.add(it) }
        }
    }
}
