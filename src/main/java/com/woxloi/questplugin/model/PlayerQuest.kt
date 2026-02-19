package com.woxloi.questplugin.model

import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * プレイヤーが作成できる民間クエスト
 */
data class PlayerQuest(
    val id: String,
    val creatorUUID: UUID,
    val creatorName: String,
    var title: String,
    var description: String,
    var type: QuestType,
    var target: String,
    var amount: Int,
    var rewardItems: MutableList<ItemStack> = mutableListOf(),
    var rewardMoney: Double = 0.0,
    var timeLimitSeconds: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    var isOpen: Boolean = true,          // 受注可能かどうか
    var maxAcceptors: Int = 1,           // 同時受注可能人数
    var currentAcceptors: MutableList<UUID> = mutableListOf(),
    var completedBy: MutableList<UUID> = mutableListOf(),
    var depositPaid: Boolean = false,    // 報酬デポジット済みか
)
