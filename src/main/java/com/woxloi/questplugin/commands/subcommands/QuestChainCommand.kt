import com.woxloi.questplugin.QuestPlugin
import com.woxloi.questplugin.features.QuestChainManager
import org.bukkit.entity.Player

/**
 * チェーンコマンド
 */
class QuestChainCommand : org.bukkit.command.CommandExecutor {

    override fun onCommand(
        sender: org.bukkit.command.CommandSender,
        command: org.bukkit.command.Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage(QuestPlugin.prefix + "§c§lプレイヤーのみ実行可能です")
            return true
        }

        if (args.isEmpty() || args[0].lowercase() == "chains") {
            QuestChainManager.showAllChains(sender)
            return true
        }

        if (args[0].lowercase() == "chain" && args.size >= 2) {
            QuestChainManager.showChainProgress(sender, args[1])
            return true
        }

        return true
    }
}