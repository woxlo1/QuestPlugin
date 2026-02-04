package com.woxloi.questplugin.features

import com.woxloi.questplugin.manager.QuestConfigManager
import com.woxloi.questplugin.model.QuestData
import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.database.DatabaseManager
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.*

/**
 * クエストチェーン（ストーリークエスト）システム
 */
object QuestChainManager {

    private val chainFile = File(QuestPlugin.plugin.dataFolder, "quest_chains.yml")
    private lateinit var config: YamlConfiguration

    // 全チェーン
    private val chains = mutableMapOf<String, QuestChain>()

    // プレイヤーの進行状況
    private val playerProgress = mutableMapOf<UUID, MutableMap<String, ChainProgress>>()

    fun init() {
        loadChains()
        loadPlayerProgress()
    }

    private fun loadChains() {
        if (!chainFile.exists()) {
            createDefaultChains()
        }

        config = YamlConfiguration.loadConfiguration(chainFile)
        chains.clear()

        val section = config.getConfigurationSection("chains") ?: return

        for (chainId in section.getKeys(false)) {
            val chainSection = section.getConfigurationSection(chainId) ?: continue

            val name = chainSection.getString("name") ?: chainId
            val description = chainSection.getString("description") ?: ""
            val questList = mutableListOf<ChainQuest>()

            val questsSection = chainSection.getConfigurationSection("quests")
            if (questsSection != null) {
                for (questKey in questsSection.getKeys(false)) {
                    val q = questsSection.getConfigurationSection(questKey) ?: continue

                    val questId = q.getString("id") ?: continue
                    val questName = q.getString("name") ?: questId

                    // アンロック条件
                    val unlock = if (q.contains("unlock")) {
                        val unlockSection = q.getConfigurationSection("unlock")
                        if (unlockSection != null) {
                            UnlockCondition(
                                quest = unlockSection.getString("quest"),
                                status = QuestStatus.valueOf(unlockSection.getString("status", "COMPLETED")!!)
                            )
                        } else {
                            null
                        }
                    } else {
                        null
                    }

                    // 分岐
                    val branches = mutableListOf<QuestBranch>()
                    if (q.contains("branches")) {
                        val branchList = q.getMapList("branches")
                        branchList.forEach { branchMap ->
                            branches.add(
                                QuestBranch(
                                    choice = branchMap["choice"] as? String ?: "",
                                    next = branchMap["next"] as? String ?: ""
                                )
                            )
                        }
                    }

                    questList.add(
                        ChainQuest(
                            id = questId,
                            name = questName,
                            unlock = unlock,
                            branches = branches
                        )
                    )
                }
            }

            chains[chainId] = QuestChain(
                id = chainId,
                name = name,
                description = description,
                quests = questList
            )
        }

        QuestPlugin.plugin.logger.info("[QuestChain] ${chains.size}個のチェーンを読み込みました")
    }

    private fun createDefaultChains() {
        chainFile.parentFile.mkdirs()
        chainFile.createNewFile()

        val defaultConfig = YamlConfiguration()

        // サンプルチェーン
        defaultConfig.set("chains.hero_story.name", "§6§l英雄の物語")
        defaultConfig.set("chains.hero_story.description", "村を救う英雄になろう")

        // 第1章
        defaultConfig.set("chains.hero_story.quests.0.id", "chapter1_intro")
        defaultConfig.set("chains.hero_story.quests.0.name", "§e第1章: 始まり")
        defaultConfig.set("chains.hero_story.quests.0.unlock", null)

        // 第2章
        defaultConfig.set("chains.hero_story.quests.1.id", "chapter1_battle")
        defaultConfig.set("chains.hero_story.quests.1.name", "§e第1章: 戦い")
        defaultConfig.set("chains.hero_story.quests.1.unlock.quest", "chapter1_intro")
        defaultConfig.set("chains.hero_story.quests.1.unlock.status", "COMPLETED")

        // 第3章（分岐）
        defaultConfig.set("chains.hero_story.quests.2.id", "chapter2_choice")
        defaultConfig.set("chains.hero_story.quests.2.name", "§e第2章: 選択")
        defaultConfig.set("chains.hero_story.quests.2.unlock.quest", "chapter1_battle")
        defaultConfig.set("chains.hero_story.quests.2.unlock.status", "COMPLETED")

        val branches = listOf(
            mapOf("choice" to "村を守る", "next" to "good_path_1"),
            mapOf("choice" to "力を求める", "next" to "dark_path_1")
        )
        defaultConfig.set("chains.hero_story.quests.2.branches", branches)

        defaultConfig.save(chainFile)
    }

    /**
     * プレイヤーが利用可能なクエストを取得
     */
    fun getAvailableQuests(player: Player, chainId: String? = null): List<ChainQuest> {
        val completed = getCompletedQuests(player)

        val targetChains = if (chainId != null) {
            listOfNotNull(chains[chainId])
        } else {
            chains.values
        }

        return targetChains.flatMap { chain ->
            chain.quests.filter { quest ->
                isUnlocked(quest, completed) && !completed.contains(quest.id)
            }
        }
    }

    /**
     * クエストがアンロックされているかチェック
     */
    private fun isUnlocked(quest: ChainQuest, completed: Set<String>): Boolean {
        val unlock = quest.unlock ?: return true

        return when (unlock.status) {
            QuestStatus.COMPLETED -> completed.contains(unlock.quest)
            QuestStatus.STARTED -> true // 開始済みなら常にアンロック
            QuestStatus.FAILED -> false
        }
    }

    /**
     * プレイヤーの完了済みクエストを取得
     */
    fun getCompletedQuests(player: Player): Set<String> {
        // データベースから取得
        val completed = mutableSetOf<String>()

        if (DatabaseManager.isEnabled()) {
            // MySQLから取得
            val histories = DatabaseManager.loadHistory(player.uniqueId, 1, 1000)
            histories.filter { it.success }.forEach { completed.add(it.questId) }
        } else {
            // YAMLから取得（既存の履歴システム）
            val historyFile = File(QuestPlugin.plugin.dataFolder, "quest_histories.yml")
            if (historyFile.exists()) {
                val config = YamlConfiguration.loadConfiguration(historyFile)
                val histories = config.getConfigurationSection("histories")
                    ?.getMapList(player.uniqueId.toString())

                histories?.forEach { entry ->
                    if (entry["success"] == true) {
                        (entry["questId"] as? String)?.let { completed.add(it) }
                    }
                }
            }
        }

        return completed
    }

    /**
     * チェーンの進行状況を表示
     */
    fun showChainProgress(player: Player, chainId: String) {
        val chain = chains[chainId]
        if (chain == null) {
            player.sendMessage(QuestPlugin.prefix + "§c§l${chainId}は存在しません")
            return
        }

        val completed = getCompletedQuests(player)

        player.sendMessage("§6§l========================================")
        player.sendMessage("§e§l${chain.name}")
        player.sendMessage("§7${chain.description}")
        player.sendMessage("")

        chain.quests.forEachIndexed { index, quest ->
            val questData = QuestConfigManager.getQuest(quest.id)
            val displayName = questData?.name ?: quest.name

            val status = when {
                completed.contains(quest.id) -> "§a✓"
                isUnlocked(quest, completed) -> "§e●"
                else -> "§8○"
            }

            player.sendMessage("$status §f${index + 1}. $displayName")

            // 分岐表示
            if (quest.branches.isNotEmpty() && completed.contains(quest.id)) {
                quest.branches.forEach { branch ->
                    player.sendMessage("  §7→ ${branch.choice}")
                }
            }
        }

        val completedCount = chain.quests.count { completed.contains(it.id) }
        val totalCount = chain.quests.size
        val progress = if (totalCount > 0) (completedCount * 100 / totalCount) else 0

        player.sendMessage("")
        player.sendMessage("§7進行状況: §f${completedCount}/${totalCount} §7(${progress}%)")
        player.sendMessage("§6§l========================================")
    }

    /**
     * 全チェーン一覧を表示
     */
    fun showAllChains(player: Player) {
        player.sendMessage("§6§l======== ストーリークエスト ========")

        if (chains.isEmpty()) {
            player.sendMessage("§7利用可能なストーリーはありません")
            return
        }

        chains.forEach { (id, chain) ->
            val completed = getCompletedQuests(player)
            val total = chain.quests.size
            val done = chain.quests.count { completed.contains(it.id) }
            val progress = if (total > 0) (done * 100 / total) else 0

            player.sendMessage("")
            player.sendMessage("§e§l${chain.name}")
            player.sendMessage("§7${chain.description}")
            player.sendMessage("§7進行度: §f${done}/${total} §7(${progress}%)")
            player.sendMessage("§8/quest chain $id §7で詳細確認")
        }

        player.sendMessage("§6§l===================================")
    }

    /**
     * 次に進めるクエストを提案
     */
    fun suggestNextQuest(player: Player, chainId: String) {
        val available = getAvailableQuests(player, chainId)

        if (available.isEmpty()) {
            player.sendMessage(QuestPlugin.prefix + "§a§l全てのクエストを完了しました！")
            return
        }

        val next = available.first()
        val questData = QuestConfigManager.getQuest(next.id)

        if (questData != null) {
            player.sendMessage(QuestPlugin.prefix + "§e§l次のクエスト: §f${questData.name}")
            player.sendMessage(QuestPlugin.prefix + "§7/quest start ${next.id} で開始")
        }
    }

    /**
     * クエスト完了時の処理（チェーン進行）
     */
    fun onQuestComplete(player: Player, questId: String) {
        // どのチェーンに属しているか確認
        chains.forEach { (chainId, chain) ->
            val quest = chain.quests.find { it.id == questId }
            if (quest != null) {
                // 分岐がある場合
                if (quest.branches.isNotEmpty()) {
                    showBranchChoice(player, quest, chain)
                } else {
                    // 次のクエストを提案
                    suggestNextQuest(player, chainId)
                }
            }
        }
    }

    /**
     * 分岐選択を表示
     */
    private fun showBranchChoice(player: Player, quest: ChainQuest, chain: QuestChain) {
        player.sendMessage("")
        player.sendMessage("§6§l========== 選択してください ==========")
        player.sendMessage("§e§l${quest.name}を完了しました")
        player.sendMessage("")

        quest.branches.forEach { branch ->
            player.sendMessage(
                net.kyori.adventure.text.Component.text("§a§l[${branch.choice}]")
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/quest start ${branch.next}"))
            )
        }

        player.sendMessage("§6§l=====================================")
    }

    /**
     * プレイヤー進行状況の保存
     */
    private fun loadPlayerProgress() {
        // TODO: データベースから読み込み
    }

    fun savePlayerProgress() {
        // TODO: データベースに保存
    }
}

/**
 * データクラス
 */
data class QuestChain(
    val id: String,
    val name: String,
    val description: String,
    val quests: List<ChainQuest>
)

data class ChainQuest(
    val id: String,
    val name: String,
    val unlock: UnlockCondition?,
    val branches: List<QuestBranch> = emptyList()
)

data class UnlockCondition(
    val quest: String?,
    val status: QuestStatus
)

data class QuestBranch(
    val choice: String,
    val next: String
)

data class ChainProgress(
    val chainId: String,
    val currentQuest: String?,
    val completedQuests: MutableSet<String> = mutableSetOf()
)

enum class QuestStatus {
    COMPLETED,
    STARTED,
    FAILED
}
