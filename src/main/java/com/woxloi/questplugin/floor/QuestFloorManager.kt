package com.woxloi.questplugin.floor

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.session.ClipboardHolder
import com.woxloi.questplugin.QuestData
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import java.io.File
import java.util.*
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.flags.Flags
import com.sk89q.worldguard.protection.flags.StateFlag
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion

object QuestFloorManager {

    data class FloorInstance(
        val instanceId: UUID,
        val questId: String,
        val world: World,
        val origin: Location,
        val min: BlockVector3,
        val max: BlockVector3,
        val partyLeader: UUID
    )

    private val activeInstances = mutableMapOf<UUID, FloorInstance>()
    private val spacing = 400 // フロア間隔

    /**
     * フロア生成 & TP
     */
    fun createInstance(
        quest: QuestData,
        partyMembers: List<Player>
    ): FloorInstance {

        val instanceId = UUID.randomUUID()
        val floorId = quest.floorId
            ?: error("Quest ${quest.id} に floorId が設定されていません")

        val floorConfig = QuestFloorConfig.getFloor(floorId)
        val world = Bukkit.getWorld(floorConfig.world)
            ?: error("World ${floorConfig.world} が存在しません")

        val origin = getNextOrigin(world)
        pasteFloor(floorConfig.schematicFile, origin)

        val min = floorConfig.min.add(origin.blockX, origin.blockY, origin.blockZ)
        val max = floorConfig.max.add(origin.blockX, origin.blockY, origin.blockZ)

        createWorldGuardRegion(instanceId, world, min, max, partyMembers)

        val instance = FloorInstance(
            instanceId,
            quest.id,
            world,
            origin,
            min,
            max,
            partyMembers.first().uniqueId
        )

        activeInstances[instanceId] = instance

        partyMembers.forEach {
            it.teleport(origin.clone().add(0.5, 1.0, 0.5))
        }

        return instance
    }

    /**
     * フロア削除
     */
    fun release(instanceId: UUID) {
        val instance = activeInstances.remove(instanceId) ?: return

        removeWorldGuardRegion(instance)
        clearArea(instance)
    }

    // ---------------- 内部処理 ----------------

    private fun getNextOrigin(world: World): Location {
        val index = activeInstances.size
        return Location(world, index * spacing.toDouble(), 64.0, 0.0)
    }

    private fun pasteFloor(file: File, origin: Location) {
        val format = ClipboardFormats.findByFile(file)
            ?: error("不明な schematic 形式")

        val clipboard: Clipboard = format.getReader(file.inputStream()).use { it.read() }
        val weWorld = BukkitAdapter.adapt(origin.world)

        val session = WorldEdit.getInstance().newEditSession(weWorld)
        val operation = ClipboardHolder(clipboard)
            .createPaste(session)
            .to(BlockVector3.at(origin.blockX, origin.blockY, origin.blockZ))
            .ignoreAirBlocks(false)
            .build()

        Operations.complete(operation)
        session.close()
    }

    private fun clearArea(instance: FloorInstance) {
        val world = instance.world
        for (x in instance.min.blockX..instance.max.blockX) {
            for (y in instance.min.blockY..instance.max.blockY) {
                for (z in instance.min.blockZ..instance.max.blockZ) {
                    world.getBlockAt(x, y, z).type = org.bukkit.Material.AIR
                }
            }
        }
    }

    private fun createWorldGuardRegion(
        instanceId: UUID,
        world: World,
        min: BlockVector3,
        max: BlockVector3,
        members: List<Player>
    ) {
        val container = WorldGuard.getInstance().platform.regionContainer
        val manager = container.get(BukkitAdapter.adapt(world)) ?: return

        val region = ProtectedCuboidRegion(
            "quest_$instanceId",
            min,
            max
        )

        region.setFlag(Flags.PVP, StateFlag.State.DENY)
        region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY)
        region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY)
        region.setFlag(Flags.TNT, StateFlag.State.DENY)
        region.setFlag(Flags.CREEPER_EXPLOSION, StateFlag.State.DENY)
        region.setFlag(Flags.FIRE_SPREAD, StateFlag.State.DENY)
        region.setFlag(Flags.ENTRY, StateFlag.State.DENY)

        members.forEach { region.owners.addPlayer(it.uniqueId) }

        manager.addRegion(region)
    }

    private fun removeWorldGuardRegion(instance: FloorInstance) {
        val container = WorldGuard.getInstance().platform.regionContainer
        val manager = container.get(BukkitAdapter.adapt(instance.world)) ?: return
        manager.removeRegion("quest_${instance.instanceId}")
    }
}
