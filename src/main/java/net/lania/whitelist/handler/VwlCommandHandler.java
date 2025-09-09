package net.lania.whitelist.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.CommandSource;

import lombok.RequiredArgsConstructor;
import lombok.val;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.lania.whitelist.ConfigManager;
import net.lania.whitelist.Constants;
import net.lania.whitelist.VelocityWhitelist;
import net.lania.whitelist.service.WhitelistService;

@RequiredArgsConstructor
public class VwlCommandHandler {

  public static final String VWL_COMMAND_ALIAS = "vwl";
  public static final String VWL_COMMAND_ACTION_ARGUMENT = "action";
  public static final String VWL_COMMAND_TARGET_ARGUMENT = "target";

  private static final Map<String, String> USAGE_MESSAGE = Map.of(
      "all", "/vwl add/del <player> | list <search> | enable/disable | reload | debug <on/off> ",
      "add", "/vwl add <player>",
      "del", "/vwl del <player>",
      "list", "/vwl list <search>",
      "enable", "/vwl enable",
      "disable", "/vwl disable",
      "reload", "/vwl reload",
      "debug", "/vwl debug <on/off>");

  private static final Component INSUFFICIENT_PERMISSION_MESSAGE = Component.text(
      "You do not have permission to use this command.",
      NamedTextColor.RED);

  private final VelocityWhitelist plugin;

  private final ConfigManager configHandler;

  private final WhitelistService whitelistService;

  /**
   * Sends a usage message to the command source.
   * This method constructs a message based on the specified subcommand.
   *
   * @param source     The CommandSource to send the message to.
   * @param subcommand The subcommand for which to display usage.
   */
  public void sendUsageMessage(CommandSource source, String subcommand) {
    plugin.logDebug("Sending usage message for {}", subcommand);

    var usage = USAGE_MESSAGE.get(subcommand.toLowerCase());
    if (usage == null) {
      usage = USAGE_MESSAGE.get("all");
    }

    val message = Component.text("Usage:", NamedTextColor.RED)
        .append(Component.text(" " + usage, NamedTextColor.WHITE));
    source.sendMessage(message);
  }

  public CompletableFuture<Suggestions> suggestAction(
      CommandContext<CommandSource> context,
      SuggestionsBuilder builder) {

    val suggestions = new ArrayList<>(List.of("add", "del", "list"));

    if (context.getSource().hasPermission(Constants.BASE_PERMISSION_ADMIN)) {
      suggestions.addAll(List.of("debug", "enable", "disable", "reload"));
    }

    for (val suggestion : suggestions) {
      builder.suggest(suggestion);
    }

    return builder.buildFuture();
  }

  public int handleAction(CommandContext<CommandSource> context) {
    val source = context.getSource();
    val action = context.getArgument(VWL_COMMAND_ACTION_ARGUMENT, String.class);

    if (List.of("enable", "disable", "reload", "debug").contains(action.toLowerCase())) {
      if (!context.getSource().hasPermission(Constants.BASE_PERMISSION_ADMIN)) {
        context.getSource().sendMessage(INSUFFICIENT_PERMISSION_MESSAGE);
        return Command.SINGLE_SUCCESS;
      }
    }

    switch (action.toLowerCase()) {
      case "enable" -> {
        configHandler.setPluginEnabled(true);
        source.sendMessage(Component.text("Whitelist enabled", NamedTextColor.GREEN));
      }
      case "disable" -> {
        configHandler.setPluginEnabled(false);
        source.sendMessage(Component.text("Whitelist disabled", NamedTextColor.AQUA));
      }
      case "reload" -> {
        if (configHandler.reloadConfig()) {
          source.sendMessage(Component.text("Configuration reloaded successfully.", NamedTextColor.GREEN));
        } else {
          source.sendMessage(Component.text(
              "Error while reloading configuration. Check the console for details.", NamedTextColor.RED));
        }
      }
      default -> sendUsageMessage(source, action);
    }

    return Command.SINGLE_SUCCESS;
  }

  public CompletableFuture<Suggestions> suggestTarget(
      CommandContext<CommandSource> context,
      SuggestionsBuilder builder) {

    val input = context.getInput();
    val parts = input.split(" ");

    // Check if there are at least two parts in the input
    if (parts.length > 1) {
      val action = parts[1].trim(); // Get the action (add/del)
      // Ensure that the action is the last meaningful part of the input
      if (parts.length == 2 && action.equalsIgnoreCase("del")) {
        return whitelistService.getWhitelistedPlayerSuggestions(builder);
      } else if (action.equalsIgnoreCase("debug")) {
        builder.suggest("on").suggest("off");
      }
    }

    return Suggestions.empty();
  }

  public int handleActionWithTarget(CommandContext<CommandSource> context) {
    val source = context.getSource();
    val action = context.getArgument(VWL_COMMAND_ACTION_ARGUMENT, String.class);
    val target = context.getArgument(VWL_COMMAND_TARGET_ARGUMENT, String.class);

    switch (action.toLowerCase()) {
      // case "add" -> whitelistService.addWhitelist(source, target);
      // case "del" -> whitelistService.delWhitelist(source, target);
      case "list" -> whitelistService.listWhitelist(source, target);
      case "debug" -> {
        if (!source.hasPermission(Constants.BASE_PERMISSION_ADMIN)) {
          source.sendMessage(INSUFFICIENT_PERMISSION_MESSAGE);
          return Command.SINGLE_SUCCESS;
        }

        if (target.equalsIgnoreCase("on")) {
          setDebugMode(source, true);
        } else if (target.equalsIgnoreCase("off")) {
          setDebugMode(source, false);
        } else {
          sendUsageMessage(source, "debug");
        }
      }
      default -> sendUsageMessage(source, action);
    }
    return Command.SINGLE_SUCCESS;
  }

  /**
   * Sets the debug mode and updates the configuration.
   * This method updates the debugEnabled field, saves the configuration, and
   * sends a message to the command source.
   *
   * @param source The CommandSource who executed the command.
   * @param enable True to enable debug mode, false to disable.
   */
  private void setDebugMode(CommandSource source, boolean enabled) {
    this.configHandler.setDebugMode(enabled);
    source.sendMessage(Component.text("Debug mode is now " + (enabled ? "enabled" : "disabled"),
        enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
  }

}
