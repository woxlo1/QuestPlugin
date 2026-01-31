package com.woxloi.questplugin.commands.subcommands

import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.manager.QuestConfigManager
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.npc.NPC
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class NPCQuestCommand : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (sender !is Player) {
            sender.sendMessage(QuestPlugin.prefix + "§cプレイヤーのみ実行可能です")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(QuestPlugin.prefix + "§c使用方法: /quest npc <set|greeting|remove|info> <npcId> ...")
            return true
        }

        when (args[0].lowercase()) {

            "set" -> {
                if (args.size < 3) {
                    sender.sendMessage(QuestPlugin.prefix + "§c使用方法: /quest npc set <npcId> <questId>")
                    return true
                }

                val npc = getNPC(sender, args[1]) ?: return true
                val questId = args[2]
                val quest = QuestConfigManager.getQuest(questId)
                    ?: run {
                        sender.sendMessage(QuestPlugin.prefix + "§c${questId}は存在しません")
                        return true
                    }

                npc.data().set("quest_id", questId)
                sender.sendMessage(QuestPlugin.prefix + "§e${npc.name}§aにクエスト§e${quest.name}§aを設定しました")
            }

            "greeting" -> {
                if (args.size < 3) {
                    sender.sendMessage(QuestPlugin.prefix + "§c使用方法: /quest npc greeting <npcId> <メッセージ>")
                    return true
                }

                val npc = getNPC(sender, args[1]) ?: return true
                val greeting = args.drop(2).joinToString(" ")
                npc.data().set("quest_greeting", greeting)
                sender.sendMessage(QuestPlugin.prefix + "§a挨拶を設定しました: §f$greeting")
            }

            "remove" -> {
                if (args.size < 2) {
                    sender.sendMessage(QuestPlugin.prefix + "§c使用方法: /quest npc remove <npcId>")
                    return true
                }

                val npc = getNPC(sender, args[1]) ?: return true
                npc.data().remove("quest_id")
                npc.data().remove("quest_greeting")
                sender.sendMessage(QuestPlugin.prefix + "§aNPCからクエストを削除しました")
            }

            "info" -> {
                if (args.size < 2) {
                    sender.sendMessage(QuestPlugin.prefix + "§c使用方法: /quest npc info <npcId>")
                    return true
                }

                val npc = getNPC(sender, args[1]) ?: return true
                val questId = npc.data().get<String>("quest_id")
                val greeting = npc.data().get<String>("quest_greeting")

                sender.sendMessage("§7§l━━━━━━━━ NPC情報 ━━━━━━━━")
                sender.sendMessage("§e名前: §f${npc.name}")
                sender.sendMessage("§eクエストID: §f${questId ?: "未設定"}")
                sender.sendMessage("§e挨拶: §f${greeting ?: "デフォルト"}")
                sender.sendMessage("§7§l━━━━━━━━━━━━━━━━━━━━━━")
            }

            else -> {
                sender.sendMessage(QuestPlugin.prefix + "§c不明なサブコマンドです")
            }
        }

        return true
    }

    private fun getNPC(sender: Player, idArg: String): NPC? {
        val npcId = idArg.toIntOrNull()
            ?: run {
                sender.sendMessage(QuestPlugin.prefix + "§cNPCIDは数値で指定してください")
                return null
            }

        val npc = CitizensAPI.getNPCRegistry().getById(npcId)
        if (npc == null) {
            sender.sendMessage(QuestPlugin.prefix + "§c${npcId}は存在しません")
            return null
        }

        return npc
    }
}
