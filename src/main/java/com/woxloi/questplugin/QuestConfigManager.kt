package com.woxloi.questplugin

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object QuestConfigManager {

    private val questFile = File(QuestPlugin.plugin.dataFolder, "quests.yml")
    private lateinit var config: YamlConfiguration

    val quests = mutableMapOf<String, QuestData>()

    fun loadAllQuests() {
        if (!questFile.exists()) {
            questFile.parentFile.mkdirs()
            questFile.createNewFile()
        }

        config = YamlConfiguration.loadConfiguration(questFile)
        quests.clear()

        val section = config.getConfigurationSection("quests") ?: return

        for (id in section.getKeys(false)) {
            val q = section.getConfigurationSection(id) ?: continue

            val name = q.getString("name") ?: "名前未設定"
            val type = QuestType.fromString(q.getString("type") ?: "") ?: QuestType.KILL
            val target = q.getString("target") ?: ""
            val amount = q.getInt("amount")
            val timeLimit = q.getLong("timeLimit", -1)
            val rewards = q.getStringList("rewards").toMutableList()
            val startCommands = q.getStringList("startCommands").toMutableList()
            val maxLives = q.getInt("maxLives", -1)

            val cooldown = q.getLong("cooldownSeconds", -1)
            val maxUse = q.getInt("maxUseCount", -1)

            val partyEnabled = q.getBoolean("partyEnabled", false)
            val shareProgress = q.getBoolean("shareProgress", false)
            val shareCompletion = q.getBoolean("shareCompletion", false)
            val partyMaxMembers = q.getInt("partyMaxMembers", -1)

            val floorId = q.getString("floorId")

            // 追加: テレポート先の読み込み（null許容）
            val teleportWorld = q.getString("teleportWorld")
            val teleportX = if (q.contains("teleportX")) q.getDouble("teleportX") else null
            val teleportY = if (q.contains("teleportY")) q.getDouble("teleportY") else null
            val teleportZ = if (q.contains("teleportZ")) q.getDouble("teleportZ") else null

            quests[id] = QuestData(
                id = id,
                name = name,
                type = type,
                target = target,
                amount = amount,
                timeLimitSeconds = if (timeLimit >= 0) timeLimit else null,
                rewards = rewards,
                startCommands = startCommands,
                cooldownSeconds = if (cooldown >= 0) cooldown else null,
                maxUseCount = if (maxUse >= 0) maxUse else null,
                partyEnabled = partyEnabled,
                shareProgress = shareProgress,
                shareCompletion = shareCompletion,
                partyMaxMembers = if (partyMaxMembers >= 0) partyMaxMembers else null,

                floorId = floorId,

                teleportWorld = teleportWorld,
                teleportX = teleportX,
                teleportY = teleportY,
                teleportZ = teleportZ,
                maxLives = if (maxLives >= 0) maxLives else null
            )
        }
    }

    fun saveAllQuests() {
        val root = YamlConfiguration()

        for ((id, data) in quests) {
            val path = "quests.$id"
            root.set("$path.name", data.name)
            root.set("$path.type", data.type.name)
            root.set("$path.target", data.target)
            root.set("$path.amount", data.amount)
            root.set("$path.timeLimit", data.timeLimitSeconds ?: -1)
            root.set("$path.rewards", data.rewards)
            root.set("$path.startCommands", data.startCommands)
            root.set("$path.maxLives", data.maxLives ?: -1)


            root.set("$path.cooldownSeconds", data.cooldownSeconds ?: -1)
            root.set("$path.maxUseCount", data.maxUseCount ?: -1)

            root.set("$path.partyEnabled", data.partyEnabled)
            root.set("$path.partyMaxMembers", data.partyMaxMembers ?: -1)
            root.set("$path.shareProgress", data.shareProgress)
            root.set("$path.shareCompletion", data.shareCompletion)

            root.set("$path.floorId", data.floorId)
            // 追加: テレポート先の保存
            root.set("$path.teleportWorld", data.teleportWorld)
            root.set("$path.teleportX", data.teleportX)
            root.set("$path.teleportY", data.teleportY)
            root.set("$path.teleportZ", data.teleportZ)
        }

        root.save(questFile)
    }


    fun getQuest(id: String): QuestData? {
        return quests[id]
    }

    fun createQuest(id: String): QuestData {
        val data = QuestData(id = id)
        quests[id] = data
        return data
    }

    fun deleteQuest(id: String) {
        quests.remove(id)
    }

    fun exists(id: String): Boolean {
        return quests.containsKey(id)
    }

    fun getAllQuests(): Collection<QuestData> {
        return quests.values
    }
}
