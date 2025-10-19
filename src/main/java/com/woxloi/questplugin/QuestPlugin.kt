package com.woxloi.questplugin

import org.bukkit.plugin.java.JavaPlugin
import com.woxloi.questplugin.commands.QuestCommand
import com.woxloi.questplugin.listeners.QuestDeathListener
import com.woxloi.questplugin.listeners.QuestProgressListener
import com.woxloi.questplugin.listeners.QuestRespawnListener
import com.woxloi.questplugin.listeners.SmeltTracker

class QuestPlugin : JavaPlugin() {

    companion object {
        val MoneyPrefix = "§a[§6§lQuest§e§lMoney§6§lPlugin§a]"
        val PartyPrefix = "§a[§6§lQuestPartyPlugin§a]"
        val prefix = "§a[§6§lQuestPlugin§a]"
        val version = "2025/7/2"
        lateinit var plugin: QuestPlugin
        lateinit var commandRouter: QuestCommand
    }

    override fun onEnable() {
        plugin = this

        saveDefaultConfig()
        QuestConfigManager.loadAllQuests()
        ActiveQuestManager.init()
        commandRouter = QuestCommand()

        // イベント登録
        server.pluginManager.registerEvents(QuestRespawnListener(), this)
        server.pluginManager.registerEvents(QuestProgressListener(), this)
        server.pluginManager.registerEvents(SmeltTracker, this)
        server.pluginManager.registerEvents(QuestDeathListener(this), this)
        getCommand("quest")!!.setExecutor(commandRouter)
        getCommand("quest")!!.tabCompleter = commandRouter

        logger.info("QuestPlugin has been enabled.")
    }

    override fun onDisable() {
        com.woxloi.questplugin.ActiveQuestManager.shutdown()
        logger.info("QuestPlugin has been disabled.")
    }
}
