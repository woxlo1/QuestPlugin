package com.woxloi.questplugin.commands.subcommands

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent.suggestCommand
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.party.PartyManager

class QuestPartyCommand : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(QuestPlugin.PartyPrefix + "§c§lプレイヤーのみ実行可能です")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(QuestPlugin.PartyPrefix + "§c§lサブコマンドを指定してください。")
            return true
        }

        if (args[0].lowercase() == "party") {
            return handlePartyCommand(sender, args.drop(1).toTypedArray())
        }

        sender.sendMessage(QuestPlugin.PartyPrefix + "§c§l不明なサブコマンドです。")
        return true
    }

    private fun handlePartyCommand(player: Player, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            return showPartyInfo(player)
        }

        when (args[0].lowercase()) {
            "create" -> createParty(player)
            "disband" -> disbandParty(player)
            "invite" -> invitePlayer(player, args)
            "join" -> joinParty(player, args)
            "leave" -> leaveParty(player)
            "kick" -> kickPlayer(player, args)
            else -> player.sendMessage(QuestPlugin.PartyPrefix + "§c§l不明なパーティーサブコマンドです。")
        }
        return true
    }

    private fun showPartyInfo(player: Player): Boolean {
        val party = PartyManager.getParty(player)
        if (party == null) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§lパーティーに所属していません。")
            return true
        }
        val leaderName = Bukkit.getPlayer(party.leader)?.name ?: "不明"
        player.sendMessage("§7§l==== あなたのパーティー情報 ====")
        player.sendMessage("§c§lリーダー: §b§l$leaderName")
        player.sendMessage("§c§lメンバー:")
        party.members.forEach { memberUUID ->
            val memberName = Bukkit.getPlayer(memberUUID)?.name ?: "不明"
            player.sendMessage(" - §f§l$memberName")
        }
        player.sendMessage("§7§l===============================")
        return true
    }

    private fun createParty(player: Player) {
        if (PartyManager.isInParty(player)) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§l既にパーティーに所属しています。")
            return
        }
        if (PartyManager.createParty(player)) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§a§lパーティーを作成しました！")
        } else {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§lパーティー作成に失敗しました。")
        }
    }

    private fun disbandParty(player: Player) {
        if (!PartyManager.isInParty(player)) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§lパーティーに所属していません。")
            return
        }
        if (!PartyManager.isLeader(player)) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§lリーダーのみがパーティーを解散できます。")
            return
        }
        if (PartyManager.disbandParty(player)) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§a§lパーティーを解散しました。")
        } else {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§lパーティー解散に失敗しました。")
        }
    }

    private fun invitePlayer(player: Player, args: Array<String>) {
        if (!PartyManager.isInParty(player)) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§lパーティーに所属していません。")
            return
        }
        if (!PartyManager.isLeader(player)) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§lリーダーのみが招待可能です。")
            return
        }
        if (args.size < 2) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§l招待するプレイヤー名を指定してください。")
            return
        }
        val target = Bukkit.getPlayer(args[1])
        if (target == null || !target.isOnline) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§l指定したプレイヤーが見つかりません。")
            return
        }
        if (target.uniqueId == player.uniqueId) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§l自分自身を招待できません。")
            return
        }
        val result = PartyManager.invitePlayer(player, target)
        if (result == PartyManager.InviteResult.SUCCESS) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§a§l${target.name}をパーティーに招待しました。")
            target.sendMessage(text(QuestPlugin.PartyPrefix + "§e§l${player.name}からパーティーに招待されました"))
            target.sendMessage(text(QuestPlugin.PartyPrefix + "§a§l[ここをクリックで参加コマンド自動入力").clickEvent(suggestCommand("/quest party join ${player.name}")))
        } else {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§l招待に失敗しました。")
        }
    }

    private fun joinParty(player: Player, args: Array<String>) {
        if (args.size < 2) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§l参加するリーダー名を指定してください。")
            return
        }
        val leader = Bukkit.getPlayer(args[1])
        if (leader == null || !leader.isOnline) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§l指定したリーダーが見つかりません。")
            return
        }
        val result = PartyManager.joinParty(player, leader)
        if (result == PartyManager.JoinResult.SUCCESS) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§a§lパーティーに参加しました。")
            leader.sendMessage(QuestPlugin.PartyPrefix + "§e§l${player.name}がパーティーに参加しました。")
        } else {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§lパーティーへの参加に失敗しました。")
        }
    }

    private fun leaveParty(player: Player) {
        if (!PartyManager.isInParty(player)) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§lパーティーに所属していません。")
            return
        }
        val wasLeader = PartyManager.isLeader(player)
        PartyManager.leaveParty(player)
        player.sendMessage(QuestPlugin.PartyPrefix + "§a§lパーティーを離脱しました。")
        if (wasLeader) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§lあなたはリーダーだったため、パーティーは解散されました。")
        }
    }

    private fun kickPlayer(player: Player, args: Array<String>) {
        if (!PartyManager.isInParty(player)) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§lパーティーに所属していません。")
            return
        }
        if (!PartyManager.isLeader(player)) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§lリーダーのみが追放可能です。")
            return
        }
        if (args.size < 2) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§l追放するプレイヤー名を指定してください。")
            return
        }
        val target = Bukkit.getPlayer(args[1])
        if (target == null || !target.isOnline) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§l指定したプレイヤーが見つかりません。")
            return
        }
        if (!PartyManager.isInParty(target)) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§l指定したプレイヤーはパーティーに所属していません。")
            return
        }
        if (target.uniqueId == player.uniqueId) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§l自分自身を追放できません。")
            return
        }
        if (PartyManager.kickMember(player, target)) {
            player.sendMessage(QuestPlugin.PartyPrefix + "§a§l${target.name}をパーティーから追放しました。")
            target.sendMessage(QuestPlugin.PartyPrefix + "§c§lあなたはパーティーから追放されました。")
        } else {
            player.sendMessage(QuestPlugin.PartyPrefix + "§c§l追放に失敗しました。")
        }
    }
}
