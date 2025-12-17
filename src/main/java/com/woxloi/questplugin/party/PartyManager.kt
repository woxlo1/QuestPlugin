package com.woxloi.questplugin.party

import org.bukkit.entity.Player
import org.bukkit.Bukkit
import java.util.*

object PartyManager {

    private val playerToPartyId = mutableMapOf<UUID, UUID>()
    private val parties = mutableMapOf<UUID, Party>()
    private val playerPartyMap = mutableMapOf<UUID, UUID>()

    data class Party(
        val leader: UUID,
        val members: MutableSet<UUID> = mutableSetOf()
    )
    enum class InviteResult {
        SUCCESS,
        NOT_IN_PARTY,
        NOT_LEADER,
        ALREADY_IN_PARTY,
        FAILURE
    }
    enum class JoinResult {
        SUCCESS,
        NOT_INVITED,
        ALREADY_IN_PARTY,
        FAILURE
    }

    // 招待状：対象UUID -> 招待元リーダーUUIDセット
    private val invitations = mutableMapOf<UUID, MutableSet<UUID>>()

    fun createParty(leader: Player): Boolean {
        if (isInParty(leader)) return false
        val partyId = UUID.randomUUID()
        val party = Party(leader.uniqueId, mutableSetOf(leader.uniqueId))
        parties[partyId] = party
        playerToPartyId[leader.uniqueId] = partyId
        return true
    }

    fun disbandParty(leader: Player): Boolean {
        val party = getParty(leader) ?: return false
        if (party.leader != leader.uniqueId) return false

        val partyId = playerToPartyId[leader.uniqueId] ?: return false
        party.members.forEach { playerToPartyId.remove(it) }
        parties.remove(partyId)
        invitations.entries.removeIf { it.value.contains(leader.uniqueId) }
        return true
    }

    fun getParty(player: Player): Party? {
        val partyId = playerToPartyId[player.uniqueId] ?: return null
        return parties[partyId]
    }

    fun isInParty(player: Player): Boolean = playerToPartyId.containsKey(player.uniqueId)

    fun isLeader(player: Player): Boolean {
        val party = getParty(player) ?: return false
        return party.leader == player.uniqueId
    }

    fun invitePlayer(leader: Player, target: Player): InviteResult {
        val party = getParty(leader) ?: return InviteResult.NOT_IN_PARTY
        if (party.leader != leader.uniqueId) return InviteResult.NOT_LEADER
        if (isInParty(target)) return InviteResult.ALREADY_IN_PARTY

        invitations.getOrPut(target.uniqueId) { mutableSetOf() }.add(leader.uniqueId)
        return InviteResult.SUCCESS
    }

    fun joinParty(player: Player, leader: Player): JoinResult {
        if (isInParty(player)) return JoinResult.ALREADY_IN_PARTY

        val invitedLeaders = invitations[player.uniqueId] ?: emptySet()
        if (!invitedLeaders.contains(leader.uniqueId)) return JoinResult.NOT_INVITED

        // パーティーIDを取得
        val partyId = playerToPartyId[leader.uniqueId] ?: return JoinResult.FAILURE

        // パーティーデータを取得
        val party = parties[partyId] ?: return JoinResult.FAILURE

        // 実際に参加処理
        party.members.add(player.uniqueId)
        playerToPartyId[player.uniqueId] = partyId

        // 招待解除
        invitations[player.uniqueId]?.remove(leader.uniqueId)
        if (invitations[player.uniqueId]?.isEmpty() == true) {
            invitations.remove(player.uniqueId)
        }

        return JoinResult.SUCCESS
    }


    fun leaveParty(player: Player): Boolean {
        val party = getParty(player) ?: return false
        val partyId = playerToPartyId[player.uniqueId] ?: return false

        if (party.leader == player.uniqueId) {
            disbandParty(player)
            return true
        }

        party.members.remove(player.uniqueId)
        playerToPartyId.remove(player.uniqueId)
        return true
    }

    fun kickMember(leader: Player, target: Player): Boolean {
        val party = getParty(leader) ?: return false
        if (party.leader != leader.uniqueId) return false
        if (!party.members.contains(target.uniqueId)) return false
        if (leader.uniqueId == target.uniqueId) return false

        party.members.remove(target.uniqueId)
        playerToPartyId.remove(target.uniqueId)
        return true
    }

    fun getPartyMembers(player: Player): List<Player> {
        val party = getParty(player) ?: return emptyList()
        return party.members.mapNotNull { Bukkit.getPlayer(it) }
    }

    fun isSameParty(uuid1: UUID, uuid2: UUID): Boolean {
        if (uuid1 == uuid2) return true

        val partyId1 = playerToPartyId[uuid1]
        val partyId2 = playerToPartyId[uuid2]

        // 両方パーティー未所属 → 同一扱いしない
        if (partyId1 == null || partyId2 == null) return false

        return partyId1 == partyId2
    }

}
