package com.woxloi.questplugin.features

import com.woxloi.questplugin.manager.QuestConfigManager
import com.woxloi.questplugin.model.QuestData
import com.woxloi.questplugin.QuestPlugin
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.time.*
import java.util.*

/**
 * デイリー・ウィークリークエスト管理
 */
object DailyQuestManager {

    private val configFile = File(QuestPlugin.plugin.dataFolder, "daily_quests.yml")
    private lateinit var config: YamlConfiguration

    // 現在アクティブなクエスト
    private val currentDailyQuests = mutableListOf<String>()
    private val currentWeeklyQuests = mutableListOf<String>()

    // プレイヤーの完了記録（日付ごと）
    private val playerDailyCompletion = mutableMapOf<UUID, MutableMap<String, MutableSet<String>>>()
    private val playerWeeklyCompletion = mutableMapOf<UUID, MutableMap<String, MutableSet<String>>>()

    fun init() {
        loadConfig()
        loadPlayerData()

        // 初回ローテーション
        if (currentDailyQuests.isEmpty()) {
            rotateDailyQuests()
        }
        if (currentWeeklyQuests.isEmpty()) {
            rotateWeeklyQuests()
        }

        // スケジュール設定
        scheduleDailyRotation()
        scheduleWeeklyRotation()
    }

    private fun loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig()
        }
        config = YamlConfiguration.loadConfiguration(configFile)
    }

    private fun createDefaultConfig() {
        configFile.parentFile.mkdirs()
        configFile.createNewFile()

        val defaultConfig = YamlConfiguration()

        // デイリークエスト設定
        defaultConfig.set("daily.enabled", true)
        defaultConfig.set("daily.reset_time", "04:00")
        defaultConfig.set("daily.count", 3)
        defaultConfig.set("daily.pool", listOf(
            "daily_gather_wood",
            "daily_kill_zombies",
            "daily_mine_stone",
            "daily_fishing",
            "daily_farming"
        ))

        // ウィークリークエスト設定
        defaultConfig.set("weekly.enabled", true)
        defaultConfig.set("weekly.reset_day", "MONDAY")
        defaultConfig.set("weekly.reset_time", "04:00")
        defaultConfig.set("weekly.count", 1)
        defaultConfig.set("weekly.pool", listOf(
            "weekly_boss_raid",
            "weekly_dungeon_clear",
            "weekly_treasure_hunt"
        ))

        defaultConfig.save(configFile)
    }

    /**
     * デイリークエストのローテーション
     */
    fun rotateDailyQuests() {
        if (!config.getBoolean("daily.enabled", true)) return

        val pool = config.getStringList("daily.pool")
        val count = config.getInt("daily.count", 3)

        // ランダムに選択
        currentDailyQuests.clear()
        currentDailyQuests.addAll(pool.shuffled().take(count))

        // 保存
        saveDailyQuests()

        // 全プレイヤーに通知
        Bukkit.getOnlinePlayers().forEach { player ->
            player.sendMessage(QuestPlugin.prefix + "§a§l新しいデイリークエストが利用可能です！")
            player.sendMessage(QuestPlugin.prefix + "§e§l/quest daily で確認してください")
        }

        QuestPlugin.plugin.logger.info("[DailyQuest] デイリークエストをローテーションしました: $currentDailyQuests")
    }

    /**
     * ウィークリークエストのローテーション
     */
    fun rotateWeeklyQuests() {
        if (!config.getBoolean("weekly.enabled", true)) return

        val pool = config.getStringList("weekly.pool")
        val count = config.getInt("weekly.count", 1)

        currentWeeklyQuests.clear()
        currentWeeklyQuests.addAll(pool.shuffled().take(count))

        saveWeeklyQuests()

        Bukkit.getOnlinePlayers().forEach { player ->
            player.sendMessage(QuestPlugin.prefix + "§a§l新しいウィークリークエストが利用可能です！")
            player.sendMessage(QuestPlugin.prefix + "§e§l/quest weekly で確認してください")
        }

        QuestPlugin.plugin.logger.info("[WeeklyQuest] ウィークリークエストをローテーションしました: $currentWeeklyQuests")
    }

    /**
     * デイリーローテーションのスケジュール
     */
    private fun scheduleDailyRotation() {
        val resetTime = LocalTime.parse(config.getString("daily.reset_time", "04:00"))

        // 次のリセット時刻を計算
        val now = LocalDateTime.now()
        var nextReset = LocalDateTime.of(LocalDate.now(), resetTime)

        if (now.isAfter(nextReset)) {
            nextReset = nextReset.plusDays(1)
        }

        val delayTicks = Duration.between(now, nextReset).seconds * 20

        Bukkit.getScheduler().runTaskLater(QuestPlugin.plugin, Runnable {
            rotateDailyQuests()
            // 24時間ごとに繰り返し
            Bukkit.getScheduler().runTaskTimer(QuestPlugin.plugin, Runnable {
                rotateDailyQuests()
            }, 0L, 20L * 60 * 60 * 24)
        }, delayTicks)

        QuestPlugin.plugin.logger.info("[DailyQuest] 次回リセット: $nextReset")
    }

    /**
     * ウィークリーローテーションのスケジュール
     */
    private fun scheduleWeeklyRotation() {
        val resetDay = DayOfWeek.valueOf(config.getString("weekly.reset_day", "MONDAY")!!)
        val resetTime = LocalTime.parse(config.getString("weekly.reset_time", "04:00"))

        val now = LocalDateTime.now()
        var nextReset = LocalDateTime.of(LocalDate.now(), resetTime)

        // 次のリセット曜日まで進める
        while (nextReset.dayOfWeek != resetDay || now.isAfter(nextReset)) {
            nextReset = nextReset.plusDays(1)
        }

        val delayTicks = Duration.between(now, nextReset).seconds * 20

        Bukkit.getScheduler().runTaskLater(QuestPlugin.plugin, Runnable {
            rotateWeeklyQuests()
            // 1週間ごとに繰り返し
            Bukkit.getScheduler().runTaskTimer(QuestPlugin.plugin, Runnable {
                rotateWeeklyQuests()
            }, 0L, 20L * 60 * 60 * 24 * 7)
        }, delayTicks)

        QuestPlugin.plugin.logger.info("[WeeklyQuest] 次回リセット: $nextReset")
    }

    /**
     * プレイヤーがデイリークエストを完了済みかチェック
     */
    fun hasCompletedDaily(player: Player, questId: String): Boolean {
        val today = LocalDate.now().toString()
        val completed = playerDailyCompletion
            .getOrPut(player.uniqueId) { mutableMapOf() }
            .getOrPut(today) { mutableSetOf() }

        return completed.contains(questId)
    }

    /**
     * デイリークエスト完了を記録
     */
    fun markDailyCompleted(player: Player, questId: String) {
        val today = LocalDate.now().toString()
        playerDailyCompletion
            .getOrPut(player.uniqueId) { mutableMapOf() }
            .getOrPut(today) { mutableSetOf() }
            .add(questId)

        savePlayerData()
    }

    /**
     * 現在のデイリークエスト一覧取得
     */
    fun getCurrentDailyQuests(): List<QuestData> {
        return currentDailyQuests.mapNotNull { QuestConfigManager.getQuest(it) }
    }

    /**
     * 現在のウィークリークエスト一覧取得
     */
    fun getCurrentWeeklyQuests(): List<QuestData> {
        return currentWeeklyQuests.mapNotNull { QuestConfigManager.getQuest(it) }
    }

    /**
     * プレイヤーのデイリー進捗表示
     */
    fun showDailyProgress(player: Player) {
        val quests = getCurrentDailyQuests()

        player.sendMessage("§a§l========== デイリークエスト ==========")

        if (quests.isEmpty()) {
            player.sendMessage("§7現在利用可能なデイリークエストはありません")
            return
        }

        val today = LocalDate.now().toString()
        val completed = playerDailyCompletion[player.uniqueId]?.get(today) ?: emptySet()

        quests.forEach { quest ->
            val status = if (completed.contains(quest.id)) "§a✓ 完了" else "§e● 未完了"
            player.sendMessage("$status §f${quest.name}")
            player.sendMessage("  §7${quest.type.displayName}: ${quest.target} x${quest.amount}")
        }

        val completedCount = quests.count { completed.contains(it.id) }
        player.sendMessage("")
        player.sendMessage("§7進行状況: §f${completedCount}/${quests.size}")
        player.sendMessage("§a§l=====================================")
    }

    /**
     * データ保存
     */
    private fun saveDailyQuests() {
        config.set("current.daily", currentDailyQuests)
        config.save(configFile)
    }

    private fun saveWeeklyQuests() {
        config.set("current.weekly", currentWeeklyQuests)
        config.save(configFile)
    }

    private fun savePlayerData() {
        // プレイヤーデータをYAMLに保存
        val dataFile = File(QuestPlugin.plugin.dataFolder, "daily_player_data.yml")
        val dataConfig = YamlConfiguration()

        playerDailyCompletion.forEach { (uuid, dateMap) ->
            dateMap.forEach { (date, quests) ->
                dataConfig.set("daily.$uuid.$date", quests.toList())
            }
        }

        playerWeeklyCompletion.forEach { (uuid, dateMap) ->
            dateMap.forEach { (date, quests) ->
                dataConfig.set("weekly.$uuid.$date", quests.toList())
            }
        }

        dataConfig.save(dataFile)
    }

    private fun loadPlayerData() {
        val dataFile = File(QuestPlugin.plugin.dataFolder, "daily_player_data.yml")
        if (!dataFile.exists()) return

        val dataConfig = YamlConfiguration.loadConfiguration(dataFile)

        // デイリーデータ読み込み
        dataConfig.getConfigurationSection("daily")?.let { section ->
            section.getKeys(false).forEach { uuidStr ->
                val uuid = UUID.fromString(uuidStr)
                val dateSection = section.getConfigurationSection(uuidStr) ?: return@forEach

                dateSection.getKeys(false).forEach { date ->
                    val quests = dateSection.getStringList(date).toMutableSet()

                    val dateMap = playerDailyCompletion.getOrPut(uuid) { mutableMapOf() }
                    dateMap[date] = quests
                }
            }
        }

        // 現在のクエスト読み込み
        config.getStringList("current.daily").let {
            if (it.isNotEmpty()) currentDailyQuests.addAll(it)
        }
        config.getStringList("current.weekly").let {
            if (it.isNotEmpty()) currentWeeklyQuests.addAll(it)
        }
    }

    /**
     * 古いデータをクリーンアップ（7日以上前）
     */
    fun cleanupOldData() {
        val cutoffDate = LocalDate.now().minusDays(7).toString()

        playerDailyCompletion.values.forEach { dateMap ->
            dateMap.keys.removeIf { it < cutoffDate }
        }

        savePlayerData()
    }
}
