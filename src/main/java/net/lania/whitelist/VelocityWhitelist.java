package net.lania.whitelist;

import java.nio.file.Path;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import lombok.val;
import net.lania.whitelist.handler.VwlCommandHandler;
import net.lania.whitelist.service.WhitelistService;
import net.lania.whitelist.storage.MySqlStorage;

/**
 * The main class for the VelocityWhitelist plugin.
 * This class handles plugin initialization, command registration,
 * configuration loading, and whitelist management.
 */
@Plugin(id = BuildConstants.ID, name = BuildConstants.NAME, version = BuildConstants.VERSION, url = BuildConstants.URL, description = BuildConstants.DESCRIPTION, authors = BuildConstants.AUTHORS)
public class VelocityWhitelist {

  private final ProxyServer server;
  private final Logger logger;
  private final ConfigManager configHandler;
  private final MySqlStorage storage;
  private final WhitelistService whitelistService;
  private final VwlCommandHandler vwlCommandHandler;
  private final EventHandler eventHandler;

  /**
   * Constructor for the VelocityWhitelist plugin.
   *
   * @param server        The ProxyServer instance.
   * @param logger        The Logger instance.
   * @param dataDirectory The plugin's data directory.
   */
  @Inject
  public VelocityWhitelist(
      ProxyServer server,
      Logger logger,
      @DataDirectory Path dataDirectory) {

    this.server = server;
    this.logger = logger;
    this.configHandler = new ConfigManager(this, logger, dataDirectory);
    this.storage = new MySqlStorage(this, logger, configHandler);
    this.whitelistService = new WhitelistService(this, configHandler, storage);
    this.vwlCommandHandler = new VwlCommandHandler(this, configHandler, whitelistService);
    this.eventHandler = new EventHandler(this, logger, configHandler, whitelistService);
  }

  public void logDebug(String message, Object... args) {
    if (configHandler.isDebugEnabled()) {
      logger.info("[DEBUG] " + message, args);
    }
  }

  /**
   * Event listener for the ProxyInitializeEvent.
   * This method is called when the proxy server is initialized.
   * It registers commands, loads the configuration, and creates the database
   * table.
   *
   * @param event The ProxyInitializeEvent.
   */
  @Subscribe
  public void onProxyInitialization(ProxyInitializeEvent event) {
    try {
      // Initialize the configuration
      configHandler.initConfig();

      if (configHandler.isPluginEnabled() && !storage.init()) {
        logger.error("Failed to initialize storage");
        return;
      }

      // Register the whitelist command
      val commandManager = server.getCommandManager();
      val commandMeta = commandManager.metaBuilder(VwlCommandHandler.VWL_COMMAND_ALIAS)
          .plugin(this)
          .build();
      val commandToRegister = createCommandStructure();
      commandManager.register(commandMeta, commandToRegister);

      // Register the event handler
      server.getEventManager().register(this, eventHandler);

      /***
       * SAMPLE LOGO
       * __ __ __ __ _
       * \ \ / / \ \ / / | | Velocity Whitelist
       * \ V / \ \/\/ / | |__ v 1.0.0
       * \_/ \_/\_/ |____| Built on 2021-09-30
       * 
       */

      logger.info(" __   __ __      __  _    ");
      logger.info(" \\\\ \\ / / \\\\ \\    / / | |     {} ", BuildConstants.NAME);
      logger.info("  \\ V /   \\\\ \\/\\/ /  | |__   v {} ", BuildConstants.VERSION);
      logger.info("   \\_/     \\_/\\_/   |____|  Built on {} ", BuildConstants.BUILD_DATE);
      logger.info(" ");
      logger.info("{} {} loaded successfully!", BuildConstants.NAME, BuildConstants.VERSION);
    } catch (Exception e) {
      logger.error("Error during plugin initialization", e);
    }
  }

  /**
   * Creates a BrigadierCommand for the whitelist command.
   * This method defines the command structure and its execution logic.
   *
   * @return The BrigadierCommand instance.
   */
  public BrigadierCommand createCommandStructure() {
    logDebug("Creating Brigadier command");

    val coreCmd = BrigadierCommand.literalArgumentBuilder(VwlCommandHandler.VWL_COMMAND_ALIAS)
        .requires(source -> source.hasPermission(Constants.BASE_PERMISSION))
        .executes(context -> {
          val source = context.getSource();
          vwlCommandHandler.sendUsageMessage(source, "all");
          return Command.SINGLE_SUCCESS;
        })
        .then(BrigadierCommand
            .requiredArgumentBuilder(VwlCommandHandler.VWL_COMMAND_ACTION_ARGUMENT, StringArgumentType.string())
            .suggests((context, builder) -> vwlCommandHandler.suggestAction(context, builder))
            .then(BrigadierCommand
                .requiredArgumentBuilder(VwlCommandHandler.VWL_COMMAND_TARGET_ARGUMENT, StringArgumentType.string())
                .suggests((context, builder) -> vwlCommandHandler.suggestTarget(context, builder))
                .executes(context -> vwlCommandHandler.handleActionWithTarget(context)))
            .executes(context -> vwlCommandHandler.handleAction(context)))
        .build();

    return new BrigadierCommand(coreCmd);
  }

}
