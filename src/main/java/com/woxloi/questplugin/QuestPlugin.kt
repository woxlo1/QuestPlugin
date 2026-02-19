package com.woxloi.questplugin

import org.bukkit.plugin.java.JavaPlugin
import com.woxloi.questplugin.commands.QuestCommand
import com.woxloi.questplugin.listeners.*
import com.woxloi.questplugin.database.DatabaseManager
import com.woxloi.questplugin.features.DailyQuestManager
import com.woxloi.questplugin.features.PlayerQuestBoardGUI
import com.woxloi.questplugin.features.QuestChainManager
import com.woxloi.questplugin.features.QuestListGUI
import com.woxloi.questplugin.features.QuestScriptEngine
import com.woxloi.questplugin.integrations.CitizensIntegration
import com.woxloi.questplugin.manager.ActiveQuestManager
import com.woxloi.questplugin.manager.PlayerQuestManager
import com.woxloi.questplugin.manager.QuestConfigManager

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

        // データベース初期化
        try {
            DatabaseManager.init(this)
            if (DatabaseManager.isEnabled()) {
                logger.info("§a[QuestPlugin] MySQL接続完了 - データベースモードで動作します")
            } else {
                logger.info("§e[QuestPlugin] YAMLモードで動作します")
            }
        } catch (e: Exception) {
            logger.severe("§c[QuestPlugin] データベース初期化エラー。YAMLモードにフォールバックします")
            logger.severe(e.message)
        }

        // スクリプトエンジン初期化
        try {
            QuestScriptEngine.init()
            logger.info("§a[QuestPlugin] スクリプトエンジンを有効化しました")
        } catch (e: Exception) {
            logger.warning("§e[QuestPlugin] スクリプトエンジンの初期化に失敗")
        }

        if (server.pluginManager.getPlugin("Citizens") != null) {
            server.pluginManager.registerEvents(CitizensIntegration(), this)
            logger.info("§a[QuestPlugin] Citizens連携を有効化しました")
        }

        // クエスト設定読み込み
        QuestConfigManager.loadAllQuests()

        // アクティブクエスト管理初期化
        ActiveQuestManager.init()

        DailyQuestManager.init()
        QuestChainManager.init()

        // 民間クエスト初期化
        PlayerQuestManager.init()

        // コマンド登録
        commandRouter = QuestCommand()

        // イベントリスナー登録
        server.pluginManager.registerEvents(QuestRespawnListener(), this)
        server.pluginManager.registerEvents(QuestProgressListener(), this)
        server.pluginManager.registerEvents(SmeltTracker, this)
        server.pluginManager.registerEvents(QuestDeathListener(this), this)

        // GUI系リスナー（SInventoryが自己管理するため登録不要だが、objectシングルトンは登録する）
        server.pluginManager.registerEvents(PlayerQuestProgressListener(), this)

        // コマンド設定
        getCommand("quest")!!.setExecutor(commandRouter)
        getCommand("quest")!!.tabCompleter = commandRouter

        logger.info("§a[QuestPlugin] プラグインが有効化されました (Version: $version)")
    }

    override fun onDisable() {
        ActiveQuestManager.shutdown()
        QuestScriptEngine.shutdown()
        DailyQuestManager.cleanupOldData()
        PlayerQuestManager.save()
        DatabaseManager.close()

        logger.info("§a[QuestPlugin] プラグインが無効化されました")
    }
}
