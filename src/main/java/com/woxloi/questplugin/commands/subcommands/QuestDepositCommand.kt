package com.woxloi.questplugin.commands.subcommands

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.utils.VaultAPI

class QuestDepositCommand : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        val targetName = args[1]
        val amount = args[2].toDoubleOrNull()

        if (amount == null || amount <= 0) {
            sender.sendMessage(QuestPlugin.MoneyPrefix + "§c§l無効な金額です ")
            return true
        }

        val target = Bukkit.getOfflinePlayer(targetName)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            sender.sendMessage(QuestPlugin.MoneyPrefix + "§c§lプレイヤーが見つかりません ")
            return true
        }

        val vault = com.woxloi.questplugin.utils.VaultAPI()
        val success = vault.deposit(target.uniqueId, amount)

        if (success) {
            sender.sendMessage(QuestPlugin.MoneyPrefix + "§a§l${target.name}に${amount}円を付与しました ")
        } else {
            sender.sendMessage(QuestPlugin.MoneyPrefix + "§c§l付与に失敗しました ")
        }
        return true
    }
}
