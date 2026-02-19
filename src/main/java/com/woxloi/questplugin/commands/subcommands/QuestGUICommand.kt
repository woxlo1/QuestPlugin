package com.woxloi.questplugin.commands.subcommands

import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.features.QuestListGUI
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class QuestGUICommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String> ): Boolean {
        if (sender !is Player) {
            sender.sendMessage(QuestPlugin.prefix + "§cプレイヤーのみ実行可能です")
            return true }

        QuestListGUI.open(sender)
        return true
    }
}
