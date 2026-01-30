package com.woxloi.questplugin.manager

import com.woxloi.questplugin.QuestPlugin
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent.suggestCommand
import com.woxloi.questplugin.utils.STimer
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import com.woxloi.questplugin.QuestPlugin.Companion.plugin
import com.woxloi.questplugin.party.PartyManager
import com.woxloi.questplugin.utils.ItemBackup
import com.woxloi.questplugin.database.DatabaseManager
import com.woxloi.questplugin.model.QuestData
import com.woxloi.questplugin.model.QuestType
import java.io.File
import java.util.*
import java.util.logging.Level

object ActiveQuestManager {

    private val activeQuests = mutableMapOf<UUID, PlayerQuestData>()

    data class PlayerQuestData(
        val quest: QuestData,
        val startTime: Long,
        var progress: Int = 0,
        val bossBar: BossBar,
        val timer: STimer,
        val questScoreboard: QuestScoreboard,
        var deathCount: Int = 0,
        var originalLocation: Location,
        var inventoryBackup: ItemBackup.InventoryBackup?
    )

    data class QuestHistoryEntry(
        val questId: String,
        val questName: String,
        val completedAt: Long,
        val success: Boolean,
        val progress: Int,
        val deathCount: Int
    )

    // YAML用の履歴保持マップ
    private val questHistories = mutableMapOf<UUID, MutableList<QuestHistoryEntry>>()

    // YAML履歴保存用ファイル
    private val historyFile = File(plugin.dataFolder, "quest_histories.yml")
    private val historyConfig = if (historyFile.exists()) YamlConfiguration.loadConfiguration(historyFile) else YamlConfiguration()

    object PlayerQuestUsageManager {
        public val usageMap = mutableMapOf<UUID, MutableMap<String, UsageData>>()

        data class UsageData(
            var lastUsedTime: Long = 0,
            var usedCount: Int = 0,
            var lastRecoveryTime: Long = System.currentTimeMillis()
        )

        fun addDeath(player: Player) {
            val data = activeQuests[player.uniqueId] ?: return
            data.deathCount++
            val quest = data.quest

            // データベースに保存（非同期）
            saveProgressAsync(player)

            val maxLives = quest.maxLives ?: return

            val partyMembers = if (quest.partyEnabled) {
                PartyManager.getPartyMembers(player).distinctBy { it.uniqueId }
            } else {
                listOf(player)
            }

            val totalMaxLives = maxLives * partyMembers.size
            val totalDeaths = partyMembers.sumOf { member ->
                activeQuests[member.uniqueId]?.deathCount ?: 0
            }

            val remainingLives = (totalMaxLives - totalDeaths).coerceAtLeast(0)

            if (remainingLives <= 0) {
                partyMembers.forEach {
                    cancelQuest(it)
                    it.sendMessage(QuestPlugin.prefix + "§c§lライフが尽きたためクエスト失敗です")
                }
            }
        }

        fun getRemainingLives(player: Player): Int {
            val data = activeQuests[player.uniqueId] ?: return 0
            val quest = data.quest
            val maxLives = quest.maxLives ?: return Int.MAX_VALUE

            val partyMembers = if (quest.partyEnabled) {
                PartyManager.getPartyMembers(player).distinctBy { it.uniqueId }
            } else {
                listOf(player)
            }

            val totalMaxLives = maxLives * partyMembers.size
            val totalDeaths = partyMembers.sumOf { member ->
                activeQuests[member.uniqueId]?.deathCount ?: 0
            }

            return (totalMaxLives - totalDeaths).coerceAtLeast(0)
        }

        fun canUseQuest(playerUUID: UUID, quest: QuestData): Boolean {
            val now = System.currentTimeMillis()
            val usage = usageMap
                .getOrPut(playerUUID) { mutableMapOf() }
                .getOrPut(quest.id) { UsageData() }

            val cd = quest.cooldownSeconds
            val max = quest.maxUseCount

            // 回復処理
            if (cd != null) {
                val elapsed = (now - usage.lastRecoveryTime) / 1000
                val recoveries = (elapsed / cd).toInt()
                if (recoveries > 0) {
                    usage.usedCount = (usage.usedCount - recoveries).coerceAtLeast(0)
                    usage.lastRecoveryTime += recoveries * cd * 1000

                    // データベースに保存
                    saveUsageAsync(playerUUID, quest.id, usage)
                }
            }

            // 判定
            if (max != null && usage.usedCount >= max) return false
            if (cd != null && now - usage.lastUsedTime < cd * 1000) return false

            return true
        }

        fun recordQuestUse(playerUUID: UUID, quest: QuestData) {
            val playerUsage = usageMap.getOrPut(playerUUID) { mutableMapOf() }
            val usage = playerUsage.getOrPut(quest.id) { UsageData() }

            val now = System.currentTimeMillis()
            usage.lastUsedTime = now
            usage.usedCount++

            // データベースに保存（非同期）
            saveUsageAsync(playerUUID, quest.id, usage)
        }

        fun getUsage(playerUUID: UUID, quest: QuestData): UsageData {
            val playerUsage = usageMap.getOrPut(playerUUID) { mutableMapOf() }
            return playerUsage.getOrPut(quest.id) { UsageData() }
        }

        /**
         * 使用状況をデータベースに非同期保存
         */
        private fun saveUsageAsync(uuid: UUID, questId: String, usage: UsageData) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                try {
                    DatabaseManager.saveUsage(
                        uuid,
                        questId,
                        usage.lastUsedTime,
                        usage.usedCount,
                        usage.lastRecoveryTime
                    )
                } catch (e: Exception) {
                    plugin.logger.log(Level.WARNING, "使用状況の保存に失敗", e)
                }
            })
        }

        /**
         * データベースから使用状況を読み込み
         */
        fun loadAllUsagesFromDatabase() {
            if (!DatabaseManager.isEnabled()) return

            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                try {
                    val dbUsages = DatabaseManager.loadAllUsages()

                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        for ((uuid, questMap) in dbUsages) {
                            val playerUsageMap = usageMap.getOrPut(uuid) { mutableMapOf() }

                            for ((questId, dbUsage) in questMap) {
                                playerUsageMap[questId] = UsageData(
                                    lastUsedTime = dbUsage.lastUsedTime,
                                    usedCount = dbUsage.usedCount,
                                    lastRecoveryTime = dbUsage.lastRecoveryTime
                                )
                            }
                        }
                        plugin.logger.info("§a[QuestPlugin] 使用状況をデータベースから読み込みました")
                    })
                } catch (e: Exception) {
                    plugin.logger.log(Level.WARNING, "使用状況の読み込みに失敗", e)
                }
            })
        }
    }

    /**
     * クエスト履歴を追加（MySQL/YAML両対応）
     */
    private fun addQuestHistory(uuid: UUID, data: PlayerQuestData, success: Boolean) {
        // データベースに保存（非同期）
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                DatabaseManager.saveHistory(
                    uuid,
                    data.quest.id,
                    data.quest.name,
                    success,
                    data.progress,
                    data.deathCount
                )
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "履歴のDB保存に失敗。YAMLに保存します", e)
                // フォールバック: YAML保存
                addQuestHistoryToYaml(uuid, data, success)
            }
        })

        // YAMLにも保存（MySQL無効時やバックアップ用）
        if (!DatabaseManager.isEnabled()) {
            addQuestHistoryToYaml(uuid, data, success)
        }
    }

    /**
     * YAML用の履歴追加
     */
    private fun addQuestHistoryToYaml(uuid: UUID, data: PlayerQuestData, success: Boolean) {
        val list = questHistories.getOrPut(uuid) { mutableListOf() }
        list.add(
            QuestHistoryEntry(
                questId = data.quest.id,
                questName = data.quest.name,
                completedAt = System.currentTimeMillis(),
                success = success,
                progress = data.progress,
                deathCount = data.deathCount
            )
        )
    }

    /**
     * YAML履歴の保存
     */
    fun saveQuestHistories() {
        if (DatabaseManager.isEnabled()) {
            // データベース使用時はYAML保存をスキップ
            return
        }

        val mapToSave = questHistories.mapKeys { it.key.toString() }
            .mapValues { entry ->
                entry.value.map {
                    mapOf(
                        "questId" to it.questId,
                        "questName" to it.questName,
                        "completedAt" to it.completedAt,
                        "success" to it.success,
                        "progress" to it.progress,
                        "deathCount" to it.deathCount
                    )
                }
            }
        historyConfig.set("histories", mapToSave)
        historyConfig.save(historyFile)
    }

    /**
     * YAML履歴の読み込み
     */
    fun loadQuestHistories() {
        if (DatabaseManager.isEnabled()) {
            // データベース使用時はYAML読み込みをスキップ
            plugin.logger.info("§a[QuestPlugin] データベースを使用するため、YAML履歴の読み込みをスキップします")
            return
        }

        if (!historyFile.exists()) return

        val section = historyConfig.getConfigurationSection("histories") ?: return
        for (uuidStr in section.getKeys(false)) {
            val list = section.getMapList(uuidStr)
            val historyList = mutableListOf<QuestHistoryEntry>()
            for (map in list) {
                val questId = map["questId"] as? String ?: continue
                val questName = map["questName"] as? String ?: ""
                val completedAt = (map["completedAt"] as? Number)?.toLong() ?: 0L
                val success = map["success"] as? Boolean ?: false
                val progress = (map["progress"] as? Number)?.toInt() ?: 0
                val deathCount = (map["deathCount"] as? Number)?.toInt() ?: 0

                historyList.add(
                    QuestHistoryEntry(
                        questId,
                        questName,
                        completedAt,
                        success,
                        progress,
                        deathCount
                    )
                )
            }
            questHistories[UUID.fromString(uuidStr)] = historyList
        }
    }

    /**
     * プレイヤーの進行状況を非同期保存
     */
    private fun saveProgressAsync(player: Player) {
        val data = activeQuests[player.uniqueId] ?: return

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                DatabaseManager.saveProgress(
                    player.uniqueId,
                    data.quest.id,
                    data.progress,
                    data.deathCount,
                    data.startTime
                )
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "進行状況の保存に失敗", e)
            }
        })
    }

    fun init() {
        // スポーン位置の定期更新（10秒ごと）
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            for ((uuid, data) in activeQuests) {
                val player = Bukkit.getPlayer(uuid) ?: continue
                if (player.isOnline && !player.isDead) {
                    data.originalLocation = player.location.clone()
                }
            }
        }, 20L * 10, 20L * 10)

        // 履歴読み込み
        loadQuestHistories()

        // データベースから使用状況を読み込み
        PlayerQuestUsageManager.loadAllUsagesFromDatabase()

        // クールダウン回復処理（1分ごと）
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            for ((uuid, questMap) in PlayerQuestUsageManager.usageMap) {
                val player = Bukkit.getPlayer(uuid) ?: continue

                for ((questId, usage) in questMap) {
                    val quest = QuestManager.getQuestById(questId) ?: continue
                    val cd = quest.cooldownSeconds ?: continue

                    val now = System.currentTimeMillis()
                    val elapsed = (now - usage.lastRecoveryTime) / 1000
                    val recoveries = (elapsed / cd).toInt()

                    if (recoveries > 0) {
                        val before = usage.usedCount
                        usage.usedCount = (usage.usedCount - recoveries).coerceAtLeast(0)
                        usage.lastRecoveryTime += recoveries * cd * 1000

                        val recovered = before - usage.usedCount
                        if (recovered > 0) {
                            player.sendMessage(QuestPlugin.prefix + "§e§l${quest.name}の使用回数が§b§l${recovered}回§e§l回復しました！")
                            plugin.logger.info("[QuestCooldown] Player ${player.name} quest '${quest.name}': recovered $recovered usages.")

                            // データベースに保存
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                                DatabaseManager.saveUsage(uuid, questId, usage.lastUsedTime, usage.usedCount, usage.lastRecoveryTime)
                            })
                        }
                    }
                }
            }
        }, 20L * 60, 20L * 60)

        // 定期的に進行状況を保存（5分ごと）
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            for ((uuid, data) in activeQuests) {
                val player = Bukkit.getPlayer(uuid) ?: continue
                saveProgressAsync(player)
            }
        }, 20L * 60 * 5, 20L * 60 * 5)
    }

    fun shutdown() {
        activeQuests.values.forEach { data -> data.timer.stop() }

        // 全プレイヤーの進行状況を保存
        for ((uuid, data) in activeQuests) {
            val player = Bukkit.getPlayer(uuid)
            if (player != null) {
                // 同期的に保存（シャットダウン時）
                DatabaseManager.saveProgress(
                    uuid,
                    data.quest.id,
                    data.progress,
                    data.deathCount,
                    data.startTime
                )
            }
        }

        activeQuests.clear()
        saveQuestHistories()
    }

    fun startQuest(player: Player, quest: QuestData): Boolean {
        val uuid = player.uniqueId

        // 同じクエストが同一パーティーで進行中かチェック
        if (activeQuests.any { (uuid, data) ->
                data.quest.id == quest.id && PartyManager.isSameParty(uuid, player.uniqueId)
            }) {
            player.sendMessage(QuestPlugin.prefix + "§c§lこのクエストは、すでにあなたのパーティーで進行中です")
            return false
        }

        if (!quest.partyEnabled && activeQuests.containsKey(uuid)) {
            player.sendMessage(QuestPlugin.prefix + "§c§lすでにクエストを進行中です")
            return false
        }

        val partyMembers: List<Player> = if (quest.partyEnabled) {
            val members = PartyManager.getPartyMembers(player)
            if (members.isEmpty()) {
                player.sendMessage(QuestPlugin.prefix + "§c§lこのクエストはパーティー専用です まずパーティーを作成してください")
                player.sendMessage(text("§c§l[ここをクリックでパーティーコマンドを自動入力]").clickEvent(suggestCommand("/quest party create")))
                return false
            }

            quest.partyMaxMembers?.let { limit ->
                if (members.size > limit) {
                    player.sendMessage(QuestPlugin.prefix + "§c§lこのクエストのパーティー上限は${limit}人です（現在${members.size}人）")
                    return false
                }
            }

            for (member in members) {
                val usage = PlayerQuestUsageManager.getUsage(member.uniqueId, quest)
                val usable = PlayerQuestUsageManager.canUseQuest(member.uniqueId, quest)

                if (!usable) {
                    quest.cooldownSeconds?.let { cd ->
                        val remaining = cd - ((System.currentTimeMillis() - usage.lastUsedTime) / 1000)
                        if (remaining > 0) {
                            member.sendMessage(QuestPlugin.prefix + "§c§lクールダウン中です あと §e§l${remaining}秒§c§lお待ちください")
                        }
                    }
                    quest.maxUseCount?.let { maxCount ->
                        if (usage.usedCount >= maxCount) {
                            player.sendMessage(QuestPlugin.prefix + "§c§lこのクエストは最大使用回数に達しています")
                            return false
                        }
                    }
                    return false
                }
            }

            quest.maxUseCount?.let { max ->
                val cd = quest.cooldownSeconds
                for (member in members) {
                    val usage = PlayerQuestUsageManager.getUsage(member.uniqueId, quest)
                    val remaining = (max - usage.usedCount).coerceAtLeast(0)
                    val cooldownMsg = if (cd != null) "（${cd}秒ごとに1回復）" else ""
                    member.sendMessage(QuestPlugin.prefix + "§a§lこのクエストはストック制です 残り使用可能回数: §e§l${remaining}/${max}§a§l${cooldownMsg}")
                }
            }

            members.forEach { PlayerQuestUsageManager.recordQuestUse(it.uniqueId, quest) }
            members
        } else {
            val usage = PlayerQuestUsageManager.getUsage(uuid, quest)
            val usable = PlayerQuestUsageManager.canUseQuest(uuid, quest)

            if (!usable) {
                quest.cooldownSeconds?.let { cd ->
                    val remaining = cd - ((System.currentTimeMillis() - usage.lastUsedTime) / 1000)
                    if (remaining > 0) {
                        player.sendMessage(QuestPlugin.prefix + "§c§lクールダウン中です あと§e§l${remaining}秒§c§lお待ちください")
                    }
                }
                quest.maxUseCount?.let { maxCount ->
                    if (usage.usedCount >= maxCount) {
                        player.sendMessage(QuestPlugin.prefix + "§c§lこのクエストは最大使用回数に達しています")
                        return false
                    }
                }
                return false
            }

            quest.maxUseCount?.let { max ->
                val remaining = (max - usage.usedCount).coerceAtLeast(0)
                val cd = quest.cooldownSeconds
                val cooldownMsg = if (cd != null) "（${cd}秒ごとに1回復）" else ""
                player.sendMessage(QuestPlugin.prefix + "§a§lこのクエストはストック制です 残り使用可能回数: §e§l${remaining}/${max}§a§l${cooldownMsg}")
            }

            PlayerQuestUsageManager.recordQuestUse(uuid, quest)
            listOf(player)
        }

        val bossBar = createBossBar(quest)
        val timer = STimer()
        val start = System.currentTimeMillis()

        quest.timeLimitSeconds?.let { limit ->
            timer.setRemainingTime(limit.toInt())
            timer.linkBossBar(bossBar, true)
            timer.addOnEndEvent {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    partyMembers.forEach {
                        cancelQuest(it)
                        it.sendMessage(QuestPlugin.prefix + "§c§l時間制限が来てしまった...")
                    }
                })
            }
        }

        timer.addOnIntervalEvent { sec ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                partyMembers.forEach { p ->
                    activeQuests[p.uniqueId]?.let { data ->
                        data.questScoreboard.updateProgress(data.progress)
                        data.questScoreboard.updateRemainingTime(sec.toLong())
                    }
                }
            })
        }

        timer.start()

        for (member in partyMembers) {
            val board = QuestScoreboard(member, quest).apply { show() }

            val file = File(plugin.dataFolder, "${member.uniqueId}_inv.yml")
            ItemBackup.saveInventoryToFile(member, file)

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:clear ${member.name}")
            plugin.logger.info("[QuestPlugin] プレイヤー全員の持ち物をクリアしました")

            activeQuests[member.uniqueId] = PlayerQuestData(
                quest = quest,
                startTime = start,
                progress = 0,
                bossBar = bossBar,
                timer = timer,
                questScoreboard = board,
                deathCount = 0,
                originalLocation = member.location.clone(),
                inventoryBackup = null,
            )

            bossBar.addPlayer(member)

            quest.startCommands.forEach { cmd ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", member.name))
            }

            if (quest.teleportWorld != null && quest.teleportX != null && quest.teleportY != null && quest.teleportZ != null) {
                Bukkit.getWorld(quest.teleportWorld!!)?.let { w ->
                    val loc = Location(w, quest.teleportX!!, quest.teleportY!!, quest.teleportZ!!)
                    member.teleport(loc)
                    member.sendMessage(QuestPlugin.prefix + "§a§lクエスト開始地点へテレポートしました")
                    plugin.logger.info("[QuestTeleport] Player ${member.name} quest ${quest.name} ${quest.teleportWorld} ${quest.teleportX} ${quest.teleportY} ${quest.teleportZ} へテレポート完了")
                }
            }

            updateBossBar(member)
        }

        // データベースに保存（非同期）
        partyMembers.forEach { saveProgressAsync(it) }

        return true
    }

    fun cancelQuest(player: Player) {
        val uuid = player.uniqueId
        val data = activeQuests[uuid] ?: return

        val partyMembers = if (data.quest.partyEnabled) {
            PartyManager.getPartyMembers(player).distinctBy { it.uniqueId }
        } else {
            listOf(player)
        }

        for (member in partyMembers) {
            val file = File(plugin.dataFolder, "${member.uniqueId}_inv.yml")
            if (file.exists()) {
                ItemBackup.loadInventoryFromFile(member, file)
                file.delete()
            }
        }

        for (member in partyMembers) {
            val memberData = activeQuests.remove(member.uniqueId)
            if (memberData != null) {
                memberData.bossBar.removePlayer(member)
                if (member.uniqueId == member.uniqueId) {
                    memberData.timer.stop()
                }
                memberData.questScoreboard.hide()
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    member.gameMode = GameMode.SURVIVAL
                })
                addQuestHistory(member.uniqueId, memberData, false)

                // データベースで非アクティブ化
                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    DatabaseManager.deactivateQuest(member.uniqueId, memberData.quest.id)
                })
            }
        }

        saveQuestHistories()

        for (member in partyMembers) {
            member.health = 0.0
        }

        if (PartyManager.disbandParty(player)) {
            player.sendMessage(QuestPlugin.prefix + "§a§lクエストが終了とともにパーティーが解散されました")
        } else {
            player.sendMessage(QuestPlugin.prefix + "§c§lパーティー解散に失敗しました")
        }
    }

    fun completeQuest(player: Player) {
        val uuid = player.uniqueId
        val data = activeQuests[uuid] ?: return

        val partyMembers = if (data.quest.partyEnabled) {
            PartyManager.getPartyMembers(player).distinctBy { it.uniqueId }
        } else {
            listOf(player)
        }

        for (member in partyMembers) {
            val file = File(plugin.dataFolder, "${member.uniqueId}_inv.yml")
            if (file.exists()) {
                ItemBackup.loadInventoryFromFile(member, file)
                file.delete()
            }
        }

        val leader = partyMembers.firstOrNull()

        for (member in partyMembers) {
            val memberData = activeQuests.remove(member.uniqueId)
            if (memberData != null) {
                memberData.bossBar.removePlayer(member)
                if (leader?.uniqueId == member.uniqueId) {
                    memberData.timer.stop()
                }
                memberData.questScoreboard.hide()

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    member.gameMode = GameMode.SURVIVAL
                })

                addQuestHistory(member.uniqueId, memberData, true)
                member.sendMessage(QuestPlugin.prefix + "§a§lクエスト${memberData.quest.name}をクリアしました！")

                for (cmd in memberData.quest.rewards) {
                    val command = cmd.replace("%player%", member.name)
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                }

                // データベースで非アクティブ化
                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    DatabaseManager.deactivateQuest(member.uniqueId, memberData.quest.id)
                })
            }
        }

        saveQuestHistories()

        if (PartyManager.disbandParty(player)) {
            player.sendMessage(QuestPlugin.prefix + "§a§lクエストが終了とともにパーティーが解散されました")
        } else {
            player.sendMessage(QuestPlugin.prefix + "§c§lパーティー解散に失敗しました")
        }
    }

    fun addProgress(player: Player, amount: Int = 1) {
        val uuid = player.uniqueId
        val data = activeQuests[uuid] ?: return

        if (data.quest.partyEnabled && data.quest.shareProgress) {
            val members = PartyManager.getPartyMembers(player)
            for (member in members) {
                addProgressIndividual(member, amount)
            }
        } else {
            addProgressIndividual(player, amount)
        }
    }

    private fun addProgressIndividual(player: Player, amount: Int) {
        val uuid = player.uniqueId
        val data = activeQuests[uuid] ?: return
        data.progress += amount

        // データベースに保存（非同期）
        saveProgressAsync(player)

        if (data.progress >= data.quest.amount) {
            completeQuest(player)
        } else {
            updateBossBar(player)
        }
    }

    private fun getActionVerb(type: QuestType): String {
        return when (type) {
            QuestType.KILL -> "倒す"
            QuestType.COLLECT -> "集める"
            QuestType.TRAVEL -> "訪れる"
            QuestType.MINE -> "掘る"
            QuestType.PLACE -> "設置する"
            QuestType.BREAK -> "壊す"
            else -> "達成する"
        }
    }

    private fun createBossBar(quest: QuestData): BossBar {
        val action = getActionVerb(quest.type)
        val title = "§e${quest.name} §7- ${quest.type.displayName} §f- §b${quest.target} を ${quest.amount} 個 $action"
        return Bukkit.createBossBar(title, BarColor.GREEN, BarStyle.SOLID)
    }

    private fun updateBossBar(player: Player) {
        val uuid = player.uniqueId
        val data = activeQuests[uuid] ?: return

        val action = getActionVerb(data.quest.type)
        val progressPercent = data.progress.toDouble() / data.quest.amount
        val progress = progressPercent.coerceIn(0.0, 1.0).toFloat()

        data.bossBar.progress = progress.toDouble()

        val barName = "§e${data.quest.name} §7- ${data.quest.type.displayName}" +
                "§b${data.quest.target} を ${data.progress} / ${data.quest.amount} $action"
        data.bossBar.setTitle(barName)
    }

    fun isQuesting(player: Player): Boolean {
        return activeQuests.containsKey(player.uniqueId)
    }

    fun getQuest(player: Player): QuestData? {
        return activeQuests[player.uniqueId]?.quest
    }

    fun getPlayerData(uuid: UUID): PlayerQuestData? {
        return activeQuests[uuid]
    }
}