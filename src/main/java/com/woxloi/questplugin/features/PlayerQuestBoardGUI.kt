package com.woxloi.questplugin.features

import com.shojabon.mcutils.Utils.SInventory.SInventory
import com.shojabon.mcutils.Utils.SInventory.SInventoryItem
import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.manager.PlayerQuestManager
import com.woxloi.questplugin.model.PlayerQuest
import com.woxloi.questplugin.model.QuestType
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.inventory.ItemStack

// ─────────────────────────────────────────────
//  民間クエスト掲示板GUI（SInventory使用）
// ─────────────────────────────────────────────
class PlayerQuestBoardGUI(
    plugin: QuestPlugin,
    private val viewer: Player,
    private val page: Int = 0
) : SInventory("§6§l民間クエスト掲示板", 6, plugin) {

    companion object {
        private const val PAGE_SIZE = 45
        fun open(player: Player, page: Int = 0) {
            PlayerQuestBoardGUI(QuestPlugin.plugin, player, page).open(player)
        }
    }

    override fun renderMenu() {
        clear()
        val quests = PlayerQuestManager.getAllOpenQuests().sortedByDescending { it.createdAt }
        val totalPages = ((quests.size - 1) / PAGE_SIZE).coerceAtLeast(0)
        val currentPage = page.coerceIn(0, totalPages)
        val pageQuests = quests.drop(currentPage * PAGE_SIZE).take(PAGE_SIZE)

        pageQuests.forEachIndexed { i, quest -> setItem(i, buildQuestItem(quest)) }

        val filler = filler()
        for (i in 45..53) setItem(i, filler)

        if (currentPage > 0)
            setItem(45, SInventoryItem(nav(Material.ARROW, "§e前のページ", "§7クリックで戻る"))
                .setEvent { open(viewer, currentPage - 1) })

        setItem(47, SInventoryItem(nav(Material.PAPER,
            "§fページ: ${currentPage + 1}/${totalPages + 1}", "§7全${quests.size}件")).clickable(false))

        setItem(49, SInventoryItem(nav(Material.BOOK, "§b自分のクエスト", "§7作成したクエストを管理"))
            .setEvent { PlayerQuestMyListGUI(QuestPlugin.plugin, viewer).open(viewer) })

        setItem(51, SInventoryItem(nav(Material.WRITABLE_BOOK, "§a新規クエスト作成", "§7GUIで作成"))
            .setEvent { PlayerQuestCreateGUI(QuestPlugin.plugin, viewer).open(viewer) })

        if (currentPage < totalPages)
            setItem(53, SInventoryItem(nav(Material.ARROW, "§e次のページ", "§7クリックで進む"))
                .setEvent { open(viewer, currentPage + 1) })
    }

    private fun buildQuestItem(quest: PlayerQuest): SInventoryItem {
        val mat = typeMat(quest.type)
        val item = ItemStack(mat)
        val meta = item.itemMeta
        meta.displayName(Component.text("§e§l${quest.title}"))
        val isAccepting = PlayerQuestManager.isAccepting(viewer, quest.id)
        val lore = mutableListOf<Component>().apply {
            add(Component.text("§7依頼者: §f${quest.creatorName}"))
            add(Component.text("§7種別: §f${quest.type.displayName}"))
            add(Component.text("§7目標: §f${quest.target} x${quest.amount}"))
            add(Component.text(""))
            if (quest.rewardMoney > 0) add(Component.text("§6報酬金: §f${quest.rewardMoney}円"))
            if (quest.rewardItems.isNotEmpty()) add(Component.text("§aアイテム報酬: §f${quest.rewardItems.size}種"))
            add(Component.text(""))
            add(Component.text("§7受注中: §f${quest.currentAcceptors.size}/${quest.maxAcceptors}人"))
            if (isAccepting) {
                val progress = PlayerQuestManager.getProgress(viewer, quest.id)
                add(Component.text("§a進行中: §f$progress/${quest.amount}"))
                add(Component.text(""))
                add(Component.text("§c§lクリックでキャンセル"))
            } else {
                add(Component.text(""))
                add(Component.text("§a§lクリックで受注"))
            }
        }
        meta.lore(lore)
        item.itemMeta = meta
        return SInventoryItem(item).setEvent { e ->
            e.isCancelled = true
            if (isAccepting) {
                PlayerQuestManager.cancelAccept(viewer, quest.id)
                viewer.sendMessage(QuestPlugin.prefix + "§c受注をキャンセルしました: §f${quest.title}")
                open(viewer, page)
            } else {
                PlayerQuestManager.acceptQuest(viewer, quest.id)
                    .onSuccess {
                        viewer.sendMessage(QuestPlugin.prefix + "§a§lクエストを受注しました: §e§l${quest.title}")
                        viewer.sendMessage(QuestPlugin.prefix + "§7目標: §f${quest.target} x${quest.amount}")
                        close(viewer)
                    }
                    .onFailure { viewer.sendMessage(QuestPlugin.prefix + "§c§l${it.message}") }
            }
        }
    }

    private fun filler(): SInventoryItem {
        val i = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val m = i.itemMeta; m.displayName(Component.text(" ")); i.itemMeta = m
        return SInventoryItem(i).clickable(false)
    }
    private fun nav(mat: Material, name: String, desc: String): ItemStack {
        val i = ItemStack(mat); val m = i.itemMeta
        m.displayName(Component.text(name)); m.lore(listOf(Component.text(desc))); i.itemMeta = m; return i
    }
    private fun typeMat(t: QuestType) = when (t) {
        QuestType.KILL, QuestType.MYTHIC_KILL -> Material.IRON_SWORD
        QuestType.COLLECT -> Material.CHEST
        QuestType.MINE, QuestType.BREAK -> Material.DIAMOND_PICKAXE
        QuestType.PLACE -> Material.BRICKS
        QuestType.CRAFT -> Material.CRAFTING_TABLE
        QuestType.SMELT -> Material.FURNACE
        QuestType.FISH -> Material.FISHING_ROD
        QuestType.TRAVEL -> Material.COMPASS
        else -> Material.MAP
    }
}

// ─────────────────────────────────────────────
//  自分のクエスト一覧GUI
// ─────────────────────────────────────────────
class PlayerQuestMyListGUI(
    plugin: QuestPlugin,
    private val viewer: Player
) : SInventory("§b§l自分の民間クエスト", 6, plugin) {

    override fun renderMenu() {
        clear()
        val myQuests = PlayerQuestManager.getQuestsByCreator(viewer.uniqueId)

        if (myQuests.isEmpty()) {
            val empty = ItemStack(Material.BARRIER)
            val m = empty.itemMeta
            m.displayName(Component.text("§7作成したクエストはありません"))
            empty.itemMeta = m
            setItem(22, SInventoryItem(empty).clickable(false))
        }

        myQuests.take(45).forEachIndexed { i, quest ->
            val mat = if (quest.isOpen) Material.GREEN_WOOL else Material.RED_WOOL
            val item = ItemStack(mat); val meta = item.itemMeta
            meta.displayName(Component.text("§e§l${quest.title}"))
            meta.lore(listOf(
                Component.text("§7状態: ${if (quest.isOpen) "§a受注可能" else "§c停止中"}"),
                Component.text("§7受注: §f${quest.currentAcceptors.size}人 §7/ 完了: §f${quest.completedBy.size}人"),
                Component.text("§7タイプ: §f${quest.type.displayName} §7/ 目標: §f${quest.target} x${quest.amount}"),
                Component.text("§7金銭報酬: §f${quest.rewardMoney}円"),
                Component.text(""),
                if (quest.currentAcceptors.isEmpty())
                    Component.text("§c§lクリックで削除（返金あり）")
                else
                    Component.text("§8受注中のため削除不可")
            ))
            item.itemMeta = meta
            setItem(i, SInventoryItem(item).setEvent { e ->
                e.isCancelled = true
                PlayerQuestManager.deleteQuest(viewer, quest.id)
                    .onSuccess {
                        viewer.sendMessage(QuestPlugin.prefix + "§a§lクエストを削除しました")
                        PlayerQuestMyListGUI(QuestPlugin.plugin, viewer).open(viewer)
                    }
                    .onFailure { viewer.sendMessage(QuestPlugin.prefix + "§c§l${it.message}") }
            })
        }

        val back = ItemStack(Material.ARROW); val bm = back.itemMeta
        bm.displayName(Component.text("§e掲示板に戻る")); back.itemMeta = bm
        setItem(49, SInventoryItem(back).setEvent { PlayerQuestBoardGUI.open(viewer) })
    }
}

// ─────────────────────────────────────────────
//  クエスト作成GUI（SInventory使用 / チャット入力式）
// ─────────────────────────────────────────────
class PlayerQuestCreateGUI(
    plugin: QuestPlugin,
    private val creator: Player
) : SInventory("§a§lクエスト作成", 6, plugin) {

    private val allTypes = QuestType.values().toList()

    override fun renderMenu() {
        clear()
        val draft = PlayerQuestManager.getDraft(creator)
            ?: PlayerQuestManager.startDraft(creator, "")

        // ── タイプ選択（左右矢印） ──
        val typeIdx = allTypes.indexOf(draft.type).coerceAtLeast(0)
        setItem(10, SInventoryItem(arrow("§e◀ 前のタイプ")).setEvent { e ->
            e.isCancelled = true
            draft.type = allTypes[(typeIdx - 1 + allTypes.size) % allTypes.size]
            PlayerQuestCreateGUI(QuestPlugin.plugin, creator).open(creator)
        })
        val typeDisp = ItemStack(Material.MAP)
        typeDisp.itemMeta = typeDisp.itemMeta.also {
            it.displayName(Component.text("§bクエストタイプ: §f${draft.type.displayName}"))
            it.lore(listOf(Component.text("§7◀ ▶ で変更")))
        }
        setItem(11, SInventoryItem(typeDisp).clickable(false))
        setItem(12, SInventoryItem(arrow("§e次のタイプ ▶")).setEvent { e ->
            e.isCancelled = true
            draft.type = allTypes[(typeIdx + 1) % allTypes.size]
            PlayerQuestCreateGUI(QuestPlugin.plugin, creator).open(creator)
        })

        // ── 各設定項目 ──
        setItem(20, inputBtn(Material.OAK_SIGN,    "§bタイトル",
            draft.title.ifBlank { "§7未設定" }) {
            awaitChat(creator, "title", "タイトルをチャットで入力してください")
        })
        setItem(22, inputBtn(Material.NAME_TAG,     "§bターゲット",
            draft.target.ifBlank { "§7未設定 (例: ZOMBIE)" }) {
            awaitChat(creator, "target", "ターゲットをチャットで入力してください (例: ZOMBIE)")
        })
        setItem(24, inputBtn(Material.COMPARATOR,   "§b必要数",
            "${draft.amount}個") {
            awaitChat(creator, "amount", "必要数を入力してください (例: 10)")
        })
        setItem(29, inputBtn(Material.GOLD_INGOT,   "§b金銭報酬",
            if (draft.rewardMoney > 0) "${draft.rewardMoney}円" else "§7なし") {
            awaitChat(creator, "money", "金銭報酬額を入力してください (0でなし)")
        })
        setItem(31, inputBtn(Material.CHEST,        "§bアイテム報酬追加",
            if (draft.rewardItems.isNotEmpty()) "${draft.rewardItems.size}種登録済み" else "§7手持ちアイテムをクリックで追加") {
            val hand = creator.inventory.itemInMainHand
            if (hand.type.isAir) {
                creator.sendMessage(QuestPlugin.prefix + "§cメインハンドにアイテムを持ってください")
                PlayerQuestCreateGUI(QuestPlugin.plugin, creator).open(creator)
            } else {
                draft.rewardItems.add(hand.clone())
                creator.sendMessage(QuestPlugin.prefix + "§aアイテム報酬を追加: §f${hand.type.name}")
                PlayerQuestCreateGUI(QuestPlugin.plugin, creator).open(creator)
            }
        })
        setItem(33, inputBtn(Material.PLAYER_HEAD,  "§b最大受注人数",
            "${draft.maxAcceptors}人") {
            awaitChat(creator, "maxaccept", "最大受注人数を入力してください")
        })

        // ── プレビュー ──
        val preview = ItemStack(Material.WRITABLE_BOOK)
        preview.itemMeta = preview.itemMeta.also {
            it.displayName(Component.text("§f§l現在の設定"))
            it.lore(listOf(
                Component.text("§7タイトル: §f${draft.title.ifBlank { "未設定" }}"),
                Component.text("§7タイプ: §f${draft.type.displayName}"),
                Component.text("§7ターゲット: §f${draft.target.ifBlank { "未設定" }}"),
                Component.text("§7必要数: §f${draft.amount}"),
                Component.text("§7金銭報酬: §f${draft.rewardMoney}円"),
                Component.text("§7アイテム報酬: §f${draft.rewardItems.size}種"),
                Component.text("§7最大受注: §f${draft.maxAcceptors}人")
            ))
        }
        setItem(40, SInventoryItem(preview).clickable(false))

        // ── キャンセル ──
        val cancel = ItemStack(Material.BARRIER)
        cancel.itemMeta = cancel.itemMeta.also {
            it.displayName(Component.text("§c§lキャンセル")); it.lore(listOf(Component.text("§7作成を中止")))
        }
        setItem(45, SInventoryItem(cancel).setEvent { e ->
            e.isCancelled = true
            PlayerQuestManager.clearDraft(creator)
            creator.sendMessage(QuestPlugin.prefix + "§e§lクエスト作成をキャンセルしました")
            PlayerQuestBoardGUI.open(creator)
        })

        // ── 投稿ボタン ──
        val canPublish = draft.title.isNotBlank() && draft.target.isNotBlank()
        val pub = ItemStack(if (canPublish) Material.EMERALD_BLOCK else Material.REDSTONE_BLOCK)
        pub.itemMeta = pub.itemMeta.also {
            it.displayName(Component.text(if (canPublish) "§a§l掲示板に投稿" else "§c§l投稿不可（未設定項目あり）"))
            val l = mutableListOf<Component>()
            if (!canPublish) l += Component.text("§7タイトルとターゲットを設定してください")
            if (draft.rewardMoney > 0) l += Component.text("§c§l※ ${draft.rewardMoney}円がデポジットされます")
            it.lore(l)
        }
        setItem(53, SInventoryItem(pub).setEvent { e ->
            e.isCancelled = true
            if (!canPublish) {
                creator.sendMessage(QuestPlugin.prefix + "§cタイトルとターゲットを設定してください")
                return@setEvent
            }
            PlayerQuestManager.publishDraft(creator)
                .onSuccess { q ->
                    creator.sendMessage(QuestPlugin.prefix + "§a§l掲示板に投稿しました！ ID: §f${q.id}")
                    PlayerQuestBoardGUI.open(creator)
                }
                .onFailure { creator.sendMessage(QuestPlugin.prefix + "§c§l投稿失敗: ${it.message}") }
        })
    }

    // ── ヘルパー ──

    private fun arrow(name: String): ItemStack {
        val i = ItemStack(Material.ARROW); val m = i.itemMeta
        m.displayName(Component.text(name)); i.itemMeta = m; return i
    }

    private fun inputBtn(mat: Material, name: String, value: String, onClick: () -> Unit): SInventoryItem {
        val item = ItemStack(mat); val meta = item.itemMeta
        meta.displayName(Component.text(name))
        meta.lore(listOf(Component.text("§f$value"), Component.text(""), Component.text("§7クリックでチャット入力")))
        item.itemMeta = meta
        return SInventoryItem(item).setEvent { e ->
            e.isCancelled = true
            close(creator)
            onClick()
        }
    }

    private fun awaitChat(player: Player, field: String, prompt: String) {
        val plugin = QuestPlugin.plugin
        player.sendMessage(QuestPlugin.prefix + "§a$prompt")
        player.sendMessage(QuestPlugin.prefix + "§7キャンセル: §fcancel と入力")

        val listener = object : org.bukkit.event.Listener {
            @org.bukkit.event.EventHandler
            fun onChat(e: AsyncPlayerChatEvent) {
                if (e.player.uniqueId != player.uniqueId) return
                e.isCancelled = true
                val msg = e.message.trim()
                org.bukkit.event.HandlerList.unregisterAll(this)

                if (msg.equals("cancel", ignoreCase = true)) {
                    player.sendMessage(QuestPlugin.prefix + "§e入力をキャンセルしました")
                } else {
                    val draft = PlayerQuestManager.getDraft(player)
                    if (draft != null) applyField(player, draft, field, msg)
                }

                plugin.server.scheduler.runTask(plugin, Runnable {
                    PlayerQuestCreateGUI(plugin, player).open(player)
                })
            }
        }
        plugin.server.pluginManager.registerEvents(listener, plugin)
    }

    private fun applyField(player: Player, draft: PlayerQuest, field: String, msg: String) {
        when (field) {
            "title" -> { draft.title = msg; player.sendMessage(QuestPlugin.prefix + "§aタイトル: §f$msg") }
            "target" -> { draft.target = msg.uppercase(); player.sendMessage(QuestPlugin.prefix + "§aターゲット: §f${draft.target}") }
            "amount" -> {
                val v = msg.toIntOrNull()?.takeIf { it > 0 }
                if (v == null) player.sendMessage(QuestPlugin.prefix + "§c正の整数を入力してください")
                else { draft.amount = v; player.sendMessage(QuestPlugin.prefix + "§a必要数: §f${v}個") }
            }
            "money" -> {
                val v = msg.toDoubleOrNull()?.takeIf { it >= 0 }
                if (v == null) player.sendMessage(QuestPlugin.prefix + "§c0以上の数値を入力してください")
                else { draft.rewardMoney = v; player.sendMessage(QuestPlugin.prefix + "§a金銭報酬: §f${v}円") }
            }
            "maxaccept" -> {
                val v = msg.toIntOrNull()?.takeIf { it > 0 }
                if (v == null) player.sendMessage(QuestPlugin.prefix + "§c正の整数を入力してください")
                else { draft.maxAcceptors = v; player.sendMessage(QuestPlugin.prefix + "§a最大受注人数: §f${v}人") }
            }
        }
    }
}
