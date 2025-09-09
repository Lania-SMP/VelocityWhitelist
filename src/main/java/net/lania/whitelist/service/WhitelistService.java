package net.lania.whitelist.service;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;

import lombok.RequiredArgsConstructor;
import lombok.val;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.lania.whitelist.ConfigManager;
import net.lania.whitelist.UuidUtils;
import net.lania.whitelist.VelocityWhitelist;
import net.lania.whitelist.storage.MySqlStorage;

@RequiredArgsConstructor
public class WhitelistService {

  private final VelocityWhitelist plugin;

  private final ConfigManager config;

  private final MySqlStorage storage;

  /**
   * Checks if a player is whitelisted.
   * This method queries the database to determine if the player is in the
   * whitelist.
   *
   * @param player The Player to check.
   * @return True if the player is whitelisted, false otherwise.
   */
  public boolean isWhitelisted(Player player) {
    val uniqueId = player.getUniqueId();
    val username = player.getUsername();

    plugin.logDebug("Checking if {} (UUID: {}) is whitelisted", username, uniqueId);

    val result = storage.findEntryByUniqueId(uniqueId);
    if (result == -1) {
      player.sendMessage(config.getLocalizedMessages().get(config.getDefaultLocale()).getFailedToCheckWhitelist());
      return false;
    }

    if (result == 1) {
      return true;
    }

    return false;
  }

  /**
   * Provides suggestions for whitelisted player names.
   *
   * @param builder The SuggestionsBuilder to add suggestions to.
   * @return A CompletableFuture containing the suggestions.
   */
  public CompletableFuture<Suggestions> getWhitelistedPlayerSuggestions(SuggestionsBuilder builder) {
    return CompletableFuture.supplyAsync(() -> {
      val remaining = builder.getRemaining().toLowerCase();
      val suggestions = storage.findUsernameLikeString(remaining, 10);
      suggestions.forEach(builder::suggest);
      return builder.build();
    });
  }

  /**
   * Adds a player to the whitelist.
   * This method opens a connection to the database, executes the SQL query,
   * and then closes the connection.
   *
   * @param source   The CommandSource who executed the command.
   * @param username The name of the player to add to the whitelist.
   */
  public void addWhitelist(CommandSource source, String username) {
    plugin.logDebug("Adding {} to the whitelist", username);

    val uniqueId = UuidUtils.generateUniqueId(username);

    val result = storage.findEntryByUniqueId(uniqueId);
    if (result == -1) {
      source.sendMessage(Component.text("Failed to add " + username + " to the whitelist.", NamedTextColor.RED));
      return;
    }

    if (result == 1) {
      source.sendMessage(Component.text(username + " is already whitelisted.", NamedTextColor.RED));
      return;
    }

    if (storage.insertWhitelist(uniqueId, username)) {
      source.sendMessage(Component.text(username + " is now whitelisted.", NamedTextColor.GREEN));
    }
  }

  /**
   * Deletes a player from the whitelist.
   * This method opens a connection to the database, executes the SQL query,
   * and then closes the connection.
   *
   * @param source   The CommandSource who executed the command.
   * @param username The name of the player to delete from the whitelist.
   */
  public void delWhitelist(CommandSource source, String username) {
    plugin.logDebug("Removing {} from the whitelist", username);

    val uniqueId = UuidUtils.generateUniqueId(username);

    val result = storage.findEntryByUniqueId(uniqueId);
    if (result == -1) {
      source.sendMessage(Component.text("Failed to remove " + username + " from the whitelist.", NamedTextColor.RED));
      return;
    }

    if (result == 1) {
      source.sendMessage(Component.text(username + " is not whitelisted.", NamedTextColor.RED));
      return;
    }

    if (storage.deleteWhitelist(uniqueId)) {
      source.sendMessage(Component.text(username + " is no longer whitelisted.", NamedTextColor.AQUA));
    }
  }

  /**
   * Lists all whitelisted players that match a case-insensitive search.
   * The search string must be at least 2 characters long.
   *
   * @param source The CommandSource who executed the command.
   * @param search The search string to match against player names.
   */
  public void listWhitelist(CommandSource source, String search) {
    plugin.logDebug("Listing whitelisted players matching: {}", search);

    // Preliminary check to make sure the search string is at least 2 characters
    // long
    if (search.length() < 2) {
      source.sendMessage(Component.text("Search string must be at least 2 characters long.", NamedTextColor.RED));
      return;
    }

    // Sanitize the search string to prevent SQL injection
    val sanitizedSearch = search.replaceAll("[^a-zA-Z0-9]", "");
    var isValid = true;

    // Check if the sanitized string is still valid
    if (sanitizedSearch != search) {
      var outMsg = "Invalid characters in search string.";

      if (sanitizedSearch.length() < 2) {
        outMsg += " Not enough usable characters.";
        isValid = false;
      }
      if (sanitizedSearch.isEmpty()) {
        outMsg += " No usable characters found.";
        isValid = false;
      }

      source.sendMessage(Component.text(outMsg, NamedTextColor.RED));
      // Exit if the search string is invalid
      if (!isValid) {
        return;
      }
    }

    val matching = storage.findUsernameLikeString(sanitizedSearch, 20);

    if (matching.isEmpty()) {
      source.sendMessage(Component.text("No whitelisted players found matching '" + search + "'.",
          NamedTextColor.RED));
      return;
    }

    val playerList = matching.stream().collect(Collectors.joining(", "));
    source.sendMessage(Component.text("Whitelisted Players matching '" + search + "':", NamedTextColor.GREEN)
        .append(Component.text(" " + playerList, NamedTextColor.WHITE)));
  }

}
