package com.woxloi.questplugin.floor

data class FloorSpawner(
    val x: Int,
    val y: Int,
    val z: Int,

    val mobId: String,
    val radius: Int,
    val amount: Int,
)
