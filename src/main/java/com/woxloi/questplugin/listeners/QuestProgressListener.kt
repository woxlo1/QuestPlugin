package com.woxloi.questplugin.listeners

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.entity.EntityTameEvent
import org.bukkit.event.entity.EntityBreedEvent
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.entity.*
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.event.player.*
import org.bukkit.event.vehicle.VehicleEnterEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import com.woxloi.questplugin.ActiveQuestManager
import com.woxloi.questplugin.QuestType
import com.woxloi.questplugin.listeners.SmeltTracker.getRecentSmelter
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent
import java.util.*

class QuestProgressListener : Listener {
    // MythicMob討伐 (MYTHIC_KILL)
    @EventHandler
    fun onMythicMobKill(e: MythicMobDeathEvent) {
        val killer = e.killer as? Player ?: return
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(killer)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(killer) ?: return
        if (quest.type != QuestType.MYTHIC_KILL) return

        val targetMob = quest.target.lowercase()
        val killedMob = e.mobType.internalName.lowercase()

        if (targetMob != "any" && targetMob != killedMob) return

        com.woxloi.questplugin.ActiveQuestManager.addProgress(killer, 1)
    }
    // Mob討伐
    @EventHandler
    fun onEntityDeath(e: EntityDeathEvent) {
        val killer = e.entity.killer ?: return
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(killer)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(killer) ?: return
        if (quest.type != QuestType.KILL) return

        val targetName = quest.target.lowercase()
        val entityName = e.entity.type.name.lowercase()
        if (targetName != "any" && targetName != entityName) return

        com.woxloi.questplugin.ActiveQuestManager.addProgress(killer, 1)
    }

    // アイテム収集 (アイテムを拾う時)
    @EventHandler
    fun onPlayerPickupItem(e: PlayerPickupItemEvent) {
        val player = e.player
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: return
        if (quest.type != QuestType.COLLECT) return

        val targetItem = quest.target.lowercase()
        val itemName = e.item.itemStack.type.name.lowercase()
        if (targetItem != "any" && targetItem != itemName) return

        com.woxloi.questplugin.ActiveQuestManager.addProgress(player, e.item.itemStack.amount)
    }

    // 場所訪問 (TRAVEL)
    @EventHandler
    fun onPlayerMove(e: PlayerMoveEvent) {
        val player = e.player
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: return
        if (quest.type != QuestType.TRAVEL) return

        // targetは "world,x,y,z"形式を想定
        val parts = quest.target.split(",")
        if (parts.size != 4) return
        val worldName = parts[0]
        val targetLoc = Location(
            Bukkit.getWorld(worldName) ?: return,
            parts[1].toDoubleOrNull() ?: return,
            parts[2].toDoubleOrNull() ?: return,
            parts[3].toDoubleOrNull() ?: return
        )

        // プレイヤーが目的地に半径3ブロック以内に入ったら進行
        if (player.location.world?.name == targetLoc.world?.name) {
            if (player.location.distance(targetLoc) <= 3.0) {
                com.woxloi.questplugin.ActiveQuestManager.addProgress(player, 1)
            }
        }
    }

    // 採掘 (MINE)
    @EventHandler
    fun onBlockBreak(e: BlockBreakEvent) {
        val player = e.player
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: return
        if (quest.type != QuestType.MINE) return

        val targetBlock = quest.target.lowercase()
        val brokenBlock = e.block.type.name.lowercase()
        if (targetBlock != "any" && targetBlock != brokenBlock) return

        com.woxloi.questplugin.ActiveQuestManager.addProgress(player, 1)
    }

    // ブロック設置 (PLACE)
    @EventHandler
    fun onBlockPlace(e: BlockPlaceEvent) {
        val player = e.player
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: return
        if (quest.type != QuestType.PLACE) return

        val targetBlock = quest.target.lowercase()
        val placedBlock = e.block.type.name.lowercase()
        if (targetBlock != "any" && targetBlock != placedBlock) return

        com.woxloi.questplugin.ActiveQuestManager.addProgress(player, 1)
    }

    // ブロック破壊 (BREAK)
    @EventHandler
    fun onBlockBreakForBreakQuest(e: BlockBreakEvent) {
        val player = e.player
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: return
        if (quest.type != QuestType.BREAK) return

        val targetBlock = quest.target.lowercase()
        val brokenBlock = e.block.type.name.lowercase()
        if (targetBlock != "any" && targetBlock != brokenBlock) return

        com.woxloi.questplugin.ActiveQuestManager.addProgress(player, 1)
    }

    // クラフト (CRAFT)
    @EventHandler
    fun onCraftItem(e: CraftItemEvent) {
        val player = e.whoClicked as? Player ?: return
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: return
        if (quest.type != QuestType.CRAFT) return

        val targetItem = quest.target.lowercase()
        val craftedItem = e.currentItem?.type?.name?.lowercase() ?: return
        if (targetItem != "any" && targetItem != craftedItem) return

        com.woxloi.questplugin.ActiveQuestManager.addProgress(player, 1)
    }

    // 精錬 (SMELT)

    // 釣り (FISH)
    @EventHandler
    fun onFish(e: PlayerFishEvent) {
        val player = e.player
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: return
        if (quest.type != QuestType.FISH) return

        if (e.state == PlayerFishEvent.State.CAUGHT_FISH) {
            val targetItem = quest.target.lowercase()
            val caughtItem = e.caught?.type?.name?.lowercase() ?: return
            if (targetItem != "any" && targetItem != caughtItem) return

            com.woxloi.questplugin.ActiveQuestManager.addProgress(player, 1)
        }
    }

    // 寝る (SLEEP)
    @EventHandler
    fun onPlayerBedEnter(e: PlayerBedEnterEvent) {
        val player = e.player
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: return
        if (quest.type != QuestType.SLEEP) return

        com.woxloi.questplugin.ActiveQuestManager.addProgress(player, 1)
    }

    // チャット送信 (CHAT)
    @EventHandler
    fun onPlayerChat(e: AsyncPlayerChatEvent) {
        val player = e.player
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: return
        if (quest.type != QuestType.CHAT) return

        com.woxloi.questplugin.ActiveQuestManager.addProgress(player, 1)
    }

    // コマンド実行 (COMMAND)
    // コマンドが成功したかなどを判定するには別途管理が必要なのでここでは省略

    // 対話 (INTERACT)
    @EventHandler
    fun onPlayerInteract(e: PlayerInteractEvent) {
        val player = e.player
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: return
        if (quest.type != QuestType.INTERACT) return

        val target = quest.target.lowercase()
        val clickedBlock = e.clickedBlock?.type?.name?.lowercase()
        val clickedEntity = e.clickedBlock?.state?.javaClass?.simpleName?.lowercase() // クリックエンティティ判定は要拡張

        if (target != "any") {
            if (clickedBlock != target) return
        }

        com.woxloi.questplugin.ActiveQuestManager.addProgress(player, 1)
    }

    // ダメージを受ける (DAMAGE_TAKEN)
    @EventHandler
    fun onEntityDamage(e: EntityDamageEvent) {
        val entity = e.entity
        if (entity !is Player) return
        val player = entity
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: return
        if (quest.type != QuestType.DAMAGE_TAKEN) return

        com.woxloi.questplugin.ActiveQuestManager.addProgress(player, 1)
    }

    // ダメージを与える (DAMAGE_GIVEN)
    @EventHandler
    fun onEntityDamageByEntity(e: EntityDamageByEntityEvent) {
        val damager = e.damager
        if (damager !is Player) return
        val player = damager
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: return
        if (quest.type != QuestType.DAMAGE_GIVEN) return

        com.woxloi.questplugin.ActiveQuestManager.addProgress(player, e.damage.toInt())
    }

    // 弓で攻撃 (SHOOT)
    @EventHandler
    fun onProjectileHit(e: ProjectileHitEvent) {
        val shooter = e.entity.shooter
        if (shooter !is Player) return
        val player = shooter
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: return
        if (quest.type != QuestType.SHOOT) return

        com.woxloi.questplugin.ActiveQuestManager.addProgress(player, 1)
    }

    // レベル到達 (LEVEL)
    @EventHandler
    fun onPlayerLevelChange(e: PlayerLevelChangeEvent) {
        val player = e.player
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: return
        if (quest.type != QuestType.LEVEL) return

        val targetLevel = quest.target.toIntOrNull() ?: return
        if (e.newLevel >= targetLevel) {
            com.woxloi.questplugin.ActiveQuestManager.addProgress(player, 1)
        }
    }

    // 経験値獲得 (EXP_GAINED)
    @EventHandler
    fun onPlayerExpChange(e: PlayerExpChangeEvent) {
        val player = e.player
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: return
        if (quest.type != QuestType.EXP_GAINED) return

        val targetExp = quest.target.toIntOrNull() ?: return
        com.woxloi.questplugin.ActiveQuestManager.addProgress(player, e.amount)
    }

    // プレイ時間 (TIME_PLAYED)
    // 定期的にタイマー等で処理すべきためイベントなし（別途実装推奨）

    // 動物を手懐ける (TAME)
    @EventHandler
    fun onEntityTame(e: EntityTameEvent) {
        val owner = e.owner ?: return
        if (owner !is Player) return  // Playerでなければ無視
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(owner)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(owner) ?: return
        if (quest.type != QuestType.TAME) return

        val target = quest.target.lowercase()
        val tamed = e.entity.type.name.lowercase()
        if (target != "any" && target != tamed) return

        com.woxloi.questplugin.ActiveQuestManager.addProgress(owner, 1)
    }

    // 繁殖 (BREED)
    @EventHandler
    fun onEntityBreed(e: EntityBreedEvent) {
        val breeder = e.breeder ?: return
        if (breeder !is Player) return  // Playerでなければ無視
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(breeder)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(breeder) ?: return
        if (quest.type != QuestType.BREED) return

        val target = quest.target.lowercase()
        val bred = e.entity.type.name.lowercase()
        if (target != "any" && target != bred) return

        com.woxloi.questplugin.ActiveQuestManager.addProgress(breeder, 1)
    }

    // 村人と取引 (TRADE)
    @EventHandler
    fun onVillagerTrade(e: PlayerInteractEntityEvent) {
        val player = e.player
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: return
        if (quest.type != QuestType.TRADE) return

        // 取引対象が村人か確認
        val entityType = e.rightClicked.type
        if (entityType != EntityType.VILLAGER) return

        com.woxloi.questplugin.ActiveQuestManager.addProgress(player, 1)
    }

    // 乗り物に乗る (RIDE)
    @EventHandler
    fun onVehicleEnter(e: VehicleEnterEvent) {
        val entered = e.entered
        if (entered !is Player) return
        val player = entered
        if (!com.woxloi.questplugin.ActiveQuestManager.isQuesting(player)) return
        val quest = com.woxloi.questplugin.ActiveQuestManager.getQuest(player) ?: return
        if (quest.type != QuestType.RIDE) return

        val target = quest.target.lowercase()
        val vehicleType = e.vehicle.type.name.lowercase()
        if (target != "any" && target != vehicleType) return

        com.woxloi.questplugin.ActiveQuestManager.addProgress(player, 1)
    }
}
