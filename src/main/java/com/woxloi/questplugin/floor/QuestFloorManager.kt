package com.woxloi.questplugin.floor

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.session.ClipboardHolder
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.flags.Flags
import com.sk89q.worldguard.protection.flags.StateFlag
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion
import com.woxloi.questplugin.QuestData
import org.bukkit.*
import org.bukkit.block.Sign
import org.bukkit.entity.Player
import java.io.File
import java.util.*

object QuestFloorManager {

    data class FloorSpawner(
        val x: Int,
        val y: Int,
        val z: Int,
        val mobId: String,
        val amount: Int,
        val radius: Int
    )

    data class FloorInstance(
        val instanceId: UUID,
        val questId: String,
        val floorId: String,
        val world: World,
        val origin: Location,
        val min: BlockVector3,
        val max: BlockVector3,
        val partyLeader: UUID,
        val spawners: List<FloorSpawner>,
        val markers: List<FloorMarker>
    )

    data class FloorMarker(
        val type: MarkerType,
        val location: Location
    )

    enum class MarkerType {
        SPAWN,
        GOAL,
        NEXT
    }

    private val activeInstances = mutableMapOf<UUID, FloorInstance>()
    private const val SPACING = 400

    // ===============================
    // フロア生成
    // ===============================

    fun createInstance(quest: QuestData, members: List<Player>): FloorInstance {

        val floorId = quest.floorId ?: error("Quest floorId is null")
        val floor = QuestFloorConfig.getFloor(floorId)
        val world = Bukkit.getWorld(floor.world)
            ?: error("World ${floor.world} not found")

        val instanceId = UUID.randomUUID()
        val origin = getNextOrigin(world)

        pasteFloor(floor.schematicFile, origin)

        val min = floor.min.add(origin.blockX, origin.blockY, origin.blockZ)
        val max = floor.max.add(origin.blockX, origin.blockY, origin.blockZ)

        val spawners = scanSpawnerSigns(world, min, max)
        val markers = scanMarkers(world, min, max) // ← ★追加

        val instance = FloorInstance(
            instanceId,
            quest.id,
            floorId,
            world,
            origin,
            min,
            max,
            members.first().uniqueId,
            spawners,
            markers
        )

        activeInstances[instanceId] = instance

        createWorldGuardRegion(instanceId, world, min, max, members)

        // ===== スポーン処理 =====
        val spawn = markers.firstOrNull { it.type == MarkerType.SPAWN }
            ?: error("Spawn marker not found")

        members.forEach {
            it.teleport(spawn.location.clone().add(0.5, 1.0, 0.5))
        }

        return instance
    }

    // ===============================
    // Instance取得
    // ===============================
    fun getInstanceByPlayer(player: Player): UUID? {
        val l = player.location
        return activeInstances.values.firstOrNull {
            it.world == l.world &&
                    l.blockX in it.min.blockX..it.max.blockX &&
                    l.blockY in it.min.blockY..it.max.blockY &&
                    l.blockZ in it.min.blockZ..it.max.blockZ
        }?.instanceId
    }

    // ===============================
    // Schematic
    // ===============================
    private fun pasteFloor(file: File, origin: Location) {
        val format = ClipboardFormats.findByFile(file)!!
        val clipboard = format.getReader(file.inputStream()).read()

        val session = WorldEdit.getInstance()
            .newEditSession(BukkitAdapter.adapt(origin.world))

        val op = ClipboardHolder(clipboard)
            .createPaste(session)
            .to(BlockVector3.at(origin.blockX, origin.blockY, origin.blockZ))
            .ignoreAirBlocks(false)
            .build()

        Operations.complete(op)
        session.close()
    }

    // ===============================
    // Spawner看板読み取り（保存のみ）
    // ===============================
    private fun scanSpawnerSigns(
        world: World,
        min: BlockVector3,
        max: BlockVector3
    ): List<FloorSpawner> {

        val list = mutableListOf<FloorSpawner>()

        for (x in min.blockX..max.blockX)
            for (y in min.blockY..max.blockY)
                for (z in min.blockZ..max.blockZ) {

                    val state = world.getBlockAt(x, y, z).state
                    if (state is Sign && state.getLine(0).equals("[Spawner]", true)) {
                        list.add(
                            FloorSpawner(
                                x, y, z,
                                state.getLine(1),
                                state.getLine(2).toIntOrNull() ?: 1,
                                state.getLine(3).toIntOrNull() ?: 5
                            )
                        )
                        state.block.type = Material.AIR
                    }
                }

        return list
    }

    // ===============================
    // クリーンアップ
    // ===============================
    fun release(instanceId: UUID) {
        val instance = activeInstances.remove(instanceId) ?: return
        removeWorldGuardRegion(instance)
        clearArea(instance)
    }

    // ===============================
    // WorldGuard
    // ===============================
    private fun createWorldGuardRegion(
        id: UUID,
        world: World,
        min: BlockVector3,
        max: BlockVector3,
        players: List<Player>
    ) {
        val manager = WorldGuard.getInstance()
            .platform.regionContainer
            .get(BukkitAdapter.adapt(world)) ?: return

        val region = ProtectedCuboidRegion("quest_$id", min, max)

        region.setFlag(Flags.PVP, StateFlag.State.DENY)
        region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY)
        region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY)

        players.forEach { region.owners.addPlayer(it.uniqueId) }

        manager.addRegion(region)
    }

    private fun removeWorldGuardRegion(instance: FloorInstance) {
        val manager = WorldGuard.getInstance()
            .platform.regionContainer
            .get(BukkitAdapter.adapt(instance.world)) ?: return

        manager.removeRegion("quest_${instance.instanceId}")
    }

    private fun getNextOrigin(world: World): Location {
        return Location(world, activeInstances.size * SPACING.toDouble(), 64.0, 0.0)
    }

    private fun clearArea(instance: FloorInstance) {
        val w = instance.world
        for (x in instance.min.blockX..instance.max.blockX)
            for (y in instance.min.blockY..instance.max.blockY)
                for (z in instance.min.blockZ..instance.max.blockZ)
                    w.getBlockAt(x, y, z).type = Material.AIR
    }

    // ===============================
    // スキャン処理
    // ===============================

    private fun scanMarkers(world: World, min: BlockVector3, max: BlockVector3): List<FloorMarker> {
        val list = mutableListOf<FloorMarker>()

        for (x in min.blockX..max.blockX)
            for (y in min.blockY..max.blockY)
                for (z in min.blockZ..max.blockZ) {

                    val block = world.getBlockAt(x, y, z)

                    when (block.type) {
                        Material.STONE_BRICK_STAIRS ->
                            list.add(FloorMarker(MarkerType.SPAWN, block.location))

                        Material.DIAMOND_BLOCK ->
                            list.add(FloorMarker(MarkerType.GOAL, block.location))

                        Material.CRYING_OBSIDIAN ->
                            list.add(FloorMarker(MarkerType.NEXT, block.location))

                        else -> {}
                    }
                }

        return list
    }

    fun getInstance(id: UUID): FloorInstance? {
        return activeInstances[id]
    }

    fun getMarkers(instance: FloorInstance): List<FloorMarker> {
        return instance.markers
    }

}
