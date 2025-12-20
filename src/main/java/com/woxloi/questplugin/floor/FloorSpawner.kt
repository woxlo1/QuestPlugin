package com.woxloi.questplugin.floor

data class FloorSpawner(
    val x: Int,
    val y: Int,
    val z: Int,

    val mobId: String,
    val radius: Int,
    val amount: Int,

    // 将来拡張用（今は未使用）
    val wave: Int = 1,
    val isBoss: Boolean = false
)
