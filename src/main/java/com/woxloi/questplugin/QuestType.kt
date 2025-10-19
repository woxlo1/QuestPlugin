package com.woxloi.questplugin

enum class QuestType(val displayName: String) {

    // 討伐系
    KILL("Mob討伐"),

    // 収集系
    COLLECT("アイテム収集"),

    // 探索系
    TRAVEL("場所訪問"),

    // 採掘・設置
    MINE("ブロック採掘"),
    PLACE("ブロック設置"),
    BREAK("ブロック破壊"),

    // 生産系
    CRAFT("クラフト"),
    SMELT("精錬"),

    // 行動系
    FISH("釣り"),
    SLEEP("寝る"),
    CHAT("チャット送信"),
    COMMAND("コマンド実行"),
    INTERACT("ブロック/エンティティと対話"),

    // 戦闘系
    DAMAGE_TAKEN("ダメージを受ける"),
    DAMAGE_GIVEN("ダメージを与える"),
    SHOOT("弓などで攻撃"),
    MYTHIC_KILL("特殊Mob討伐"),

    // 経験・成長
    LEVEL("レベル到達"),
    EXP_GAINED("経験値獲得"),
    TIME_PLAYED("プレイ時間"),

    // 村人・動物系
    TAME("動物を手懐ける"),
    BREED("繁殖"),
    TRADE("村人と取引"),

    // 乗り物系
    RIDE("乗り物に乗る");

    companion object {
        fun fromString(type: String): QuestType? {
            return values().firstOrNull { it.name.equals(type, ignoreCase = true) }
        }
    }
}
