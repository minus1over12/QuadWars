package io.github.minus1over12.quadwars;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.scoreboard.Scoreboard;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.Objects;
import java.util.UUID;

/**
 * Utilities that require Floodgate.
 *
 * @author War Pigeon
 */
public enum FloodgateIntegration {
    ;
    
    /**
     * Sends a team join form to the player.
     *
     * @param player the player to send the form to
     */
    static void sendTeamForm(Entity player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        if (scoreboard.getEntityTeam(player) == null &&
                player.hasPermission("quadwars.player.jointeam")) {
            FloodgateApi instance = FloodgateApi.getInstance();
            UUID uniqueId = player.getUniqueId();
            if (instance.isFloodgatePlayer(uniqueId)) {
                instance.sendForm(uniqueId, SimpleForm.builder().title("Select a Team")
                        .button(PlainTextComponentSerializer.plainText().serialize(
                                Objects.requireNonNull(scoreboard.getTeam("quadwars_NE"))
                                        .displayName()))
                        .button(PlainTextComponentSerializer.plainText().serialize(
                                Objects.requireNonNull(scoreboard.getTeam("quadwars_SE"))
                                        .displayName()))
                        .button(PlainTextComponentSerializer.plainText().serialize(
                                Objects.requireNonNull(scoreboard.getTeam("quadwars_SW"))
                                        .displayName()))
                        .button(PlainTextComponentSerializer.plainText().serialize(
                                Objects.requireNonNull(scoreboard.getTeam("quadwars_NW"))
                                        .displayName())).validResultHandler(
                                response -> Bukkit.dispatchCommand(player,
                                        "jointeam " + response.clickedButton().text()))
                        .closedOrInvalidResultHandler(() -> sendTeamForm(player)).build());
            }
        }
    }
}
