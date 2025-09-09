package net.lania.whitelist;

import org.slf4j.Logger;

import com.velocitypowered.api.event.ResultedEvent.ComponentResult;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;

import lombok.RequiredArgsConstructor;
import lombok.val;
import net.lania.whitelist.service.WhitelistService;

@RequiredArgsConstructor
public class EventHandler {

  private final VelocityWhitelist plugin;

  private final Logger logger;

  private final ConfigManager configHandler;

  private final WhitelistService whitelistService;

  /**
   * Event listener for the LoginEvent.
   * This method is called when a player attempts to log in.
   * It checks if the player is whitelisted and denies the connection if they are
   * not.
   *
   * @param event The LoginEvent.
   */
  @Subscribe
  public void onPlayerLogin(LoginEvent event) {
    // Validate that the player object is not null
    val player = event.getPlayer();
    if (player == null) {
      logger.error("LoginEvent triggered with a null player.");
      return;
    }

    plugin.logDebug("Player login: " + player.getUsername());

    // Check if the whitelist is enabled and if the player is not whitelisted
    if (configHandler.isPluginEnabled() && !whitelistService.isWhitelisted(player)) {
      event.setResult(ComponentResult
          .denied(configHandler.getLocalizedMessages().get(configHandler.getDefaultLocale()).getKicked()));
    }
  }
}
