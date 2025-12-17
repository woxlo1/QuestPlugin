package com.woxloi.questplugin.commands.subcommands

import com.woxloi.questplugin.QuestPlugin
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class QuestWandCommand : CommandExecutor {

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

        val wand = ItemStack(Material.STICK)
        val meta = wand.itemMeta ?: return true

        meta.setDisplayName("§e§lQuest Floor Wand")
        meta.lore = listOf(
            "§7クエストフロア範囲選択用",
            "§7左クリック: 始点",
            "§7右クリック: 終点",
        )

        wand.itemMeta = meta
        sender.inventory.addItem(wand)

        sender.sendMessage(QuestPlugin.prefix + "§a§lワンドを付与しました")
        return true
    }
}
