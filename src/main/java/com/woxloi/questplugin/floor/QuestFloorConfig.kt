package com.woxloi.questplugin.floor

import com.sk89q.worldedit.math.BlockVector3
import com.woxloi.questplugin.QuestPlugin
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object QuestFloorConfig {

    data class Floor(
        val world: String,
        val min: BlockVector3,
        val max: BlockVector3,
        val schematicFile: File
    )

    private val file = File(QuestPlugin.plugin.dataFolder, "floors.yml")
    private val config = YamlConfiguration.loadConfiguration(file)

    fun saveFloor(id: String, world: World, min: BlockVector3, max: BlockVector3) {
        config.set("floors.$id.world", world.name)
        config.set("floors.$id.min", mapOf("x" to min.x, "y" to min.y, "z" to min.z))
        config.set("floors.$id.max", mapOf("x" to max.x, "y" to max.y, "z" to max.z))
        config.set("floors.$id.schematic", "$id.schem")
        config.save(file)
    }

    fun getFloor(id: String): Floor {
        val sec = config.getConfigurationSection("floors.$id")
            ?: error("Floor $id が存在しません")

        return Floor(
            sec.getString("world")!!,
            BlockVector3.at(
                sec.getInt("min.x"),
                sec.getInt("min.y"),
                sec.getInt("min.z")
            ),
            BlockVector3.at(
                sec.getInt("max.x"),
                sec.getInt("max.y"),
                sec.getInt("max.z")
            ),
            File(QuestPlugin.plugin.dataFolder, sec.getString("schematic")!!)
        )
    }
}
