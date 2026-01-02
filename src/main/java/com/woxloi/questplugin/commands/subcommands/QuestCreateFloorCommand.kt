package com.woxloi.questplugin.commands.subcommands

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat
import com.sk89q.worldedit.function.operation.ForwardExtentCopy
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.floor.QuestFloorConfig
import com.woxloi.questplugin.listeners.QuestWandListener
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.io.File
import java.io.FileOutputStream

class QuestCreateFloorCommand : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): Boolean {

        if (sender !is Player) {
            sender.sendMessage(QuestPlugin.prefix + "§cプレイヤーのみ実行可能です")
            return true
        }

        val floorId = args[2]

        // ============================
        // QuestWand 選択取得
        // ============================
        val (minLoc, maxLoc) = QuestWandListener.getSelectionLocations(sender)
            ?: run {
                sender.sendMessage(QuestPlugin.prefix + "§cQuestWandで範囲を選択してください")
                return true
            }

        val world = sender.world

        val min = BlockVector3.at(minLoc.blockX, minLoc.blockY, minLoc.blockZ)
        val max = BlockVector3.at(maxLoc.blockX, maxLoc.blockY, maxLoc.blockZ)

        // ============================
        // Clipboard 作成
        // ============================
        val region = CuboidRegion(min, max)
        val clipboard = BlockArrayClipboard(region)

        val editSession = WorldEdit.getInstance()
            .newEditSession(BukkitAdapter.adapt(world))

        val copy = ForwardExtentCopy(
            editSession,
            region,
            clipboard,
            region.minimumPoint
        )

        Operations.complete(copy)
        editSession.close()

        // ============================
        // Schematic 保存
        // ============================
        val schemDir = File(QuestPlugin.plugin.dataFolder, "schematics")
        if (!schemDir.exists()) schemDir.mkdirs()

        val schemFile = File(schemDir, "$floorId.schem")

        BuiltInClipboardFormat.SPONGE_SCHEMATIC
            .getWriter(FileOutputStream(schemFile))
            .use { it.write(clipboard) }

        // ============================
        // floors.yml 保存
        // ============================
        QuestFloorConfig.saveFloor(
            id = floorId,
            world = world,
            min = min,
            max = max
        )

        sender.sendMessage(QuestPlugin.prefix + "§a§lフロア§e§l" + floorId + "§a§lを保存しました")
        return true
    }
}
