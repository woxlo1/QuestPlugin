package com.woxloi.questplugin.manager

import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.model.PlayerQuest
import com.woxloi.questplugin.model.QuestType
import com.woxloi.questplugin.utils.VaultAPI
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import java.util.Base64

/**
 * プレイヤーが作成できる民間クエスト管理
 */
object PlayerQuestManager {

    private val dataFile = File(QuestPlugin.plugin.dataFolder, "player_quests.yml")
    private val playerQuests = mutableMapOf<String, PlayerQuest>()

    // 作成途中のクエスト（確定前）
    private val draftQuests = mutableMapOf<UUID, PlayerQuest>()

    // プレイヤーが受注中の民間クエスト
    private val acceptedQuests = mutableMapOf<UUID, MutableMap<String, Int>>() // uuid -> questId -> progress

    fun init() {
        load()
    }

    // ==================== 作成フロー ====================

    /**
     * ドラフト作成開始
     */
    fun startDraft(player: Player, title: String): PlayerQuest {
        val draft = PlayerQuest(
            id = "pq_${UUID.randomUUID().toString().replace("-", "").take(8)}",
            creatorUUID = player.uniqueId,
            creatorName = player.name,
            title = title,
            description = "",
            type = QuestType.KILL,
            target = "",
            amount = 1
        )
        draftQuests[player.uniqueId] = draft
        return draft
    }

    fun getDraft(player: Player): PlayerQuest? = draftQuests[player.uniqueId]

    fun clearDraft(player: Player) = draftQuests.remove(player.uniqueId)

    /**
     * ドラフトを確定して掲示板に投稿
     * 報酬金額を事前にデポジットする
     */
    fun publishDraft(player: Player): Result<PlayerQuest> {
        val draft = draftQuests[player.uniqueId]
            ?: return Result.failure(IllegalStateException("作成中のクエストがありません"))

        if (draft.title.isBlank()) return Result.failure(IllegalArgumentException("タイトルが未設定です"))
        if (draft.target.isBlank()) return Result.failure(IllegalArgumentException("ターゲットが未設定です"))

        // 金銭報酬がある場合はデポジット
        if (draft.rewardMoney > 0) {
            val vault = VaultAPI()
            val balance = vault.getBalance(player.uniqueId)
            if (balance < draft.rewardMoney) {
                return Result.failure(IllegalStateException("残高不足です（必要: ${draft.rewardMoney}円、所持: ${balance}円）"))
            }
            vault.silentWithdraw(player.uniqueId, draft.rewardMoney)
            draft.depositPaid = true
        }

        playerQuests[draft.id] = draft
        draftQuests.remove(player.uniqueId)
        save()
        return Result.success(draft)
    }

    // ==================== 受注・進行 ====================

    fun acceptQuest(player: Player, questId: String): Result<PlayerQuest> {
        val quest = playerQuests[questId]
            ?: return Result.failure(IllegalArgumentException("クエストが見つかりません: $questId"))

        if (!quest.isOpen) return Result.failure(IllegalStateException("このクエストは受注停止中です"))
        if (quest.creatorUUID == player.uniqueId) return Result.failure(IllegalStateException("自分のクエストは受注できません"))
        if (quest.currentAcceptors.contains(player.uniqueId)) return Result.failure(IllegalStateException("すでに受注中です"))
        if (quest.completedBy.contains(player.uniqueId)) return Result.failure(IllegalStateException("すでに完了済みです"))
        if (quest.currentAcceptors.size >= quest.maxAcceptors) return Result.failure(IllegalStateException("受注上限に達しています"))

        quest.currentAcceptors.add(player.uniqueId)
        acceptedQuests.getOrPut(player.uniqueId) { mutableMapOf() }[questId] = 0
        save()
        return Result.success(quest)
    }

    fun addProgress(player: Player, questId: String, amount: Int): Boolean {
        val progressMap = acceptedQuests[player.uniqueId] ?: return false
        val current = progressMap[questId] ?: return false
        progressMap[questId] = current + amount
        return true
    }

    fun getProgress(player: Player, questId: String): Int {
        return acceptedQuests[player.uniqueId]?.get(questId) ?: 0
    }

    fun isAccepting(player: Player, questId: String): Boolean {
        return acceptedQuests[player.uniqueId]?.containsKey(questId) == true
    }

    /**
     * クエスト完了処理
     */
    fun completeQuest(player: Player, questId: String): Result<Unit> {
        val quest = playerQuests[questId]
            ?: return Result.failure(IllegalArgumentException("クエストが見つかりません"))

        if (!quest.currentAcceptors.contains(player.uniqueId)) {
            return Result.failure(IllegalStateException("このクエストを受注していません"))
        }

        // 報酬付与
        if (quest.rewardMoney > 0 && quest.depositPaid) {
            val vault = VaultAPI()
            vault.silentDeposit(player.uniqueId, quest.rewardMoney)
            player.sendMessage(QuestPlugin.prefix + "§a§l報酬 §e§l${quest.rewardMoney}円§a§l を受け取りました！")
        }

        quest.rewardItems.forEach { item ->
            player.inventory.addItem(item.clone())
        }
        if (quest.rewardItems.isNotEmpty()) {
            player.sendMessage(QuestPlugin.prefix + "§a§lアイテム報酬を受け取りました！")
        }

        // 完了記録
        quest.completedBy.add(player.uniqueId)
        quest.currentAcceptors.remove(player.uniqueId)
        acceptedQuests[player.uniqueId]?.remove(questId)

        // 最大受注人数に達した場合はクローズ
        if (quest.completedBy.size >= quest.maxAcceptors) {
            quest.isOpen = false
        }

        save()
        return Result.success(Unit)
    }

    fun cancelAccept(player: Player, questId: String) {
        val quest = playerQuests[questId] ?: return
        quest.currentAcceptors.remove(player.uniqueId)
        acceptedQuests[player.uniqueId]?.remove(questId)
        save()
    }

    /**
     * クエスト削除（作成者のみ、受注者がいない場合）
     * デポジットを返金
     */
    fun deleteQuest(player: Player, questId: String): Result<Unit> {
        val quest = playerQuests[questId]
            ?: return Result.failure(IllegalArgumentException("クエストが見つかりません"))

        if (quest.creatorUUID != player.uniqueId && !player.hasPermission("quest.admin")) {
            return Result.failure(IllegalStateException("削除権限がありません"))
        }

        if (quest.currentAcceptors.isNotEmpty()) {
            return Result.failure(IllegalStateException("受注中のプレイヤーがいるため削除できません"))
        }

        // 返金
        if (quest.rewardMoney > 0 && quest.depositPaid) {
            val vault = VaultAPI()
            vault.silentDeposit(quest.creatorUUID, quest.rewardMoney)
            Bukkit.getPlayer(quest.creatorUUID)?.sendMessage(
                QuestPlugin.prefix + "§e§l民間クエスト削除により §a§l${quest.rewardMoney}円§e§l が返金されました"
            )
        }

        playerQuests.remove(questId)
        save()
        return Result.success(Unit)
    }

    // ==================== 取得系 ====================

    fun getQuest(id: String): PlayerQuest? = playerQuests[id]

    fun getAllOpenQuests(): List<PlayerQuest> = playerQuests.values.filter { it.isOpen }

    fun getQuestsByCreator(uuid: UUID): List<PlayerQuest> =
        playerQuests.values.filter { it.creatorUUID == uuid }

    fun getAcceptedQuestIds(player: Player): Set<String> =
        acceptedQuests[player.uniqueId]?.keys ?: emptySet()

    // ==================== 永続化 ====================

    fun save() {
        val config = YamlConfiguration()

        for ((id, quest) in playerQuests) {
            val path = "quests.$id"
            config.set("$path.creatorUUID", quest.creatorUUID.toString())
            config.set("$path.creatorName", quest.creatorName)
            config.set("$path.title", quest.title)
            config.set("$path.description", quest.description)
            config.set("$path.type", quest.type.name)
            config.set("$path.target", quest.target)
            config.set("$path.amount", quest.amount)
            config.set("$path.rewardMoney", quest.rewardMoney)
            config.set("$path.timeLimitSeconds", quest.timeLimitSeconds)
            config.set("$path.createdAt", quest.createdAt)
            config.set("$path.isOpen", quest.isOpen)
            config.set("$path.maxAcceptors", quest.maxAcceptors)
            config.set("$path.depositPaid", quest.depositPaid)
            config.set("$path.currentAcceptors", quest.currentAcceptors.map { it.toString() })
            config.set("$path.completedBy", quest.completedBy.map { it.toString() })

            // アイテム報酬はBase64シリアライズ
            val itemStrings = quest.rewardItems.map { itemToBase64(it) }
            config.set("$path.rewardItems", itemStrings)
        }

        // 受注状況保存
        for ((uuid, questMap) in acceptedQuests) {
            for ((qid, progress) in questMap) {
                config.set("accepted.$uuid.$qid", progress)
            }
        }

        config.save(dataFile)
    }

    fun load() {
        if (!dataFile.exists()) return
        val config = YamlConfiguration.loadConfiguration(dataFile)

        playerQuests.clear()
        acceptedQuests.clear()

        config.getConfigurationSection("quests")?.getKeys(false)?.forEach { id ->
            val s = config.getConfigurationSection("quests.$id") ?: return@forEach
            try {
                val quest = PlayerQuest(
                    id = id,
                    creatorUUID = UUID.fromString(s.getString("creatorUUID")!!),
                    creatorName = s.getString("creatorName") ?: "",
                    title = s.getString("title") ?: "",
                    description = s.getString("description") ?: "",
                    type = QuestType.fromString(s.getString("type") ?: "") ?: QuestType.KILL,
                    target = s.getString("target") ?: "",
                    amount = s.getInt("amount", 1),
                    rewardMoney = s.getDouble("rewardMoney", 0.0),
                    timeLimitSeconds = if (s.contains("timeLimitSeconds")) s.getLong("timeLimitSeconds") else null,
                    createdAt = s.getLong("createdAt", 0L),
                    isOpen = s.getBoolean("isOpen", true),
                    maxAcceptors = s.getInt("maxAcceptors", 1),
                    depositPaid = s.getBoolean("depositPaid", false),
                    currentAcceptors = s.getStringList("currentAcceptors").map { UUID.fromString(it) }.toMutableList(),
                    completedBy = s.getStringList("completedBy").map { UUID.fromString(it) }.toMutableList(),
                    rewardItems = s.getStringList("rewardItems")
                        .mapNotNull { runCatching { base64ToItem(it) }.getOrNull() }
                        .toMutableList()
                )
                playerQuests[id] = quest
            } catch (e: Exception) {
                QuestPlugin.plugin.logger.warning("[PlayerQuest] クエスト読み込みエラー ($id): ${e.message}")
            }
        }

        config.getConfigurationSection("accepted")?.getKeys(false)?.forEach { uuidStr ->
            val uuid = runCatching { UUID.fromString(uuidStr) }.getOrNull() ?: return@forEach
            val map = mutableMapOf<String, Int>()
            config.getConfigurationSection("accepted.$uuidStr")?.getKeys(false)?.forEach { qid ->
                map[qid] = config.getInt("accepted.$uuidStr.$qid", 0)
            }
            if (map.isNotEmpty()) acceptedQuests[uuid] = map
        }

        QuestPlugin.plugin.logger.info("[PlayerQuest] ${playerQuests.size}件の民間クエストを読み込みました")
    }

    private fun itemToBase64(item: ItemStack): String {
        val out = ByteArrayOutputStream()
        BukkitObjectOutputStream(out).use { it.writeObject(item) }
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }

    private fun base64ToItem(base64: String): ItemStack {
        val inp = ByteArrayInputStream(Base64.getDecoder().decode(base64))
        return BukkitObjectInputStream(inp).use { it.readObject() as ItemStack }
    }
}
