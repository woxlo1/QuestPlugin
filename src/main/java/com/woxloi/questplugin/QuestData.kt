package com.woxloi.questplugin

data class QuestData(
    val id: String,
    var name: String = "名前未設定",
    var type: QuestType = QuestType.KILL,
    var target: String = "",
    var amount: Int = 1,
    var timeLimitSeconds: Long? = null,
    var rewards: MutableList<String> = mutableListOf(),

    var cooldownSeconds: Long? = null,
    var maxUseCount: Int? = null,
    var partyMaxMembers: Int? = null,

    var partyEnabled: Boolean = false,
    var shareProgress: Boolean = false,
    var shareCompletion: Boolean = false,

    var floorId: String? = null,

    //floorId の登場により使わなくなるかも
    var teleportWorld: String? = null,
    var teleportX: Double? = null,
    var teleportY: Double? = null,
    var teleportZ: Double? = null,

    var startCommands: MutableList<String> = mutableListOf(),
    var maxLives: Int? = null

)
