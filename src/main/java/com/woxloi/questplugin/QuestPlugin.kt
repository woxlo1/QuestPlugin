package com.woxloi.questplugin

import org.bukkit.plugin.java.JavaPlugin
import com.woxloi.questplugin.commands.QuestCommand
import com.woxloi.questplugin.listeners.*
import org.bukkit.NamespacedKey

class QuestPlugin : JavaPlugin() {

    companion object {
        val MoneyPrefix = "§a[§6§lQuest§e§lMoney§6§lPlugin§a]"
        val PartyPrefix = "§a[§6§lQuestPartyPlugin§a]"
        val prefix = "§a[§6§lQuestPlugin§a]"
        val version = "2025/7/2"

        lateinit var plugin: QuestPlugin
        lateinit var commandRouter: QuestCommand
        lateinit var questWandKey: NamespacedKey
    }

    override fun onEnable() {
        plugin = this
        questWandKey = NamespacedKey(this, "quest_wand")

        saveDefaultConfig()

        QuestConfigManager.loadAllQuests()
        ActiveQuestManager.init()
        commandRouter = QuestCommand()

        server.pluginManager.registerEvents(QuestRespawnListener(), this)
        server.pluginManager.registerEvents(QuestProgressListener(), this)
        server.pluginManager.registerEvents(QuestWandListener(), this)
        server.pluginManager.registerEvents(QuestGoalListener(), this)
        server.pluginManager.registerEvents(SmeltTracker, this)
        server.pluginManager.registerEvents(QuestDeathListener(this), this)

        getCommand("quest")!!.setExecutor(commandRouter)
        getCommand("quest")!!.tabCompleter = commandRouter

        logger.info("QuestPlugin has been enabled.")
    }

    override fun onDisable() {
        ActiveQuestManager.shutdown()
        logger.info("QuestPlugin has been disabled.")
    }
}
