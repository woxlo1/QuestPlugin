package com.woxloi.questplugin.features

import com.woxloi.questplugin.model.QuestData
import com.woxloi.questplugin.QuestPlugin
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import java.io.File

/**
 * JavaScriptスクリプトエンジン（Rhino使用）
 *
 * クエストの動的な振る舞いをJavaScriptで定義できます
 */
object QuestScriptEngine {

    private val scriptFolder = File(QuestPlugin.plugin.dataFolder, "quest_scripts")
    private val contexts = mutableMapOf<String, Scriptable>()
    private val questScripts = mutableMapOf<String, QuestScript>()
    private var isEnabled = false

    fun init() {
        try {
            // スクリプトフォルダ作成
            if (!scriptFolder.exists()) {
                scriptFolder.mkdirs()
                createExampleScripts()
            }

            // Rhinoで全スクリプトを読み込む
            loadAllScripts()
            isEnabled = true
            QuestPlugin.plugin.logger.info("[QuestScript] スクリプトエンジンを有効化しました")

        } catch (e: Exception) {
            QuestPlugin.plugin.logger.severe("[QuestScript] 初期化エラー: ${e.message}")
            isEnabled = false
        }
    }

    private fun loadAllScripts() {
        scriptFolder.listFiles()?.forEach { file ->
            if (file.extension == "js") loadScript(file)
        }
        QuestPlugin.plugin.logger.info("[QuestScript] ${questScripts.size}個のスクリプトを読み込みました")
    }

    fun loadScript(file: File) {
        if (!isEnabled) return

        try {
            val questId = file.nameWithoutExtension
            val cx = RhinoContext.enter()
            cx.optimizationLevel = -1
            val scope = cx.initStandardObjects()

            // グローバル関数登録
            registerGlobalFunctions(scope)

            // スクリプト実行
            cx.evaluateString(scope, file.readText(), file.name, 1, null)

            val script = QuestScript(
                questId = questId,
                scope = scope,
                onStart = scope.get("onStart", scope) as? Function,
                onProgress = scope.get("onProgress", scope) as? Function,
                onComplete = scope.get("onComplete", scope) as? Function,
                onFail = scope.get("onFail", scope) as? Function
            )

            questScripts[questId] = script
            contexts[questId] = scope

            QuestPlugin.plugin.logger.info("[QuestScript] ${file.name} を読み込みました")
        } catch (e: Exception) {
            QuestPlugin.plugin.logger.severe("[QuestScript] ${file.name} の読み込みに失敗: ${e.message}")
            e.printStackTrace()
        } finally {
            RhinoContext.exit()
        }
    }

    private fun registerGlobalFunctions(scope: Scriptable) {
        // quest オブジェクト
        val questObj = ScriptQuestAPI()
        scope.put("quest", scope, questObj)

        // ユーティリティ関数
        scope.put("summonBoss", scope, ScriptUtilities::summonBoss)
        scope.put("fireworks", scope, ScriptUtilities::fireworks)
        scope.put("broadcastMessage", scope, ScriptUtilities::broadcastMessage)
        scope.put("giveItem", scope, ScriptUtilities::giveItem)
        scope.put("playSound", scope, ScriptUtilities::playSound)
        scope.put("spawnParticles", scope, ScriptUtilities::spawnParticles)
    }

    fun onQuestStart(player: Player, quest: QuestData) = executeFunction(player, quest.id, "onStart")
    fun onQuestProgress(player: Player, quest: QuestData, amount: Int) = executeFunction(player, quest.id, "onProgress", amount)
    fun onQuestComplete(player: Player, quest: QuestData) = executeFunction(player, quest.id, "onComplete")
    fun onQuestFail(player: Player, quest: QuestData) = executeFunction(player, quest.id, "onFail")

    private fun executeFunction(player: Player, questId: String, funcName: String, vararg args: Any) {
        if (!isEnabled) return
        val script = questScripts[questId] ?: return
        val cx = RhinoContext.enter()
        try {
            val func = when(funcName) {
                "onStart" -> script.onStart
                "onProgress" -> script.onProgress
                "onComplete" -> script.onComplete
                "onFail" -> script.onFail
                else -> null
            } ?: return

            val jsArgs = arrayOf(PlayerWrapper(player), *args)
            func.call(cx, script.scope, script.scope, jsArgs)
        } catch (e: Exception) {
            QuestPlugin.plugin.logger.warning("[QuestScript] $funcName 実行エラー: ${e.message}")
        } finally {
            RhinoContext.exit()
        }
    }

    private fun createExampleScripts() {
        val readmeFile = File(scriptFolder, "README.txt")
        readmeFile.writeText("""
=== QuestPlugin スクリプトフォルダ ===

このフォルダにJavaScriptファイル(.js)を配置することで、
クエストに独自の動作を追加できます。

サンプル: dragon_slayer.js を参照してください
        """.trimIndent())

        val dragonScript = File(scriptFolder, "dragon_slayer.js.example")
        dragonScript.writeText("""
// ドラゴン討伐クエストのスクリプト例
// 使用方法: このファイルを dragon_slayer.js にリネームしてください

quest.onStart = function(player) {
    player.sendMessage("§c§lドラゴンが現れた！");
    player.addPotionEffect("FIRE_RESISTANCE", 6000, 1);
    summonBoss(player.getLocation(), "ENDER_DRAGON");
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
    fireworks(player.getLocation(), 10);
    broadcastMessage("§e" + player.getName() + " §7がドラゴンを倒した！");
    giveItem(player, "DIAMOND_SWORD", 1, "§b§lドラゴンスレイヤー", [
        "§7伝説のドラゴンを倒した証",
        "§c攻撃力 +10"
    ]);
    spawnParticles(player.getLocation(), "DRAGON_BREATH", 50);
};

quest.onFail = function(player) {
    player.sendMessage("§c§lドラゴンに敗北した...");
    playSound(player, "ENTITY_ENDER_DRAGON_DEATH", 1.0, 0.5);
};
        """.trimIndent())

        QuestPlugin.plugin.logger.info("[QuestScript] サンプルスクリプトを作成しました（.example拡張子）")
    }

    fun shutdown() {
        contexts.clear()
        questScripts.clear()
    }

    fun isScriptEnabled(): Boolean = isEnabled
}

data class QuestScript(
    val questId: String,
    val scope: Scriptable,
    val onStart: Function?,
    val onProgress: Function?,
    val onComplete: Function?,
    val onFail: Function?
)

class PlayerWrapper(private val player: Player) {
    fun sendMessage(message: String) = player.sendMessage(message)
    fun getName(): String = player.name
    fun getLocation(): LocationWrapper = LocationWrapper(player.location)
    fun addPotionEffect(effect: String, duration: Int, amplifier: Int) {
        val type = org.bukkit.potion.PotionEffectType.getByName(effect) ?: return
        player.addPotionEffect(org.bukkit.potion.PotionEffect(type, duration, amplifier))
    }
    fun teleport(x: Double, y: Double, z: Double) = player.teleport(Location(player.world, x, y, z))
}

class LocationWrapper(private val location: Location) {
    fun getX(): Double = location.x
    fun getY(): Double = location.y
    fun getZ(): Double = location.z
    fun getWorld(): String = location.world.name
}

object ScriptUtilities {
    @JvmStatic fun summonBoss(location: LocationWrapper, entityType: String) {
        val world = Bukkit.getWorld(location.getWorld()) ?: return
        world.spawnEntity(Location(world, location.getX(), location.getY(), location.getZ()), org.bukkit.entity.EntityType.valueOf(entityType))
    }
    @JvmStatic fun fireworks(location: LocationWrapper, count: Int) {
        val world = Bukkit.getWorld(location.getWorld()) ?: return
        val loc = Location(world, location.getX(), location.getY(), location.getZ())
        repeat(count) {
            val fw = world.spawn(loc, org.bukkit.entity.Firework::class.java)
            val meta = fw.fireworkMeta
            meta.power = 1
            meta.addEffect(org.bukkit.FireworkEffect.builder()
                .withColor(org.bukkit.Color.RED, org.bukkit.Color.YELLOW)
                .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                .build())
            fw.fireworkMeta = meta
        }
    }
    @JvmStatic fun broadcastMessage(message: String) = Bukkit.broadcastMessage(message)
    @JvmStatic fun giveItem(player: PlayerWrapper, material: String, amount: Int, name: String?, lore: List<String>?) {
        val p = Bukkit.getPlayer(player.getName()) ?: return
        val item = org.bukkit.inventory.ItemStack(org.bukkit.Material.valueOf(material), amount)
        if (name != null || lore != null) {
            val meta = item.itemMeta
            if (name != null) meta.displayName(net.kyori.adventure.text.Component.text(name))
            if (lore != null) meta.lore(lore.map { net.kyori.adventure.text.Component.text(it) })
            item.itemMeta = meta
        }
        p.inventory.addItem(item)
    }
    @JvmStatic fun playSound(player: PlayerWrapper, sound: String, volume: Double, pitch: Double) {
        val p = Bukkit.getPlayer(player.getName()) ?: return
        p.playSound(p.location, org.bukkit.Sound.valueOf(sound), volume.toFloat(), pitch.toFloat())
    }
    @JvmStatic fun spawnParticles(location: LocationWrapper, particle: String, count: Int) {
        val world = Bukkit.getWorld(location.getWorld()) ?: return
        val loc = Location(world, location.getX(), location.getY(), location.getZ())
        world.spawnParticle(org.bukkit.Particle.valueOf(particle), loc, count)
    }
}

class ScriptQuestAPI {
    var onStart: Function? = null
    var onProgress: Function? = null
    var onComplete: Function? = null
    var onFail: Function? = null
}
