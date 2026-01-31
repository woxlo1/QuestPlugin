package com.woxloi.questplugin.features

import com.woxloi.questplugin.model.QuestData
import com.woxloi.questplugin.QuestPlugin
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import java.io.File

/**
 *  * JavaScriptスクリプトエンジン（GraalVM使用）
 *
 * クエストの動的な振る舞いをJavaScriptで定義できます
 */
object QuestScriptEngine {

    private val scriptFolder = File(QuestPlugin.plugin.dataFolder, "quest_scripts")
    private val contexts = mutableMapOf<String, Context>()
    private val questScripts = mutableMapOf<String, QuestScript>()

    fun init() {
        if (!scriptFolder.exists()) {
            scriptFolder.mkdirs()
            createExampleScripts()
        }

        loadAllScripts()
    }

    /**
     * 全スクリプトを読み込み
     */
    private fun loadAllScripts() {
        scriptFolder.listFiles()?.forEach { file ->
            if (file.extension == "js") {
                loadScript(file)
            }
        }

        QuestPlugin.plugin.logger.info("[QuestScript] ${questScripts.size}個のスクリプトを読み込みました")
    }

    /**
     * スクリプトファイルを読み込み
     */
    fun loadScript(file: File) {
        try {
            val questId = file.nameWithoutExtension
            val context = Context.newBuilder("js")
                .allowAllAccess(true)
                .build()

            // グローバル関数を登録
            registerGlobalFunctions(context)

            // スクリプト実行
            val scriptContent = file.readText()
            context.eval("js", scriptContent)

            // イベントハンドラを取得
            val script = QuestScript(
                questId = questId,
                context = context,
                onStart = context.getBindings("js").getMember("onStart"),
                onProgress = context.getBindings("js").getMember("onProgress"),
                onComplete = context.getBindings("js").getMember("onComplete"),
                onFail = context.getBindings("js").getMember("onFail")
            )

            questScripts[questId] = script
            contexts[questId] = context

            QuestPlugin.plugin.logger.info("[QuestScript] ${file.name} を読み込みました")

        } catch (e: Exception) {
            QuestPlugin.plugin.logger.severe("[QuestScript] ${file.name} の読み込みに失敗: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * グローバル関数の登録
     */
    private fun registerGlobalFunctions(context: Context) {
        val bindings = context.getBindings("js")

        // quest オブジェクト
        val questObj = ScriptQuestAPI()
        bindings.putMember("quest", questObj)

        // ユーティリティ関数
        bindings.putMember("summonBoss", ScriptUtilities::summonBoss)
        bindings.putMember("fireworks", ScriptUtilities::fireworks)
        bindings.putMember("broadcastMessage", ScriptUtilities::broadcastMessage)
        bindings.putMember("giveItem", ScriptUtilities::giveItem)
        bindings.putMember("playSound", ScriptUtilities::playSound)
        bindings.putMember("spawnParticles", ScriptUtilities::spawnParticles)
    }

    /**
     * クエスト開始時のスクリプト実行
     */
    fun onQuestStart(player: Player, quest: QuestData) {
        val script = questScripts[quest.id] ?: return

        if (script.onStart != null && script.onStart.canExecute()) {
            try {
                script.onStart.execute(PlayerWrapper(player))
            } catch (e: Exception) {
                QuestPlugin.plugin.logger.warning("[QuestScript] onStart実行エラー: ${e.message}")
            }
        }
    }

    /**
     * 進行度更新時のスクリプト実行
     */
    fun onQuestProgress(player: Player, quest: QuestData, amount: Int) {
        val script = questScripts[quest.id] ?: return

        if (script.onProgress != null && script.onProgress.canExecute()) {
            try {
                script.onProgress.execute(PlayerWrapper(player), amount)
            } catch (e: Exception) {
                QuestPlugin.plugin.logger.warning("[QuestScript] onProgress実行エラー: ${e.message}")
            }
        }
    }

    /**
     * クエスト完了時のスクリプト実行
     */
    fun onQuestComplete(player: Player, quest: QuestData) {
        val script = questScripts[quest.id] ?: return

        if (script.onComplete != null && script.onComplete.canExecute()) {
            try {
                script.onComplete.execute(PlayerWrapper(player))
            } catch (e: Exception) {
                QuestPlugin.plugin.logger.warning("[QuestScript] onComplete実行エラー: ${e.message}")
            }
        }
    }

    /**
     * クエスト失敗時のスクリプト実行
     */
    fun onQuestFail(player: Player, quest: QuestData) {
        val script = questScripts[quest.id] ?: return

        if (script.onFail != null && script.onFail.canExecute()) {
            try {
                script.onFail.execute(PlayerWrapper(player))
            } catch (e: Exception) {
                QuestPlugin.plugin.logger.warning("[QuestScript] onFail実行エラー: ${e.message}")
            }
        }
    }

    /**
     * サンプルスクリプトを作成
     */
    private fun createExampleScripts() {
        // ドラゴン討伐スクリプト
        val dragonScript = File(scriptFolder, "dragon_slayer.js")
        dragonScript.writeText("""
// ドラゴン討伐クエストのスクリプト例

quest.onStart = function(player) {
    player.sendMessage("§c§lドラゴンが現れた！");
    player.addPotionEffect("FIRE_RESISTANCE", 6000, 1);
    
    // ボスを召喚
    summonBoss(player.getLocation(), "ENDER_DRAGON");
    
    // サウンド再生
    playSound(player, "ENTITY_ENDER_DRAGON_GROWL", 1.0, 1.0);
};

quest.onProgress = function(player, amount) {
    if (amount >= 1) {
        player.sendMessage("§a§lドラゴンに大ダメージを与えた！");
        player.addPotionEffect("STRENGTH", 600, 2);
    }
    
    if (amount >= 50) {
        player.sendMessage("§e§lドラゴンが弱ってきた！");
        fireworks(player.getLocation(), 3);
    }
};

quest.onComplete = function(player) {
    player.sendMessage("§6§l§k|||§r §6§lドラゴンを倒した！ §k|||");
    
    // 花火を打ち上げ
    fireworks(player.getLocation(), 10);
    
    // 全体通知
    broadcastMessage("§e" + player.getName() + " §7がドラゴンを倒した！");
    
    // 特別アイテム付与
    giveItem(player, "DIAMOND_SWORD", 1, "§b§lドラゴンスレイヤー", [
        "§7伝説のドラゴンを倒した証",
        "§c攻撃力 +10"
    ]);
    
    // エフェクト
    spawnParticles(player.getLocation(), "DRAGON_BREATH", 50);
};

quest.onFail = function(player) {
    player.sendMessage("§c§lドラゴンに敗北した...");
    playSound(player, "ENTITY_ENDER_DRAGON_DEATH", 1.0, 0.5);
};
        """.trimIndent())

        QuestPlugin.plugin.logger.info("[QuestScript] サンプルスクリプトを作成しました")
    }

    fun shutdown() {
        contexts.values.forEach { it.close() }
        contexts.clear()
        questScripts.clear()
    }
}

/**
 * スクリプトデータクラス
 */
data class QuestScript(
    val questId: String,
    val context: Context,
    val onStart: Value?,
    val onProgress: Value?,
    val onComplete: Value?,
    val onFail: Value?
)

/**
 * Playerラッパー（JavaScript用）
 */
class PlayerWrapper(private val player: Player) {

    fun sendMessage(message: String) {
        player.sendMessage(message)
    }

    fun getName(): String = player.name

    fun getLocation(): LocationWrapper {
        return LocationWrapper(player.location)
    }

    fun addPotionEffect(effect: String, duration: Int, amplifier: Int) {
        val effectType = org.bukkit.potion.PotionEffectType.getByName(effect) ?: return
        player.addPotionEffect(org.bukkit.potion.PotionEffect(effectType, duration, amplifier))
    }

    fun teleport(x: Double, y: Double, z: Double) {
        val loc = Location(player.world, x, y, z)
        player.teleport(loc)
    }
}

class LocationWrapper(private val location: Location) {

    fun getX(): Double = location.x
    fun getY(): Double = location.y
    fun getZ(): Double = location.z
    fun getWorld(): String = location.world.name
}

/**
 * スクリプトユーティリティ関数
 */
object ScriptUtilities {

    @JvmStatic
    fun summonBoss(location: LocationWrapper, entityType: String) {
        val world = Bukkit.getWorld(location.getWorld()) ?: return
        val loc = Location(world, location.getX(), location.getY(), location.getZ())

        val type = org.bukkit.entity.EntityType.valueOf(entityType)
        world.spawnEntity(loc, type)
    }

    @JvmStatic
    fun fireworks(location: LocationWrapper, count: Int) {
        val world = Bukkit.getWorld(location.getWorld()) ?: return
        val loc = Location(world, location.getX(), location.getY(), location.getZ())

        repeat(count) {
            val firework = world.spawn(loc, org.bukkit.entity.Firework::class.java)
            val meta = firework.fireworkMeta
            meta.power = 1
            meta.addEffect(
                org.bukkit.FireworkEffect.builder()
                    .withColor(org.bukkit.Color.RED, org.bukkit.Color.YELLOW)
                    .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                    .build()
            )
            firework.fireworkMeta = meta
        }
    }

    @JvmStatic
    fun broadcastMessage(message: String) {
        Bukkit.broadcastMessage(message)
    }

    @JvmStatic
    fun giveItem(player: PlayerWrapper, material: String, amount: Int, name: String?, lore: List<String>?) {
        val bukkitPlayer = Bukkit.getPlayer(player.getName()) ?: return
        val item = org.bukkit.inventory.ItemStack(org.bukkit.Material.valueOf(material), amount)

        if (name != null || lore != null) {
            val meta = item.itemMeta
            if (name != null) {
                meta.displayName(net.kyori.adventure.text.Component.text(name))
            }
            if (lore != null) {
                meta.lore(lore.map { net.kyori.adventure.text.Component.text(it) })
            }
            item.itemMeta = meta
        }

        bukkitPlayer.inventory.addItem(item)
    }

    @JvmStatic
    fun playSound(player: PlayerWrapper, sound: String, volume: Double, pitch: Double) {
        val bukkitPlayer = Bukkit.getPlayer(player.getName()) ?: return
        val soundType = org.bukkit.Sound.valueOf(sound)
        bukkitPlayer.playSound(bukkitPlayer.location, soundType, volume.toFloat(), pitch.toFloat())
    }

    @JvmStatic
    fun spawnParticles(location: LocationWrapper, particle: String, count: Int) {
        val world = Bukkit.getWorld(location.getWorld()) ?: return
        val loc = Location(world, location.getX(), location.getY(), location.getZ())
        val particleType = org.bukkit.Particle.valueOf(particle)

        world.spawnParticle(particleType, loc, count)
    }
}

/**
 * クエストAPI（JavaScript用）
 */
class ScriptQuestAPI {
    var onStart: Value? = null
    var onProgress: Value? = null
    var onComplete: Value? = null
    var onFail: Value? = null
}