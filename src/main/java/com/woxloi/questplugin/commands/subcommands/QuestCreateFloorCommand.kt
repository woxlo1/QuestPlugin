package com.woxloi.questplugin.commands.subcommands

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.floor.QuestFloorConfig
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class QuestCreateFloorCommand : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): Boolean {

        // プレイヤー限定
        if (sender !is Player) {
            sender.sendMessage("§c§lプレイヤーのみ実行可能です")
            return true
        }

        val floorId = args[2]

        // WorldEdit選択取得
        val session = WorldEdit.getInstance()
            .sessionManager
            .get(BukkitAdapter.adapt(sender))

        val selection = try {
            session.selection
        } catch (e: Exception) {
            null
        }

        if (selection == null) {
            sender.sendMessage(
                QuestPlugin.prefix + "§c§lWorldEditで範囲を選択してください"
            )
            return true
        }

        // 保存
        QuestFloorConfig.saveFloor(
            id = floorId,
            world = sender.world,
            min = selection.minimumPoint,
            max = selection.maximumPoint
        )

        sender.sendMessage(
            QuestPlugin.prefix + "§a§lフロア§e§l" + floorId + "§a§lを保存しました"
        )
        return true
    }
}
