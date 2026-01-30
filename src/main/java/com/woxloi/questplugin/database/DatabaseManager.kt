package com.woxloi.questplugin.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.plugin.java.JavaPlugin
import java.sql.Connection
import java.util.UUID
import java.util.logging.Level

object DatabaseManager {

    private lateinit var dataSource: HikariDataSource
    private lateinit var plugin: JavaPlugin
    private var enabled = false

    /**
     * データベース接続の初期化
     */
    fun init(plugin: JavaPlugin) {
        this.plugin = plugin
        val config = plugin.config

        // データベースが有効かチェック
        enabled = config.getBoolean("database.enabled", false)

        if (!enabled) {
            plugin.logger.info("§e[QuestPlugin] データベースが無効です。YAML保存を使用します")
            return
        }

        val host = config.getString("database.host", "localhost")
        val port = config.getInt("database.port", 3306)
        val database = config.getString("database.database", "minecraft")
        val username = config.getString("database.username", "root")
        val password = config.getString("database.password", "")
        val useSSL = config.getBoolean("database.useSSL", false)

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=$useSSL&allowPublicKeyRetrieval=true&serverTimezone=Asia/Tokyo&characterEncoding=utf8"
            this.username = username
            this.password = password

            // コネクションプール設定
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000

            // パフォーマンス最適化
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")
        }

        try {
            dataSource = HikariDataSource(hikariConfig)
            plugin.logger.info("§a[QuestPlugin] MySQL接続成功")

            // テーブル作成
            createTables()
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "§c[QuestPlugin] MySQL接続失敗。YAML保存にフォールバックします", e)
            enabled = false
        }
    }

    /**
     * データベースが有効かどうか
     */
    fun isEnabled(): Boolean = enabled

    /**
     * 接続を取得
     */
    private fun getConnection(): Connection {
        if (!enabled) {
            throw IllegalStateException("Database is not enabled")
        }
        return dataSource.connection
    }

    /**
     * テーブル作成
     */
    private fun createTables() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                // quest_progressテーブル
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS quest_progress (
                        player_uuid VARCHAR(36) NOT NULL,
                        quest_id VARCHAR(50) NOT NULL,
                        current_amount INT DEFAULT 0,
                        started_at BIGINT NOT NULL,
                        death_count INT DEFAULT 0,
                        is_active BOOLEAN DEFAULT TRUE,
                        PRIMARY KEY (player_uuid, quest_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """)

                // quest_usageテーブル（クールダウン・使用回数）
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS quest_usage (
                        player_uuid VARCHAR(36) NOT NULL,
                        quest_id VARCHAR(50) NOT NULL,
                        last_used_time BIGINT DEFAULT 0,
                        used_count INT DEFAULT 0,
                        last_recovery_time BIGINT DEFAULT 0,
                        PRIMARY KEY (player_uuid, quest_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """)

                // quest_historyテーブル
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS quest_history (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        quest_id VARCHAR(50) NOT NULL,
                        quest_name VARCHAR(100),
                        success BOOLEAN NOT NULL,
                        progress INT DEFAULT 0,
                        death_count INT DEFAULT 0,
                        completed_at BIGINT NOT NULL,
                        INDEX idx_player (player_uuid),
                        INDEX idx_completed (completed_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """)
            }
        }
        plugin.logger.info("§a[QuestPlugin] テーブル作成完了")
    }

    /**
     * 接続を閉じる
     */
    fun close() {
        if (enabled && ::dataSource.isInitialized && !dataSource.isClosed) {
            dataSource.close()
            plugin.logger.info("§a[QuestPlugin] データベース接続を閉じました")
        }
    }

    // ==================== クエスト進行状況 ====================

    /**
     * プレイヤーの進行状況を保存（非同期推奨）
     */
    fun saveProgress(uuid: UUID, questId: String, amount: Int, deathCount: Int, startedAt: Long) {
        if (!enabled) return

        try {
            getConnection().use { conn ->
                val sql = """
                    INSERT INTO quest_progress (player_uuid, quest_id, current_amount, started_at, death_count, is_active)
                    VALUES (?, ?, ?, ?, ?, TRUE)
                    ON DUPLICATE KEY UPDATE
                        current_amount = VALUES(current_amount),
                        death_count = VALUES(death_count),
                        is_active = TRUE
                """
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    stmt.setString(2, questId)
                    stmt.setInt(3, amount)
                    stmt.setLong(4, startedAt)
                    stmt.setInt(5, deathCount)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "進行状況保存エラー", e)
        }
    }

    /**
     * プレイヤーの進行状況を取得
     */
    fun loadProgress(uuid: UUID, questId: String): QuestProgressData? {
        if (!enabled) return null

        try {
            getConnection().use { conn ->
                val sql = """
                    SELECT current_amount, started_at, death_count
                    FROM quest_progress
                    WHERE player_uuid = ? AND quest_id = ? AND is_active = TRUE
                """
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    stmt.setString(2, questId)
                    val rs = stmt.executeQuery()

                    return if (rs.next()) {
                        QuestProgressData(
                            currentAmount = rs.getInt("current_amount"),
                            startedAt = rs.getLong("started_at"),
                            deathCount = rs.getInt("death_count")
                        )
                    } else null
                }
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "進行状況読み込みエラー", e)
            return null
        }
    }

    /**
     * プレイヤーのアクティブなクエストをすべて取得
     */
    fun loadActiveQuests(uuid: UUID): List<ActiveQuestData> {
        if (!enabled) return emptyList()

        val result = mutableListOf<ActiveQuestData>()
        try {
            getConnection().use { conn ->
                val sql = """
                    SELECT quest_id, current_amount, started_at, death_count
                    FROM quest_progress
                    WHERE player_uuid = ? AND is_active = TRUE
                """
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    val rs = stmt.executeQuery()

                    while (rs.next()) {
                        result.add(ActiveQuestData(
                            questId = rs.getString("quest_id"),
                            currentAmount = rs.getInt("current_amount"),
                            startedAt = rs.getLong("started_at"),
                            deathCount = rs.getInt("death_count")
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "アクティブクエスト読み込みエラー", e)
        }
        return result
    }

    /**
     * クエストを非アクティブ化（完了・中断時）
     */
    fun deactivateQuest(uuid: UUID, questId: String) {
        if (!enabled) return

        try {
            getConnection().use { conn ->
                val sql = "UPDATE quest_progress SET is_active = FALSE WHERE player_uuid = ? AND quest_id = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    stmt.setString(2, questId)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "クエスト非アクティブ化エラー", e)
        }
    }

    // ==================== 使用回数・クールダウン ====================

    /**
     * 使用状況を保存
     */
    fun saveUsage(uuid: UUID, questId: String, lastUsedTime: Long, usedCount: Int, lastRecoveryTime: Long) {
        if (!enabled) return

        try {
            getConnection().use { conn ->
                val sql = """
                    INSERT INTO quest_usage (player_uuid, quest_id, last_used_time, used_count, last_recovery_time)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        last_used_time = VALUES(last_used_time),
                        used_count = VALUES(used_count),
                        last_recovery_time = VALUES(last_recovery_time)
                """
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    stmt.setString(2, questId)
                    stmt.setLong(3, lastUsedTime)
                    stmt.setInt(4, usedCount)
                    stmt.setLong(5, lastRecoveryTime)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "使用状況保存エラー", e)
        }
    }

    /**
     * 使用状況を取得
     */
    fun loadUsage(uuid: UUID, questId: String): QuestUsageData? {
        if (!enabled) return null

        try {
            getConnection().use { conn ->
                val sql = """
                    SELECT last_used_time, used_count, last_recovery_time
                    FROM quest_usage
                    WHERE player_uuid = ? AND quest_id = ?
                """
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    stmt.setString(2, questId)
                    val rs = stmt.executeQuery()

                    return if (rs.next()) {
                        QuestUsageData(
                            lastUsedTime = rs.getLong("last_used_time"),
                            usedCount = rs.getInt("used_count"),
                            lastRecoveryTime = rs.getLong("last_recovery_time")
                        )
                    } else null
                }
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "使用状況読み込みエラー", e)
            return null
        }
    }

    /**
     * 全プレイヤーの使用状況を取得（回復処理用）
     */
    fun loadAllUsages(): Map<UUID, Map<String, QuestUsageData>> {
        if (!enabled) return emptyMap()

        val result = mutableMapOf<UUID, MutableMap<String, QuestUsageData>>()
        try {
            getConnection().use { conn ->
                val sql = "SELECT player_uuid, quest_id, last_used_time, used_count, last_recovery_time FROM quest_usage"
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery(sql)

                    while (rs.next()) {
                        val uuid = UUID.fromString(rs.getString("player_uuid"))
                        val questId = rs.getString("quest_id")
                        val usage = QuestUsageData(
                            lastUsedTime = rs.getLong("last_used_time"),
                            usedCount = rs.getInt("used_count"),
                            lastRecoveryTime = rs.getLong("last_recovery_time")
                        )

                        result.getOrPut(uuid) { mutableMapOf() }[questId] = usage
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "全使用状況読み込みエラー", e)
        }
        return result
    }

    // ==================== クエスト履歴 ====================

    /**
     * クエスト履歴を保存
     */
    fun saveHistory(
        uuid: UUID,
        questId: String,
        questName: String,
        success: Boolean,
        progress: Int,
        deathCount: Int
    ) {
        if (!enabled) return

        try {
            getConnection().use { conn ->
                val sql = """
                    INSERT INTO quest_history 
                    (player_uuid, quest_id, quest_name, success, progress, death_count, completed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    stmt.setString(2, questId)
                    stmt.setString(3, questName)
                    stmt.setBoolean(4, success)
                    stmt.setInt(5, progress)
                    stmt.setInt(6, deathCount)
                    stmt.setLong(7, System.currentTimeMillis())
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "履歴保存エラー", e)
        }
    }

    /**
     * プレイヤーのクエスト履歴を取得（ページネーション対応）
     */
    fun loadHistory(uuid: UUID, page: Int, pageSize: Int): List<QuestHistoryData> {
        if (!enabled) return emptyList()

        val result = mutableListOf<QuestHistoryData>()
        val offset = (page - 1) * pageSize

        try {
            getConnection().use { conn ->
                val sql = """
                    SELECT quest_id, quest_name, success, progress, death_count, completed_at
                    FROM quest_history
                    WHERE player_uuid = ?
                    ORDER BY completed_at DESC
                    LIMIT ? OFFSET ?
                """
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    stmt.setInt(2, pageSize)
                    stmt.setInt(3, offset)
                    val rs = stmt.executeQuery()

                    while (rs.next()) {
                        result.add(QuestHistoryData(
                            questId = rs.getString("quest_id"),
                            questName = rs.getString("quest_name"),
                            success = rs.getBoolean("success"),
                            progress = rs.getInt("progress"),
                            deathCount = rs.getInt("death_count"),
                            completedAt = rs.getLong("completed_at")
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "履歴読み込みエラー", e)
        }
        return result
    }

    /**
     * プレイヤーの履歴件数を取得
     */
    fun getHistoryCount(uuid: UUID): Int {
        if (!enabled) return 0

        try {
            getConnection().use { conn ->
                val sql = "SELECT COUNT(*) FROM quest_history WHERE player_uuid = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    val rs = stmt.executeQuery()
                    return if (rs.next()) rs.getInt(1) else 0
                }
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "履歴件数取得エラー", e)
            return 0
        }
    }
}

// ==================== データクラス ====================

data class QuestProgressData(
    val currentAmount: Int,
    val startedAt: Long,
    val deathCount: Int
)

data class ActiveQuestData(
    val questId: String,
    val currentAmount: Int,
    val startedAt: Long,
    val deathCount: Int
)

data class QuestUsageData(
    val lastUsedTime: Long,
    val usedCount: Int,
    val lastRecoveryTime: Long
)

data class QuestHistoryData(
    val questId: String,
    val questName: String,
    val success: Boolean,
    val progress: Int,
    val deathCount: Int,
    val completedAt: Long
)