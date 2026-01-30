package com.woxloi.questplugin.commands.subcommands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.database.DatabaseManager

class QuestStorageCommand(private val plugin: QuestPlugin) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (args.size < 2) {
            // 現在のストレージモードを表示
            val currentMode = if (DatabaseManager.isEnabled()) "§aMySQL" else "§eYAML"
            sender.sendMessage(QuestPlugin.prefix + "§7現在のストレージモード: $currentMode")
            sender.sendMessage(QuestPlugin.prefix + "§7切り替えるには: §f/quest storage <mysql|yaml>")
            return true
        }

        val mode = args[1].lowercase()

        when (mode) {
            "mysql", "db", "database" -> {
                if (DatabaseManager.isEnabled()) {
                    sender.sendMessage(QuestPlugin.prefix + "§e§l既にMySQLモードで動作しています")
                    return true
                }

                sender.sendMessage(QuestPlugin.prefix + "§e§lconfig.ymlを編集してMySQLモードに切り替えてください")
                sender.sendMessage(QuestPlugin.prefix + "§7§l1. config.ymlを開く")
                sender.sendMessage(QuestPlugin.prefix + "§7§l2. database.enabled を true に設定")
                sender.sendMessage(QuestPlugin.prefix + "§7§l3. MySQL接続情報を設定")
                sender.sendMessage(QuestPlugin.prefix + "§7§l4. /quest reload を実行")
                sender.sendMessage(QuestPlugin.prefix + "§c§l※サーバー再起動を推奨します")
            }

            "yaml", "file" -> {
                if (!DatabaseManager.isEnabled()) {
                    sender.sendMessage(QuestPlugin.prefix + "§e§l既にYAMLモードで動作しています")
                    return true
                }

                sender.sendMessage(QuestPlugin.prefix + "§e§lconfig.ymlを編集してYAMLモードに切り替えてください")
                sender.sendMessage(QuestPlugin.prefix + "§7§l1. config.ymlを開く")
                sender.sendMessage(QuestPlugin.prefix + "§7§l2. database.enabled を false に設定")
                sender.sendMessage(QuestPlugin.prefix + "§7§l3. /quest reload を実行")
                sender.sendMessage(QuestPlugin.prefix + "§c§l※サーバー再起動を推奨します")
            }

            "status", "info" -> {
                displayStorageStatus(sender)
            }

            else -> {
                sender.sendMessage(QuestPlugin.prefix + "§c§l不明なストレージタイプです")
                sender.sendMessage(QuestPlugin.prefix + "§7§l使用可能: mysql, yaml, status")
            }
        }

        return true
    }

    /**
     * ストレージの詳細情報を表示
     */
    private fun displayStorageStatus(sender: CommandSender) {
        sender.sendMessage("§a§l========== ストレージ情報 ==========")

        val isDbEnabled = DatabaseManager.isEnabled()
        val currentMode = if (isDbEnabled) "§a§lMySQL" else "§e§lYAML"

        sender.sendMessage("§7§l現在のモード: $currentMode")

        if (isDbEnabled) {
            val config = plugin.config
            val host = config.getString("database.host", "localhost")
            val port = config.getInt("database.port", 3306)
            val database = config.getString("database.database", "minecraft")
            val username = config.getString("database.username", "root")

            sender.sendMessage("§7§l接続先: §f§l$host:$port")
            sender.sendMessage("§7§lデータベース: §f§l$database")
            sender.sendMessage("§7§lユーザー: §f§l$username")
            sender.sendMessage("§a§l✓ MySQLに接続されています")
        } else {
            sender.sendMessage("§7§lファイル保存: §f§lquest_histories.yml")
            sender.sendMessage("§e§l! YAMLファイルモードで動作中")
        }

        sender.sendMessage("§a§l===================================")
        sender.sendMessage("§7§lモード切り替え: §f§l/quest storage <mysql|yaml>")
    }
}