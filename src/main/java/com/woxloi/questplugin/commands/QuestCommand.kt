package com.woxloi.questplugin.commands

import com.shojabon.mcutils.Utils.SCommandRouter.SCommandArgument
import com.shojabon.mcutils.Utils.SCommandRouter.SCommandArgumentType
import com.shojabon.mcutils.Utils.SCommandRouter.SCommandObject
import com.shojabon.mcutils.Utils.SCommandRouter.SCommandRouter
import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.QuestPlugin.Companion.plugin
import com.woxloi.questplugin.commands.subcommands.*

class QuestCommand : SCommandRouter() {

    val partyCommand = QuestPartyCommand()

    init {
        registerCommands()
        registerEvents()
        pluginPrefix = QuestPlugin.prefix
    }

    fun registerEvents() {
        setNoPermissionEvent { e -> e.sender.sendMessage(QuestPlugin.prefix + "§c§lあなたは権限がありません") }
        setOnNoCommandFoundEvent { e -> e.sender.sendMessage(QuestPlugin.prefix + "§c§lコマンドが存在しません") }
    }

    fun registerCommands() {

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("create"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addRequiredPermission("quest.config.create")
                .addExplanation("クエストを作成する")
                .setExecutor(QuestCreateCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("remove"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addRequiredPermission("quest.config.create")
                .addExplanation("クエストを削除する")
                .setExecutor(QuestRemoveCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("set"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addArgument(SCommandArgument().addAllowedString("name"))
                .addArgument(SCommandArgument().addAlias("内部名"))
                .addRequiredPermission("quest.config.name")
                .addExplanation("クエストの名前を設定する")
                .setExecutor(QuestSetCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("set"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addArgument(SCommandArgument().addAllowedString("type"))
                .addArgument(SCommandArgument().addAlias("タイプ名"))
                .addRequiredPermission("quest.config.set")
                .addExplanation("クエストタイプを設定する")
                .setExecutor(QuestSetCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("set"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addArgument(SCommandArgument().addAllowedString("target"))
                .addArgument(SCommandArgument().addAlias("ターゲット名"))
                .addRequiredPermission("quest.config.target")
                .addExplanation("クエストのターゲットを設定する")
                .setExecutor(QuestSetCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("set"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addArgument(SCommandArgument().addAllowedString("amount"))
                .addArgument(SCommandArgument().addAlias("個数"))
                .addRequiredPermission("quest.config.amount")
                .addExplanation("クエストクリア時に必要な個数を設定する")
                .setExecutor(QuestSetCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("set"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addArgument(SCommandArgument().addAllowedString("timelimit"))
                .addArgument(SCommandArgument().addAlias("時間"))
                .addRequiredPermission("quest.config.timelimit")
                .addExplanation("クエストの制限時間を設定する")
                .setExecutor(QuestSetCommand(plugin))
        )
        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("set"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addArgument(SCommandArgument().addAllowedString("cooldown"))
                .addArgument(SCommandArgument().addAlias("クールダウン秒数"))
                .addRequiredPermission("quest.config.cooldown")
                .addExplanation("クエストのクールダウン秒数を設定する")
                .setExecutor(QuestSetCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("set"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addArgument(SCommandArgument().addAllowedString("maxuse"))
                .addArgument(SCommandArgument().addAlias("最大使用回数"))
                .addRequiredPermission("quest.config.maxuse")
                .addExplanation("クエストの最大使用回数を設定する")
                .setExecutor(QuestSetCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("set"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addArgument(SCommandArgument().addAllowedString("maxlives"))
                .addArgument(SCommandArgument().addAlias("最大ライフ"))
                .addRequiredPermission("quest.config.maxlives")
                .addExplanation("クエストの最大ライフを設定する")
                .setExecutor(QuestSetCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("set"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addArgument(SCommandArgument().addAllowedString("partyenabled"))
                .addArgument(SCommandArgument().addAlias("有効/無効").addAllowedType(SCommandArgumentType.BOOLEAN))
                .addRequiredPermission("quest.config.partyenabled")
                .addExplanation("クエストのパーティー参加を有効/無効にする")
                .setExecutor(QuestSetCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("set"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addArgument(SCommandArgument().addAllowedString("shareprogress"))
                .addArgument(SCommandArgument().addAlias("有効/無効").addAllowedType(SCommandArgumentType.BOOLEAN))
                .addRequiredPermission("quest.config.shareprogress")
                .addExplanation("パーティーで進行状況を共有するか設定する")
                .setExecutor(QuestSetCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("set"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addArgument(SCommandArgument().addAllowedString("sharecompletion"))
                .addArgument(SCommandArgument().addAlias("有効/無効").addAllowedType(SCommandArgumentType.BOOLEAN))
                .addRequiredPermission("quest.config.sharecompletion")
                .addExplanation("パーティーで達成状態を共有するか設定する")
                .setExecutor(QuestSetCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("set"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addArgument(SCommandArgument().addAllowedString("partymaxmembers"))
                .addArgument(SCommandArgument().addAlias("パーティーメンバー最大数"))
                .addRequiredPermission("quest.config.partymaxmembers")
                .addExplanation("パーティーの最大メンバー数を設定する")
                .setExecutor(QuestSetCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
               .addArgument(SCommandArgument().addAllowedString("set"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addArgument(SCommandArgument().addAllowedString("teleportworld"))
                .addArgument(SCommandArgument().addAlias("テレポートワールド"))
                .addRequiredPermission("quest.config.teleportworld")
               .addExplanation("クエスト完了時のテレポート先ワールドを設定する")
               .setExecutor(QuestSetCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("set"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addArgument(SCommandArgument().addAllowedString("teleportx"))
                .addArgument(SCommandArgument().addAlias("テレポートX座標"))
                .addRequiredPermission("quest.config.teleportx")
                .addExplanation("クエスト完了時のテレポート先X座標を設定する")
                .setExecutor(QuestSetCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("set"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addArgument(SCommandArgument().addAllowedString("teleporty"))
                .addArgument(SCommandArgument().addAlias("テレポートY座標"))
                .addRequiredPermission("quest.config.teleporty")
                .addExplanation("クエスト完了時のテレポート先Y座標を設定する")
                .setExecutor(QuestSetCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("set"))
                .addArgument(SCommandArgument().addAlias("クエスト名"))
                .addArgument(SCommandArgument().addAllowedString("teleportz"))
                .addArgument(SCommandArgument().addAlias("テレポートZ座標"))
                .addRequiredPermission("quest.config.teleportz")
                .addExplanation("クエスト完了時のテレポート先Z座標を設定する")
                .setExecutor(QuestSetCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("config"))
                .addArgument(SCommandArgument().addAllowedString("save"))
                .addRequiredPermission("quest.config.save")
                .addExplanation("クエスト設定を保存する")
                .setExecutor(QuestSaveConfigCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("deposit"))
                .addArgument(SCommandArgument().addAlias("プレイヤー名").addAllowedType(SCommandArgumentType.ONLINE_PLAYER))
                .addArgument(SCommandArgument().addAlias("金額"))
                .addRequiredPermission("quest.money")
                .addExplanation("指定プレイヤーにお金を付与する")
                .setExecutor(QuestDepositCommand())
        )
        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("withdraw"))
                .addArgument(SCommandArgument().addAlias("プレイヤー名").addAllowedType(SCommandArgumentType.ONLINE_PLAYER))
                .addArgument(SCommandArgument().addAlias("金額"))
                .addRequiredPermission("quest.money")
                .addExplanation("指定プレイヤーからお金を引き出す")
                .setExecutor(QuestWithdrawCommand())
        )
        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("info"))
                .addArgument(SCommandArgument().addAlias("クエストID"))
                .addRequiredPermission("quest.info")
                .addExplanation("クエストの詳細を見る")
                .setExecutor(QuestInfoCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("leave"))
                .addRequiredPermission("quest.leave")
                .addExplanation("クエストを中断する")
                .setExecutor(QuestLeaveCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("party"))
                .addArgument(SCommandArgument().addAllowedString("create"))
                .addRequiredPermission("quest.party")
                .addExplanation("パーティーを作成する")
                .setExecutor(partyCommand)
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("party"))
                .addArgument(SCommandArgument().addAllowedString("invite"))
                .addArgument(SCommandArgument().addAlias("プレイヤー名").addAllowedType(SCommandArgumentType.ONLINE_PLAYER))
                .addRequiredPermission("quest.party")
                .addExplanation("プレイヤーをパーティーに招待する")
                .setExecutor(partyCommand)
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("party"))
                .addArgument(SCommandArgument().addAllowedString("join"))
                .addArgument(SCommandArgument().addAlias("プレイヤー名").addAllowedType(SCommandArgumentType.ONLINE_PLAYER))
                .addRequiredPermission("quest.party")
                .addExplanation("指定プレイヤーのパーティーに参加する")
                .setExecutor(partyCommand)
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("party"))
                .addArgument(SCommandArgument().addAllowedString("leave"))
                .addRequiredPermission("quest.party")
                .addExplanation("パーティーを離脱する")
                .setExecutor(partyCommand)
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("party"))
                .addArgument(SCommandArgument().addAllowedString("disband"))
                .addRequiredPermission("quest.party")
                .addExplanation("パーティーを解散する（リーダーのみ）")
                .setExecutor(partyCommand)
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("party"))
                .addArgument(SCommandArgument().addAllowedString("kick"))
                .addArgument(SCommandArgument().addAlias("プレイヤー名").addAllowedType(SCommandArgumentType.ONLINE_PLAYER))
                .addRequiredPermission("quest.party")
                .addExplanation("パーティーからプレイヤーを追放する")
                .setExecutor(partyCommand)
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("party"))
                .addRequiredPermission("quest.party")
                .addExplanation("パーティー情報を表示する")
                .setExecutor(partyCommand)
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("reload"))
                .addRequiredPermission("quest.reload")
                .addExplanation("クエスト設定を再読み込みする")
                .setExecutor(QuestReloadCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("start"))
                .addArgument(SCommandArgument().addAlias("クエストID"))
                .addRequiredPermission("quest.start")
                .addExplanation("クエストを開始する")
                .setExecutor(QuestStartCommand(plugin))
        )

        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("list"))
                .addRequiredPermission("quest.list")
                .addExplanation("現在使用可能なクエストを見る")
                .setExecutor(QuestListCommand(plugin))
        )
        addCommand(
            SCommandObject()
                .addArgument(SCommandArgument().addAllowedString("logop"))
                .addArgument(SCommandArgument().addAlias("プレイヤー名").addAllowedType(SCommandArgumentType.ONLINE_PLAYER))
                .addArgument(SCommandArgument().addAlias("ページ数"))
                .addRequiredPermission("quest.logop")
                .addExplanation("指定したプレイヤーの履歴を見る")
                .setExecutor(QuestLogOpCommand(plugin))
        )

    }
}
