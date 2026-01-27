package com.woxloi.questplugin.commands.subcommands

import net.kyori.adventure.text.Component
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import com.woxloi.questplugin.*

class QuestSetCommand(private val plugin: JavaPlugin) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {

        val id    = args[2]
        val key   = args[3].lowercase()
        val value = args.drop(4).joinToString(" ") // スペースを含む値対策

        val quest = QuestConfigManager.getQuest(id)
        if (quest == null) {
            sender.sendMessage(QuestPlugin.prefix + "§c§lクエスト${id}は存在しません ")
            return true
        }

        fun bool(): Boolean? =
            when (value.lowercase()) { "true","yes","on","1" -> true; "false","no","off","0" -> false; else -> null }

        try {
            when (key) {
                "name"                -> quest.name  = value
                "type"                -> quest.type  = QuestType.fromString(value)
                    ?: return sender.fail("不正なtypeです ")
                "target"              -> quest.target = value
                "amount"              -> quest.amount = value.toInt().positive() ?: return sender.fail("amountは正の整数")

                "timelimit","timelimitseconds" ->
                    quest.timeLimitSeconds = value.toLong().nonNegative() ?: return sender.fail("timelimitは0以上の整数")

                "cooldown","cooldownseconds"   ->
                    quest.cooldownSeconds = value.toLong().positive() ?: return sender.fail("cooldownは正の整数")
                "maxuse","maxusecount"         ->
                    quest.maxUseCount = value.toInt().positive() ?: return sender.fail("maxUseは正の整数")

                "maxlives" -> quest.maxLives = value.toInt().positive() ?: return sender.fail("maxLivesは正の整数")

                "partyenabled"  -> quest.partyEnabled   = bool() ?: return sender.fail("true/false を指定")
                "shareprogress" -> quest.shareProgress  = bool() ?: return sender.fail("true/false を指定")
                "sharecompletion"->quest.shareCompletion= bool() ?: return sender.fail("true/false を指定")
                "partymaxmembers"-> quest.partyMaxMembers =
                    value.toInt().positive() ?: return sender.fail("partyMaxMembersは正の整数")

                "teleportworld" -> quest.teleportWorld = value
                "teleportx"     -> quest.teleportX     = value.toDoubleOrNull() ?: return sender.fail("X座標が数値ではありません")
                "teleporty"     -> quest.teleportY     = value.toDoubleOrNull() ?: return sender.fail("Y座標が数値ではありません")
                "teleportz"     -> quest.teleportZ     = value.toDoubleOrNull() ?: return sender.fail("Z座標が数値ではありません")

                else -> return sender.fail("設定できないキーです ")
            }
        } catch (ex: NumberFormatException) {
            return sender.fail("数値のパースに失敗しました")
        }

        sender.sendMessage(QuestPlugin.prefix + "§b§lクエスト名" + id + "の§f§l" + key + "を§a§l" + value + "§f§lに設定しました")
        return true
    }

    private fun CommandSender.fail(msg: String): Boolean {
        sendMessage(QuestPlugin.prefix + "§c§l$msg")
        return true
    }
}

private fun Long.positive(): Long? = takeIf { it > 0 }

fun Int.positive(): Int? = takeIf { it > 0 }

fun Long.nonNegative(): Long? = takeIf { it >= 0 }
