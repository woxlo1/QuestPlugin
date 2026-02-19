package com.woxloi.questplugin.features

import com.shojabon.mcutils.Utils.SInventory.SInventory
import com.shojabon.mcutils.Utils.SInventory.SInventoryItem
import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.manager.ActiveQuestManager
import com.woxloi.questplugin.manager.QuestConfigManager
import com.woxloi.questplugin.model.QuestData
import com.woxloi.questplugin.model.QuestType
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * /quest gui で開くクエスト一覧GUI（SInventory使用）
 */
class QuestListGUI(
    plugin: QuestPlugin,
    private val viewer: Player,
    private val page: Int = 0
) : SInventory("§6§lクエスト一覧", 6, plugin) {

    companion object {
        fun open(player: Player, page: Int = 0) {
            QuestListGUI(QuestPlugin.plugin, player, page).open(player)
        }
    }

    override fun renderMenu() {
        clear()

        val quests = QuestConfigManager.getAllQuests().toList()
        val totalPages = ((quests.size - 1) / 45).coerceAtLeast(0)
        val currentPage = page.coerceIn(0, totalPages)

        val pageQuests = quests.drop(currentPage * 45).take(45)

        pageQuests.forEachIndexed { i, quest ->
            setItem(i, buildQuestItem(quest))
        }

        // フィラー（下段）
        val filler = buildFiller()
        for (i in 45..53) setItem(i, filler)

        // 前ページ
        if (currentPage > 0) {
            setItem(45, SInventoryItem(buildNav(Material.ARROW, "§e前のページ"))
                .setEvent { open(viewer, currentPage - 1) })
        }

        // ページ表示
        setItem(48, SInventoryItem(buildNav(Material.PAPER, "§fページ ${currentPage + 1}/${totalPages + 1}"))
            .clickable(false))

        // 次ページ
        if (currentPage < totalPages) {
            setItem(53, SInventoryItem(buildNav(Material.ARROW, "§e次のページ"))
                .setEvent { open(viewer, currentPage + 1) })
        }
    }

    private fun buildQuestItem(quest: QuestData): SInventoryItem {
        val isActive = ActiveQuestManager.isQuesting(viewer) &&
                ActiveQuestManager.getQuest(viewer)?.id == quest.id

        val mat = when {
            isActive -> Material.LIME_STAINED_GLASS_PANE
            else -> when (quest.type) {
                QuestType.KILL, QuestType.MYTHIC_KILL -> Material.IRON_SWORD
                QuestType.COLLECT -> Material.CHEST
                QuestType.MINE -> Material.DIAMOND_PICKAXE
                QuestType.PLACE -> Material.BRICKS
                QuestType.CRAFT -> Material.CRAFTING_TABLE
                QuestType.SMELT -> Material.FURNACE
                QuestType.FISH -> Material.FISHING_ROD
                QuestType.TRAVEL -> Material.COMPASS
                QuestType.BREAK -> Material.STONE
                else -> Material.MAP
            }
        }

        val item = ItemStack(mat)
        val meta = item.itemMeta
        meta.displayName(Component.text("§e§l${quest.name} §7[${quest.id}]"))

        val lore = mutableListOf<Component>()
        lore += Component.text("§7タイプ: §f${quest.type.displayName}")
        lore += Component.text("§7目標: §f${quest.target} x${quest.amount}")
        quest.timeLimitSeconds?.let { lore += Component.text("§c制限時間: §f${it}秒") }
        quest.cooldownSeconds?.let { lore += Component.text("§eクールダウン: §f${it}秒") }
        quest.maxUseCount?.let { lore += Component.text("§e最大使用回数: §f${it}回") }
        if (quest.partyEnabled) lore += Component.text("§bパーティー対応")
        lore += Component.text("")
        if (isActive) {
            val data = ActiveQuestManager.getPlayerData(viewer.uniqueId)
            lore += Component.text("§a§l進行中: ${data?.progress ?: 0}/${quest.amount}")
        } else {
            lore += Component.text("§a§lクリックで開始")
        }

        meta.lore(lore)
        item.itemMeta = meta

        return SInventoryItem(item).setEvent { e ->
            e.isCancelled = true
            if (isActive) return@setEvent
            if (ActiveQuestManager.isQuesting(viewer)) {
                viewer.sendMessage(QuestPlugin.prefix + "§c§l既にクエストを進行中です")
                return@setEvent
            }
            close(viewer)
            val success = ActiveQuestManager.startQuest(viewer, quest)
            if (success) {
                viewer.sendMessage(QuestPlugin.prefix + "§e§l${quest.name}§a§lを開始しました！")
            } else {
                viewer.sendMessage(QuestPlugin.prefix + "§c§lクエストを開始できませんでした")
            }
        }
    }

    private fun buildFiller(): SInventoryItem {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta
        meta.displayName(Component.text(" "))
        item.itemMeta = meta
        return SInventoryItem(item).clickable(false)
    }

    private fun buildNav(mat: Material, name: String): ItemStack {
        val item = ItemStack(mat)
        val meta = item.itemMeta
        meta.displayName(Component.text(name))
        item.itemMeta = meta
        return item
    }
}
