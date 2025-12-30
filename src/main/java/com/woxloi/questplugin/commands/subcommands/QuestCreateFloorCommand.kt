package com.woxloi.questplugin.commands.subcommands

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat
import com.sk89q.worldedit.math.BlockVector3
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
            sender.sendMessage("§c§lプレイヤーのみ実行可能です")
            return true
        }

        if (args.size < 3) {
            sender.sendMessage("§c/quest floor create <id>")
            return true
        }

        val floorId = args[2]

        // Wand選択取得
        val selection = QuestWandListener.getSelectionLocations(sender)
            ?: run {
                sender.sendMessage("§c§lQuestWandで範囲を選択してください")
                return true
            }

        val (minLoc, maxLoc) = selection

        // ===== WorldEdit clipboard =====
        val session = WorldEdit.getInstance()
            .sessionManager
            .get(BukkitAdapter.adapt(sender))

        val holder = session.clipboard ?: run {
            sender.sendMessage("§c§lWorldEditで範囲を選択してください")
            return true
        }

        val clipboard = holder.clipboard

        val schemDir = File(QuestPlugin.plugin.dataFolder, "schematics")
        if (!schemDir.exists()) schemDir.mkdirs()

        val schemFile = File(schemDir, "$floorId.schem")

        BuiltInClipboardFormat.SPONGE_SCHEMATIC
            .getWriter(FileOutputStream(schemFile))
            .use { writer ->
                writer.write(clipboard)
            }

        // ===== floors.yml 保存 =====
        QuestFloorConfig.saveFloor(
            id = floorId,
            world = sender.world,
            min = BlockVector3.at(
                minLoc.blockX,
                minLoc.blockY,
                minLoc.blockZ
            ),
            max = BlockVector3.at(
                maxLoc.blockX,
                maxLoc.blockY,
                maxLoc.blockZ
            )
        )

        sender.sendMessage("§a§lフロア §e$floorId§a§lを保存しました")

        return true
    }
}
