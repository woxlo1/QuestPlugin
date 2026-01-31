// dragon_slayer.js
quest.onStart = function(player) {
    player.sendMessage("§c§lドラゴンが現れた！");
    summonBoss(player.getLocation(), "ENDER_DRAGON");
};

quest.onProgress = function(player, amount) {
    if (amount >= 50) {
        player.addPotionEffect("STRENGTH", 600, 2);
    }
};

quest.onComplete = function(player) {
    fireworks(player.getLocation(), 10);
    broadcastMessage(player.getName() + " がドラゴンを倒した！");
};