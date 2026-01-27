package com.woxloi.questplugin

import com.shojabon.mcutils.Utils.SScoreboard
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
import java.io.File
import java.util.*

object ActiveQuestManager {
    private val scoreboard = SScoreboard("TEST")

    private val activeQuests = mutableMapOf<UUID, com.woxloi.questplugin.ActiveQuestManager.PlayerQuestData>()

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

    // 追加: プレイヤーごとのクエスト履歴保持マップ
    private val questHistories = mutableMapOf<UUID, MutableList<com.woxloi.questplugin.ActiveQuestManager.QuestHistoryEntry>>()

    // 履歴保存用ファイル & YamlConfiguration
    private val historyFile = File(plugin.dataFolder, "quest_histories.yml")
    private val historyConfig = if (com.woxloi.questplugin.ActiveQuestManager.historyFile.exists()) YamlConfiguration.loadConfiguration(
        com.woxloi.questplugin.ActiveQuestManager.historyFile
    ) else YamlConfiguration()

    object PlayerQuestUsageManager {
        public val usageMap = mutableMapOf<UUID, MutableMap<String, com.woxloi.questplugin.ActiveQuestManager.PlayerQuestUsageManager.UsageData>>()

        data class UsageData(
            var lastUsedTime: Long = 0,
            var usedCount: Int = 0,
            var lastRecoveryTime: Long = System.currentTimeMillis()
        )
        // 死亡を1回カウント（ライフ減少）
        fun addDeath(player: Player) {
            val data = com.woxloi.questplugin.ActiveQuestManager.activeQuests[player.uniqueId] ?: return
            data.deathCount++
            val quest = data.quest

            // 最大ライフを取得（設定されていなければ無制限扱い）
            val maxLives = quest.maxLives ?: return

            val partyMembers = if (quest.partyEnabled) {
                PartyManager.getPartyMembers(player).distinctBy { it.uniqueId }
            } else {
                listOf(player)
            }

            // 全員の最大ライフ合計
            val totalMaxLives = maxLives * partyMembers.size

            // 全員の合計死亡数
            val totalDeaths = partyMembers.sumOf { member ->
                com.woxloi.questplugin.ActiveQuestManager.activeQuests[member.uniqueId]?.deathCount ?: 0
            }

            val remainingLives = (totalMaxLives - totalDeaths).coerceAtLeast(0)

            // ライフ0でクエスト失敗扱いにするならここでキャンセル
            if (remainingLives <= 0) {
                partyMembers.forEach {
                    com.woxloi.questplugin.ActiveQuestManager.cancelQuest(it)
                    it.sendMessage(com.woxloi.questplugin.QuestPlugin.Companion.prefix + "§c§lライフが尽きたためクエスト失敗です ")
                }
            } else {
                // ライフ減少の通知など必要ならここに
            }
        }

        // ライフ残りを取得（合計）
        fun getRemainingLives(player: Player): Int {
            val data = com.woxloi.questplugin.ActiveQuestManager.activeQuests[player.uniqueId] ?: return 0
            val quest = data.quest
            val maxLives = quest.maxLives ?: return Int.MAX_VALUE

            val partyMembers = if (quest.partyEnabled) {

                PartyManager.getPartyMembers(player).distinctBy { it.uniqueId }
            } else {
                listOf(player)
            }

            val totalMaxLives = maxLives * partyMembers.size

            val totalDeaths = partyMembers.sumOf { member ->
                com.woxloi.questplugin.ActiveQuestManager.activeQuests[member.uniqueId]?.deathCount ?: 0
            }

            return (totalMaxLives - totalDeaths).coerceAtLeast(0)
        }
        fun canUseQuest(playerUUID: UUID, quest: com.woxloi.questplugin.QuestData): Boolean {
            val now   = System.currentTimeMillis()
            val usage = com.woxloi.questplugin.ActiveQuestManager.PlayerQuestUsageManager.usageMap
                .getOrPut(playerUUID) { mutableMapOf() }
                .getOrPut(quest.id)   { com.woxloi.questplugin.ActiveQuestManager.PlayerQuestUsageManager.UsageData() }

            val cd   = quest.cooldownSeconds   // null ならクールダウン無し
            val max  = quest.maxUseCount       // null なら回数制限無し

            // --- 回復処理 ----------------------------------------------------------
            if (cd != null) {
                val elapsed    = (now - usage.lastRecoveryTime) / 1000
                val recoveries = (elapsed / cd).toInt()
                if (recoveries > 0) {
                    usage.usedCount        = (usage.usedCount - recoveries).coerceAtLeast(0)
                    usage.lastRecoveryTime += recoveries * cd * 1000
                }
            }
            // ----------------------------------------------------------------------

            // --- 判定 --------------------------------------------------------------
            // 回数制限がある場合は、更新後の usedCount と max を比較
            if (max != null && usage.usedCount >= max) return false

            // クールダウンのみ指定され、前回使用から cd 秒経っていない場合
            if (cd != null && now - usage.lastUsedTime < cd * 1000) return false

            return true
        }


        fun recordQuestUse(playerUUID: UUID, quest: com.woxloi.questplugin.QuestData) {
            val playerUsage = com.woxloi.questplugin.ActiveQuestManager.PlayerQuestUsageManager.usageMap.getOrPut(playerUUID) { mutableMapOf() }
            val usage = playerUsage.getOrPut(quest.id) { com.woxloi.questplugin.ActiveQuestManager.PlayerQuestUsageManager.UsageData() }

            val now = System.currentTimeMillis()

            usage.lastUsedTime = now
            usage.usedCount++
        }



        fun getUsage(playerUUID: UUID, quest: com.woxloi.questplugin.QuestData): com.woxloi.questplugin.ActiveQuestManager.PlayerQuestUsageManager.UsageData {
            val playerUsage = com.woxloi.questplugin.ActiveQuestManager.PlayerQuestUsageManager.usageMap.getOrPut(playerUUID) { mutableMapOf() }
            return playerUsage.getOrPut(quest.id) { com.woxloi.questplugin.ActiveQuestManager.PlayerQuestUsageManager.UsageData() }
        }
    }
    // クエスト履歴を追加する関数
    private fun addQuestHistory(uuid: UUID, data: com.woxloi.questplugin.ActiveQuestManager.PlayerQuestData, success: Boolean) {
        val list = com.woxloi.questplugin.ActiveQuestManager.questHistories.getOrPut(uuid) { mutableListOf() }
        list.add(
            com.woxloi.questplugin.ActiveQuestManager.QuestHistoryEntry(
                questId = data.quest.id,
                questName = data.quest.name,
                completedAt = System.currentTimeMillis(),
                success = success,
                progress = data.progress,
                deathCount = data.deathCount
            )
        )
    }

    // 履歴の保存
    fun saveQuestHistories() {
        val mapToSave = com.woxloi.questplugin.ActiveQuestManager.questHistories.mapKeys { it.key.toString() }  // ここでUUIDを文字列に変換
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
        com.woxloi.questplugin.ActiveQuestManager.historyConfig.set("histories", mapToSave)
        com.woxloi.questplugin.ActiveQuestManager.historyConfig.save(com.woxloi.questplugin.ActiveQuestManager.historyFile)
    }


    // 履歴の読み込み
    fun loadQuestHistories() {
        if (!com.woxloi.questplugin.ActiveQuestManager.historyFile.exists()) return

        val section = com.woxloi.questplugin.ActiveQuestManager.historyConfig.getConfigurationSection("histories") ?: return
        for (uuidStr in section.getKeys(false)) {
            val list = section.getMapList(uuidStr)
            val historyList = mutableListOf<com.woxloi.questplugin.ActiveQuestManager.QuestHistoryEntry>()
            for (map in list) {
                val questId = map["questId"] as? String ?: continue
                val questName = map["questName"] as? String ?: ""
                val completedAt = (map["completedAt"] as? Number)?.toLong() ?: 0L
                val success = map["success"] as? Boolean ?: false
                val progress = (map["progress"] as? Number)?.toInt() ?: 0
                val deathCount = (map["deathCount"] as? Number)?.toInt() ?: 0

                historyList.add(
                    com.woxloi.questplugin.ActiveQuestManager.QuestHistoryEntry(
                        questId,
                        questName,
                        completedAt,
                        success,
                        progress,
                        deathCount
                    )
                )
            }
            com.woxloi.questplugin.ActiveQuestManager.questHistories[UUID.fromString(uuidStr)] = historyList
        }
    }
    fun init() {
        // スポーン位置の定期更新（10秒ごと）
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            for ((uuid, data) in com.woxloi.questplugin.ActiveQuestManager.activeQuests) {
                val player = Bukkit.getPlayer(uuid) ?: continue
                if (player.isOnline && !player.isDead) {
                    // onGround 判定を入れたければ以下に条件追加
                    data.originalLocation = player.location.clone()
                }
            }
        }, 20L * 10, 20L * 10) // 最初の遅延: 10秒、間隔: 10秒
        com.woxloi.questplugin.ActiveQuestManager.loadQuestHistories()
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            for ((uuid, questMap) in com.woxloi.questplugin.ActiveQuestManager.PlayerQuestUsageManager.usageMap) {
                val player = Bukkit.getPlayer(uuid) ?: continue

                for ((questId, usage) in questMap) {
                    val quest = com.woxloi.questplugin.QuestManager.getQuestById(questId) ?: continue
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
                            player.sendMessage(com.woxloi.questplugin.QuestPlugin.Companion.prefix + "§e§lクエスト「${quest.name}」の使用回数が§b§l${recovered}回§e§l回復しました！")
                            plugin.logger.info("[QuestCooldown] Player ${player.name} quest '${quest.name}': recovered $recovered usages.")
                        }
                    } else {
                        plugin.logger.info("[QuestCooldown] Player ${player.name} quest '${quest.name}': no recovery. elapsed=$elapsed, recoveries=$recoveries")
                    }
                }
            }
        }, 20L * 60, 20L * 60)
    }


    fun shutdown() {
        com.woxloi.questplugin.ActiveQuestManager.activeQuests.values.forEach { data -> data.timer.stop() }
        com.woxloi.questplugin.ActiveQuestManager.activeQuests.clear()
        com.woxloi.questplugin.ActiveQuestManager.saveQuestHistories()
    }

    /**
     * クエスト開始処理
     *  ‑ クールダウン／使用回数の判定は **PlayerQuestUsageManager.canUseQuest** に一任
     *  ‑ 使用不可だった場合のみ、残りクールダウンや残り回数をプレイヤーへ通知
     */
    fun startQuest(player: Player, quest: com.woxloi.questplugin.QuestData): Boolean {

        val uuid = player.uniqueId

        // ───────── 同じクエストが同一パーティーで進行中かチェック ─────────
        if (activeQuests.any { (uuid, data) ->
                data.quest.id == quest.id &&
                        PartyManager.isSameParty(uuid, player.uniqueId)
            }) {
            player.sendMessage(QuestPlugin.prefix + "§c§lこのクエストは、すでにあなたのパーティーで進行中です ")
            return false
        }

        if (!quest.partyEnabled && activeQuests.containsKey(uuid)) {
            player.sendMessage(QuestPlugin.prefix + "§c§lすでにクエストを進行中です ")
            return false
        }

        val partyMembers: List<Player> = if (quest.partyEnabled) {

            val members = PartyManager.getPartyMembers(player)
            if (members.isEmpty()) {
                player.sendMessage(com.woxloi.questplugin.QuestPlugin.Companion.prefix + "§c§lこのクエストはパーティー専用です まずパーティーを作成してください ")
                player.sendMessage(
                    text("§c§l[ここをクリックでパーティーコマンドを自動入力]")
                        .clickEvent(suggestCommand("/quest party create"))
                )
                return false
            }

            quest.partyMaxMembers?.let { limit ->
                if (members.size > limit) {
                    player.sendMessage(com.woxloi.questplugin.QuestPlugin.Companion.prefix + "§c§lこのクエストのパーティー上限は${limit}人です（現在${members.size}人）")
                    return false
                }
            }

            for (member in members) {
                val usage =
                    com.woxloi.questplugin.ActiveQuestManager.PlayerQuestUsageManager.getUsage(member.uniqueId, quest)
                val usable = com.woxloi.questplugin.ActiveQuestManager.PlayerQuestUsageManager.canUseQuest(
                    member.uniqueId,
                    quest
                )

                if (!usable) {
                    quest.cooldownSeconds?.let { cd ->
                        val remaining = cd - ((System.currentTimeMillis() - usage.lastUsedTime) / 1000)
                        if (remaining > 0) {
                            member.sendMessage(com.woxloi.questplugin.QuestPlugin.Companion.prefix + "§c§lクールダウン中です あと §e§l$remaining 秒§c§lお待ちください ")
                        }
                    }
                    quest.maxUseCount?.let { maxCount ->
                        if (usage.usedCount >= maxCount) {
                            player.sendMessage(com.woxloi.questplugin.QuestPlugin.Companion.prefix + "§c§lこのクエストは最大使用回数に達しています ")
                            return false
                        }
                    }

                    return false
                }
            }

            quest.maxUseCount?.let { max ->
                val cd = quest.cooldownSeconds
                for (member in members) {
                    val usage = com.woxloi.questplugin.ActiveQuestManager.PlayerQuestUsageManager.getUsage(
                        member.uniqueId,
                        quest
                    )
                    val remaining = (max - usage.usedCount).coerceAtLeast(0)
                    val cooldownMsg = if (cd != null) "（${cd}秒ごとに1回復）" else ""
                    member.sendMessage(com.woxloi.questplugin.QuestPlugin.Companion.prefix + "§a§lこのクエストはストック制です 残り使用可能回数: §e§l$remaining / $max §a§l$cooldownMsg")
                }
            }

            members.forEach {
                com.woxloi.questplugin.ActiveQuestManager.PlayerQuestUsageManager.recordQuestUse(
                    it.uniqueId,
                    quest
                )
            }
            members

        } else {
            val usage = com.woxloi.questplugin.ActiveQuestManager.PlayerQuestUsageManager.getUsage(uuid, quest)
            val usable = com.woxloi.questplugin.ActiveQuestManager.PlayerQuestUsageManager.canUseQuest(uuid, quest)

            if (!usable) {
                quest.cooldownSeconds?.let { cd ->
                    val remaining = cd - ((System.currentTimeMillis() - usage.lastUsedTime) / 1000)
                    if (remaining > 0) {
                        player.sendMessage(com.woxloi.questplugin.QuestPlugin.Companion.prefix + "§c§lクールダウン中です あと §e§l$remaining 秒§c§lお待ちください ")
                    }
                }
                quest.maxUseCount?.let { maxCount ->
                    if (usage.usedCount >= maxCount) {
                        player.sendMessage(com.woxloi.questplugin.QuestPlugin.Companion.prefix + "§c§lこのクエストは最大使用回数に達しています ")
                        return false
                    }
                }

                return false
            }

            quest.maxUseCount?.let { max ->
                val remaining = (max - usage.usedCount).coerceAtLeast(0)
                val cd = quest.cooldownSeconds
                val cooldownMsg = if (cd != null) "（${cd}秒ごとに1回復）" else ""
                player.sendMessage(com.woxloi.questplugin.QuestPlugin.Companion.prefix + "§a§lこのクエストはストック制です 残り使用可能回数: §e§l$remaining / $max §a§l$cooldownMsg")
            }

            com.woxloi.questplugin.ActiveQuestManager.PlayerQuestUsageManager.recordQuestUse(uuid, quest)
            listOf(player)
        }

        val bossBar = com.woxloi.questplugin.ActiveQuestManager.createBossBar(quest)
        val timer = com.woxloi.questplugin.utils.STimer()
        val start = System.currentTimeMillis()

        quest.timeLimitSeconds?.let { limit ->
            timer.setRemainingTime(limit.toInt())
            timer.linkBossBar(bossBar, true)
            timer.addOnEndEvent {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    partyMembers.forEach {
                        com.woxloi.questplugin.ActiveQuestManager.cancelQuest(it)
                        it.sendMessage(com.woxloi.questplugin.QuestPlugin.Companion.prefix + "§c§l制限時間内にクエストを達成できませんでした ")
                    }
                })
            }
        }

        timer.addOnIntervalEvent { sec ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                partyMembers.forEach { p ->
                    com.woxloi.questplugin.ActiveQuestManager.activeQuests[p.uniqueId]?.let { data ->
                        data.questScoreboard.updateProgress(data.progress)
                        data.questScoreboard.updateRemainingTime(sec.toLong())
                    }
                }
            })
        }

        timer.start()

        for (member in partyMembers) {
            // スコアボード表示
            val board = com.woxloi.questplugin.QuestScoreboard(member, quest).apply { show() }

            // インベントリをファイルに保存
            val file = File(plugin.dataFolder, "${member.uniqueId}_inv.yml")
            com.woxloi.questplugin.utils.ItemBackup.saveInventoryToFile(member, file)

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:clear ${member.name}")
            plugin.logger.info("[QuestPlugin] プレイヤー全員の持ち物をクリアしました")

            com.woxloi.questplugin.ActiveQuestManager.activeQuests[member.uniqueId] =
                com.woxloi.questplugin.ActiveQuestManager.PlayerQuestData(
                    quest = quest,
                    startTime = start,
                    progress = 0,
                    bossBar = bossBar,
                    timer = timer,
                    questScoreboard = board,
                    deathCount = 0,
                    originalLocation = member.location.clone(),
                    // もうメモリ上の InventoryBackup は不要なので null にする
                    inventoryBackup = null,
                )

            bossBar.addPlayer(member)

            quest.startCommands.forEach { cmd ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", member.name))
            }

            if (quest.teleportWorld != null &&
                quest.teleportX != null && quest.teleportY != null && quest.teleportZ != null
            ) {
               Bukkit.getWorld(quest.teleportWorld!!)?.let { w ->
                   val loc = Location(w, quest.teleportX!!, quest.teleportY!!, quest.teleportZ!!)
                   member.teleport(loc)
                   member.sendMessage(com.woxloi.questplugin.QuestPlugin.Companion.prefix + "§a§lクエスト開始地点へテレポートしました ")
                   plugin.logger.info("[QuestTeleport] Player ${member.name} quest ${quest.name} ${quest.teleportWorld} ${quest.teleportX} ${quest.teleportY} ${quest.teleportZ} へテレポート完了")
               }
            }

            com.woxloi.questplugin.ActiveQuestManager.updateBossBar(member)
        }

        return true
    }

    fun cancelQuest(player: Player) {
        val uuid = player.uniqueId
        val data = com.woxloi.questplugin.ActiveQuestManager.activeQuests[uuid] ?: return

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

        // それから全員のクエスト状態を削除＆処理
        for (member in partyMembers) {
            val memberData = com.woxloi.questplugin.ActiveQuestManager.activeQuests.remove(member.uniqueId)
            if (memberData != null) {
                memberData.bossBar.removePlayer(member)
                if (member.uniqueId == member.uniqueId) {
                    memberData.timer.stop()
                }
                memberData.questScoreboard.hide()
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    member.gameMode = GameMode.SURVIVAL
                })
                com.woxloi.questplugin.ActiveQuestManager.addQuestHistory(member.uniqueId, memberData, false)
            }
        }

        com.woxloi.questplugin.ActiveQuestManager.saveQuestHistories()

        for (member in partyMembers) {
            member.health = 0.0
        }

        if (PartyManager.disbandParty(player)) {
            player.sendMessage(com.woxloi.questplugin.QuestPlugin.Companion.prefix + "§a§lクエストが終了とともにパーティーが解散されました")
        } else {
            player.sendMessage(com.woxloi.questplugin.QuestPlugin.Companion.prefix + "§c§lパーティー解散に失敗しました")
        }
    }


    fun completeQuest(player: Player) {
        val uuid = player.uniqueId
        val data = com.woxloi.questplugin.ActiveQuestManager.activeQuests[uuid] ?: return

        val partyMembers = if (data.quest.partyEnabled) {
            PartyManager.getPartyMembers(player).distinctBy { it.uniqueId }
        } else {
            listOf(player)
        }

        // 全員のインベントリをファイルから復元
        for (member in partyMembers) {
            val file = File(plugin.dataFolder, "${member.uniqueId}_inv.yml")
            if (file.exists()) {
                ItemBackup.loadInventoryFromFile(member, file)
                file.delete()
            }
        }

        val leader = partyMembers.firstOrNull()

        // 全員のクエスト状態を削除＆処理
        for (member in partyMembers) {
            val memberData = com.woxloi.questplugin.ActiveQuestManager.activeQuests.remove(member.uniqueId)
            if (memberData != null) {
                memberData.bossBar.removePlayer(member)
                if (leader?.uniqueId == member.uniqueId) {
                    memberData.timer.stop()
                }
                memberData.questScoreboard.hide()

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    member.gameMode = GameMode.SURVIVAL
                })

                com.woxloi.questplugin.ActiveQuestManager.addQuestHistory(member.uniqueId, memberData, true)
                member.sendMessage(com.woxloi.questplugin.QuestPlugin.Companion.prefix + "§a§lクエスト${memberData.quest.name}をクリアしました！")

                for (cmd in memberData.quest.rewards) {
                    val command = cmd.replace("%player%", member.name)
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                }
            }
        }

        com.woxloi.questplugin.ActiveQuestManager.saveQuestHistories()

        if (PartyManager.disbandParty(player)) {
            player.sendMessage(com.woxloi.questplugin.QuestPlugin.Companion.prefix + "§a§lクエストが終了とともにパーティーが解散されました")
        } else {
            player.sendMessage(com.woxloi.questplugin.QuestPlugin.Companion.prefix + "§c§lパーティー解散に失敗しました ")
        }
    }


    fun addProgress(player: Player, amount: Int = 1) {
        val uuid = player.uniqueId
        val data = com.woxloi.questplugin.ActiveQuestManager.activeQuests[uuid] ?: return

        if (data.quest.partyEnabled && data.quest.shareProgress) {
            val members = PartyManager.getPartyMembers(player)
            for (member in members) {
                com.woxloi.questplugin.ActiveQuestManager.addProgressIndividual(member, amount)
            }
        } else {
            com.woxloi.questplugin.ActiveQuestManager.addProgressIndividual(player, amount)
        }
    }

    private fun addProgressIndividual(player: Player, amount: Int) {
        val uuid = player.uniqueId
        val data = com.woxloi.questplugin.ActiveQuestManager.activeQuests[uuid] ?: return
        data.progress += amount
        if (data.progress >= data.quest.amount) {
            com.woxloi.questplugin.ActiveQuestManager.completeQuest(player)
        } else {
            com.woxloi.questplugin.ActiveQuestManager.updateBossBar(player)
        }
    }

    private fun getActionVerb(type: com.woxloi.questplugin.QuestType): String {
        return when (type) {
            com.woxloi.questplugin.QuestType.KILL -> "倒す"
            com.woxloi.questplugin.QuestType.COLLECT -> "集める"
            com.woxloi.questplugin.QuestType.TRAVEL -> "訪れる"
            com.woxloi.questplugin.QuestType.MINE -> "掘る"
            com.woxloi.questplugin.QuestType.PLACE -> "設置する"
            com.woxloi.questplugin.QuestType.BREAK -> "壊す"
            else -> "達成する"
        }
    }

    private fun createBossBar(quest: com.woxloi.questplugin.QuestData): BossBar {
        val action = com.woxloi.questplugin.ActiveQuestManager.getActionVerb(quest.type)
        val title = "§e${quest.name} §7- ${quest.type.displayName} §f- §b${quest.target} を ${quest.amount} 個 $action"
        return Bukkit.createBossBar(title, BarColor.GREEN, BarStyle.SOLID)
    }

    private fun updateBossBar(player: Player) {
        val uuid = player.uniqueId
        val data = com.woxloi.questplugin.ActiveQuestManager.activeQuests[uuid] ?: return

        val action = com.woxloi.questplugin.ActiveQuestManager.getActionVerb(data.quest.type)
        val progressPercent = data.progress.toDouble() / data.quest.amount
        val progress = progressPercent.coerceIn(0.0, 1.0).toFloat()

        data.bossBar.progress = progress.toDouble()

        val barName = "§e${data.quest.name} §7- ${data.quest.type.displayName}" +
                "§b${data.quest.target} を ${data.progress} / ${data.quest.amount} $action"
        data.bossBar.setTitle(barName)
    }

    fun isQuesting(player: Player): Boolean {
        return com.woxloi.questplugin.ActiveQuestManager.activeQuests.containsKey(player.uniqueId)
    }

    fun getQuest(player: Player): com.woxloi.questplugin.QuestData? {
        return com.woxloi.questplugin.ActiveQuestManager.activeQuests[player.uniqueId]?.quest
    }
    fun getPlayerData(uuid: UUID): com.woxloi.questplugin.ActiveQuestManager.PlayerQuestData? {
        return com.woxloi.questplugin.ActiveQuestManager.activeQuests[uuid]
    }
}
