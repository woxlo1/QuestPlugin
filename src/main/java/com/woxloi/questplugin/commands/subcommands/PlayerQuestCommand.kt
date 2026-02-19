package com.woxloi.questplugin.commands.subcommands

import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.features.PlayerQuestBoardGUI
import com.woxloi.questplugin.manager.PlayerQuestManager
import com.woxloi.questplugin.model.QuestType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * /quest player <サブコマンド>
 *
 * player board          - 掲示板GUIを開く
 * player create <title> - 作成開始
 * player set desc <説明> - 説明設定
 * player set type <タイプ> - タイプ設定
 * player set target <対象> - 対象設定
 * player set amount <数> - 数設定
 * player set reward money <金額> - 金銭報酬
 * player set maxaccept <人数> - 同時受注人数
 * player publish         - 掲示板に投稿
 * player cancel          - 作成キャンセル
 * player list            - 受注中クエスト一覧
 * player delete <id>     - 自分のクエスト削除
 * player info <id>       - クエスト詳細
 */
class PlayerQuestCommand : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(QuestPlugin.prefix + "§cプレイヤーのみ実行可能です")
            return true
        }

        // args[0] = "player", args[1] = サブコマンド
        val sub = args.getOrNull(1)?.lowercase() ?: "board"

        when (sub) {
            "board" -> PlayerQuestBoardGUI.open(sender)

            "create" -> {
                val title = args.drop(2).joinToString(" ")
                if (title.isBlank()) {
                    sender.sendMessage(QuestPlugin.prefix + "§c使用方法: /quest player create <タイトル>")
                    return true
                }
                val draft = PlayerQuestManager.startDraft(sender, title)
                sender.sendMessage(QuestPlugin.prefix + "§a§lドラフト作成開始: §e§l$title")
                sender.sendMessage(QuestPlugin.prefix + "§7以下のコマンドで設定してください:")
                sender.sendMessage("§e  /quest player set type <タイプ名>")
                sender.sendMessage("§e  /quest player set target <ターゲット>")
                sender.sendMessage("§e  /quest player set amount <数>")
                sender.sendMessage("§e  /quest player set reward money <金額>")
                sender.sendMessage("§e  /quest player set maxaccept <人数>")
                sender.sendMessage("§e  /quest player publish §7で投稿")
            }

            "set" -> handleSet(sender, args)

            "publish" -> {
                val draft = PlayerQuestManager.getDraft(sender)
                if (draft == null) {
                    sender.sendMessage(QuestPlugin.prefix + "§c作成中のクエストがありません。/quest player board から始めてください")
                    return true
                }
                // 確認表示
                sender.sendMessage("§7§l━━━━ クエスト投稿確認 ━━━━")
                sender.sendMessage("§eタイトル: §f${draft.title}")
                sender.sendMessage("§eタイプ: §f${draft.type.displayName}")
                sender.sendMessage("§e目標: §f${draft.target} x${draft.amount}")
                sender.sendMessage("§e金銭報酬: §f${draft.rewardMoney}円")
                sender.sendMessage("§e最大受注: §f${draft.maxAcceptors}人")
                if (draft.rewardMoney > 0) {
                    sender.sendMessage("§c§l※ ${draft.rewardMoney}円がデポジットされます")
                }

                val result = PlayerQuestManager.publishDraft(sender)
                result.onSuccess { q ->
                    sender.sendMessage(QuestPlugin.prefix + "§a§lクエストを掲示板に投稿しました！ ID: §f${q.id}")
                    sender.sendMessage(
                        Component.text(QuestPlugin.prefix + "§b§l[掲示板を開く]")
                            .clickEvent(ClickEvent.runCommand("/quest player board"))
                    )
                }.onFailure {
                    sender.sendMessage(QuestPlugin.prefix + "§c§l投稿失敗: ${it.message}")
                }
            }

            "cancel" -> {
                PlayerQuestManager.clearDraft(sender)
                sender.sendMessage(QuestPlugin.prefix + "§e§lクエスト作成をキャンセルしました")
            }

            "list" -> {
                val acceptedIds = PlayerQuestManager.getAcceptedQuestIds(sender)
                if (acceptedIds.isEmpty()) {
                    sender.sendMessage(QuestPlugin.prefix + "§7受注中の民間クエストはありません")
                    return true
                }
                sender.sendMessage("§6§l===== 受注中の民間クエスト =====")
                for (id in acceptedIds) {
                    val quest = PlayerQuestManager.getQuest(id) ?: continue
                    val progress = PlayerQuestManager.getProgress(sender, id)
                    sender.sendMessage("§e● §f${quest.title} §7[$progress/${quest.amount}]")
                }
                sender.sendMessage("§6§l===========================")
            }

            "delete" -> {
                val id = args.getOrNull(2) ?: run {
                    sender.sendMessage(QuestPlugin.prefix + "§c使用方法: /quest player delete <クエストID>")
                    return true
                }
                val result = PlayerQuestManager.deleteQuest(sender, id)
                result.onSuccess { sender.sendMessage(QuestPlugin.prefix + "§a§lクエストを削除しました") }
                    .onFailure { sender.sendMessage(QuestPlugin.prefix + "§c§l${it.message}") }
            }

            "info" -> {
                val id = args.getOrNull(2) ?: run {
                    sender.sendMessage(QuestPlugin.prefix + "§c使用方法: /quest player info <クエストID>")
                    return true
                }
                val quest = PlayerQuestManager.getQuest(id)
                if (quest == null) {
                    sender.sendMessage(QuestPlugin.prefix + "§c§lクエストが見つかりません: $id")
                    return true
                }
                sender.sendMessage("§7§l━━━━ 民間クエスト詳細 ━━━━")
                sender.sendMessage("§eタイトル: §f${quest.title}")
                sender.sendMessage("§e依頼者: §f${quest.creatorName}")
                sender.sendMessage("§eタイプ: §f${quest.type.displayName}")
                sender.sendMessage("§e目標: §f${quest.target} x${quest.amount}")
                sender.sendMessage("§e金銭報酬: §f${quest.rewardMoney}円")
                sender.sendMessage("§eアイテム報酬: §f${quest.rewardItems.size}種")
                sender.sendMessage("§e状態: ${if (quest.isOpen) "§a受注可能" else "§c停止中"}")
                sender.sendMessage("§e受注中: §f${quest.currentAcceptors.size}/${quest.maxAcceptors}人")
                sender.sendMessage("§e完了数: §f${quest.completedBy.size}人")
                sender.sendMessage("§7§l━━━━━━━━━━━━━━━━━━━━━━")
            }

            else -> {
                sender.sendMessage(QuestPlugin.prefix + "§c§l不明なサブコマンドです")
                showHelp(sender)
            }
        }
        return true
    }

    private fun handleSet(player: Player, args: Array<out String>) {
        val draft = PlayerQuestManager.getDraft(player)
        if (draft == null) {
            player.sendMessage(QuestPlugin.prefix + "§c作成中のクエストがありません。/quest player create から始めてください")
            return
        }

        val key = args.getOrNull(2)?.lowercase() ?: run {
            player.sendMessage(QuestPlugin.prefix + "§c設定項目を指定してください")
            return
        }

        when (key) {
            "type" -> {
                val typeName = args.getOrNull(3) ?: run {
                    player.sendMessage(QuestPlugin.prefix + "§cタイプ名を指定してください")
                    return
                }
                val type = QuestType.fromString(typeName) ?: run {
                    player.sendMessage(QuestPlugin.prefix + "§c不正なタイプです: $typeName")
                    return
                }
                draft.type = type
                player.sendMessage(QuestPlugin.prefix + "§aタイプを §e${type.displayName}§a に設定しました")
            }

            "target" -> {
                val target = args.drop(3).joinToString(" ")
                if (target.isBlank()) { player.sendMessage(QuestPlugin.prefix + "§cターゲットを指定してください"); return }
                draft.target = target
                player.sendMessage(QuestPlugin.prefix + "§aターゲットを §e${target}§a に設定しました")
            }

            "amount" -> {
                val amount = args.getOrNull(3)?.toIntOrNull()?.takeIf { it > 0 } ?: run {
                    player.sendMessage(QuestPlugin.prefix + "§c正の整数を指定してください")
                    return
                }
                draft.amount = amount
                player.sendMessage(QuestPlugin.prefix + "§a必要数を §e${amount}§a に設定しました")
            }

            "desc", "description" -> {
                val desc = args.drop(3).joinToString(" ")
                draft.description = desc
                player.sendMessage(QuestPlugin.prefix + "§a説明を設定しました")
            }

            "reward" -> {
                val rewardType = args.getOrNull(3)?.lowercase()
                when (rewardType) {
                    "money" -> {
                        val money = args.getOrNull(4)?.toDoubleOrNull()?.takeIf { it > 0 } ?: run {
                            player.sendMessage(QuestPlugin.prefix + "§c正の数値を指定してください")
                            return
                        }
                        draft.rewardMoney = money
                        player.sendMessage(QuestPlugin.prefix + "§a金銭報酬を §e${money}円§a に設定しました")
                    }
                    "item" -> {
                        val hand = player.inventory.itemInMainHand
                        if (hand.type.isAir) {
                            player.sendMessage(QuestPlugin.prefix + "§cメインハンドにアイテムを持ってください")
                            return
                        }
                        draft.rewardItems.add(hand.clone())
                        player.sendMessage(QuestPlugin.prefix + "§aアイテム報酬を追加しました: §f${hand.type.name}")
                    }
                    else -> player.sendMessage(QuestPlugin.prefix + "§c使用方法: /quest player set reward <money|item>")
                }
            }

            "maxaccept", "maxacceptors" -> {
                val max = args.getOrNull(3)?.toIntOrNull()?.takeIf { it > 0 } ?: run {
                    player.sendMessage(QuestPlugin.prefix + "§c正の整数を指定してください")
                    return
                }
                draft.maxAcceptors = max
                player.sendMessage(QuestPlugin.prefix + "§a最大受注人数を §e${max}人§a に設定しました")
            }

            else -> player.sendMessage(QuestPlugin.prefix + "§c不明なキー: $key")
        }
    }

    private fun showHelp(sender: Player) {
        sender.sendMessage("§6§l===== 民間クエスト コマンド =====")
        sender.sendMessage("§e/quest player board §7- 掲示板を開く")
        sender.sendMessage("§e/quest player create <タイトル> §7- 作成開始")
        sender.sendMessage("§e/quest player set type/target/amount/reward/maxaccept")
        sender.sendMessage("§e/quest player publish §7- 投稿")
        sender.sendMessage("§e/quest player list §7- 受注中一覧")
        sender.sendMessage("§e/quest player delete <id> §7- 削除")
        sender.sendMessage("§6§l=================================")
    }
}
