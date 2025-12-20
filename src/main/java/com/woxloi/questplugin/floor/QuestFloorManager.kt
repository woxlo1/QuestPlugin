package com.woxloi.questplugin.floor

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.session.ClipboardHolder
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.flags.Flags
import com.sk89q.worldguard.protection.flags.StateFlag
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion
import com.woxloi.questplugin.QuestData
import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.core.utils.placeholders.PlaceholderInt
import org.bukkit.*
import org.bukkit.block.Sign
import org.bukkit.entity.Player
import java.io.File
import java.util.*

object QuestFloorManager {

    data class FloorInstance(
        val instanceId: UUID,
        val questId: String,
        val floorId: String,
        val world: World,
        val origin: Location,
        val min: BlockVector3,
        val max: BlockVector3,
        val partyLeader: UUID,
        val spawners: List<FloorSpawner>
    )

    private val activeInstances = mutableMapOf<UUID, FloorInstance>()
    private const val SPACING = 400

    // =====================================================
    // フロア生成
    // =====================================================
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

        val spawners = scanSpawnerSigns(world, min, max)

        createWorldGuardRegion(instanceId, world, min, max, partyMembers)
        spawnMythicSpawners(instanceId, spawners, world)

        val instance = FloorInstance(
            instanceId,
            quest.id,
            floorId,
            world,
            origin,
            min,
            max,
            partyMembers.first().uniqueId,
            spawners
        )

        activeInstances[instanceId] = instance

        partyMembers.forEach {
            it.teleport(origin.clone().add(0.5, 1.0, 0.5))
        }

        return instance
    }

    // =====================================================
    // フロア解放
    // =====================================================
    fun release(instanceId: UUID) {
        val instance = activeInstances.remove(instanceId) ?: return
        clearMythic(instance)
        removeWorldGuardRegion(instance)
        clearArea(instance)
    }

    // =====================================================
    // プレイヤー → インスタンス取得
    // =====================================================
    fun getInstanceByPlayer(player: Player): UUID? {
        val loc = player.location
        return activeInstances.values.firstOrNull {
            it.world == loc.world &&
                    loc.blockX in it.min.blockX..it.max.blockX &&
                    loc.blockY in it.min.blockY..it.max.blockY &&
                    loc.blockZ in it.min.blockZ..it.max.blockZ
        }?.instanceId
    }

    fun getInstance(id: UUID): FloorInstance? = activeInstances[id]

    // =====================================================
    // 内部処理
    // =====================================================
    private fun getNextOrigin(world: World): Location {
        val index = activeInstances.size
        return Location(world, index * SPACING.toDouble(), 64.0, 0.0)
    }

    private fun pasteFloor(file: File, origin: Location) {
        val format = ClipboardFormats.findByFile(file)
            ?: error("不明な schematic 形式")

        val clipboard: Clipboard = format.getReader(file.inputStream()).use { it.read() }
        val session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(origin.world))

        val operation = ClipboardHolder(clipboard)
            .createPaste(session)
            .to(BlockVector3.at(origin.blockX, origin.blockY, origin.blockZ))
            .ignoreAirBlocks(false)
            .build()

        Operations.complete(operation)
        session.close()
    }

    // =====================================================
    // Spawner看板
    // =====================================================
    private fun scanSpawnerSigns(
        world: World,
        min: BlockVector3,
        max: BlockVector3
    ): List<FloorSpawner> {

        val list = mutableListOf<FloorSpawner>()

        for (x in min.blockX..max.blockX) {
            for (y in min.blockY..max.blockY) {
                for (z in min.blockZ..max.blockZ) {
                    val state = world.getBlockAt(x, y, z).state
                    if (state is Sign && state.getLine(0).equals("[Spawner]", true)) {
                        list.add(
                            FloorSpawner(
                                x, y, z,
                                state.getLine(1),
                                state.getLine(2).toIntOrNull() ?: 5,
                                state.getLine(3).toIntOrNull() ?: 1
                            )
                        )
                        state.block.type = Material.AIR
                    }
                }
            }
        }
        return list
    }

    // =====================================================
    // MythicSpawner生成（正規API）
    // =====================================================
    private fun spawnMythicSpawners(
        instanceId: UUID,
        spawners: List<FloorSpawner>,
        world: World
    ) {
        val manager = MythicBukkit.inst().spawnerManager

        spawners.forEach { s ->
            val loc = Location(
                world,
                s.x.toDouble() + 0.5,
                s.y.toDouble(),
                s.z.toDouble() + 0.5
            )

            val name = "quest_${instanceId}_${s.x}_${s.y}_${s.z}"

            val spawner = manager.createSpawner(
                name,
                BukkitAdapter.adapt(loc),
                s.mobId
            )

            spawner.spawnRadius = s.radius.toDouble()
            spawner.maxMobs = PlaceholderInt.of(s.amount)
            spawner.start()
        }
    }

    // =====================================================
    // Mythic全削除（インスタンス限定）
    // =====================================================
    private fun clearMythic(instance: FloorInstance) {
        val manager = MythicBukkit.inst().spawnerManager
        val prefix = "quest_${instance.instanceId}_"

        manager.allSpawners
            .filter { it.internalName.startsWith(prefix) }
            .forEach {
                it.stop()
                manager.removeSpawner(it.internalName)
            }

        instance.world.livingEntities
            .filter { MythicBukkit.inst().mobManager.isMythicMob(it) }
            .filter {
                val l = it.location
                l.blockX in instance.min.blockX..instance.max.blockX &&
                        l.blockY in instance.min.blockY..instance.max.blockY &&
                        l.blockZ in instance.min.blockZ..instance.max.blockZ
            }
            .forEach { it.remove() }
    }

    // =====================================================
    // エリア削除
    // =====================================================
    private fun clearArea(instance: FloorInstance) {
        val w = instance.world
        for (x in instance.min.blockX..instance.max.blockX)
            for (y in instance.min.blockY..instance.max.blockY)
                for (z in instance.min.blockZ..instance.max.blockZ)
                    w.getBlockAt(x, y, z).type = Material.AIR
    }

    // =====================================================
    // WorldGuard
    // =====================================================
    private fun createWorldGuardRegion(
        instanceId: UUID,
        world: World,
        min: BlockVector3,
        max: BlockVector3,
        members: List<Player>
    ) {
        val manager = WorldGuard.getInstance()
            .platform.regionContainer
            .get(BukkitAdapter.adapt(world)) ?: return

        val region = ProtectedCuboidRegion("quest_$instanceId", min, max)

        region.setFlag(Flags.PVP, StateFlag.State.DENY)
        region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY)
        region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY)
        region.setFlag(Flags.ENTRY, StateFlag.State.DENY)

        members.forEach { region.owners.addPlayer(it.uniqueId) }
        manager.addRegion(region)
    }

    private fun removeWorldGuardRegion(instance: FloorInstance) {
        val manager = WorldGuard.getInstance()
            .platform.regionContainer
            .get(BukkitAdapter.adapt(instance.world)) ?: return
        manager.removeRegion("quest_${instance.instanceId}")
    }
}
