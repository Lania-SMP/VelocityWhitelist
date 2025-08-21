package net.lania.whitelist;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

import org.slf4j.Logger;

import lombok.Getter;
import lombok.val;

public class ConfigManager {

  private final String DEFAULT_KICK_MESSAGE = "Sorry, you are not in the whitelist.";
  private final String DEFAULT_TABLE = "g_whitelist";
  private final String DATABASE_URL_FORMAT = "jdbc:mysql://%s:%s/%s?useSSL=false";

  private final VelocityWhitelist plugin;
  private final Path dataDirectory;
  private final Logger logger;
  private final Path configFile;

  private Properties properties;

  @Getter
  private boolean debugEnabled = false;
  @Getter
  private boolean pluginEnabled = false;
  @Getter
  private String kickMessage = DEFAULT_KICK_MESSAGE;
  @Getter
  private String table = DEFAULT_TABLE;
  @Getter
  private String databaseUrl = DATABASE_URL_FORMAT;
  @Getter
  private String databaseUser = "root";
  @Getter
  private String databasePassword = "1q2w3e4r";

  public ConfigManager(VelocityWhitelist plugin, Logger logger, Path dataDirectory) {
    this.plugin = plugin;
    this.dataDirectory = dataDirectory;
    this.logger = logger;
    this.configFile = dataDirectory.resolve("config.properties");
  }

  /**
   * Reloads the plugin configuration.
   * This method reloads the configuration from the config file and updates the
   * plugin's settings.
   */
  public boolean reloadConfig() {
    plugin.logDebug("Reloading configuration");

    try {
      this.properties = loadConfig();
    } catch (Exception e) {
      logger.error("Error while reloading configuration", e);
      return false;
    }
    return true;
  }

  /**
   * Saves the default configuration file if it doesn't exist.
   * This method creates the data directory if it doesn't exist,
   * and then writes the default configuration content to the config file.
   */
  public void saveDefaultConfig() {
    if (Files.notExists(dataDirectory)) {
      plugin.logDebug("Creating data directory");
      try {
        Files.createDirectories(dataDirectory);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    if (Files.notExists(configFile)) {
      plugin.logDebug("Saving default configuration");
      val defaultConfigContent = """
          # Whitelist Status
          enabled: false

          # Enable Debug Messages
          debug: false

          # MySQL settings
          host: localhost
          user: username
          password: strongpassword
          database: velocity
          port: 3306
          table: g_whitelist

          # Kick message
          message: Sorry, you are not in the whitelist.
          """;
      try {
        Files.write(configFile, defaultConfigContent.getBytes(), StandardOpenOption.CREATE);
      } catch (IOException e) {
        throw new RuntimeException("Error while saving default configuration", e);
      }
    }

  }

  /**
   * Loads the configuration from the config file.
   * This method reads the config file and loads the properties into a Properties
   * object.
   *
   * @return The Properties object containing the configuration.
   */
  public Properties loadConfig() {
    val properties = new Properties();

    plugin.logDebug("Loading configuration");

    if (Files.exists(configFile)) {
      try (val input = Files.newInputStream(configFile, StandardOpenOption.READ)) {
        val reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        properties.load(reader);
      } catch (IOException e) {
        throw new RuntimeException("Error while loading configuration", e);
      }
    }

    debugEnabled = Boolean.parseBoolean(properties.getProperty("debug", "false"));
    logger.info("Debug mode is {}", debugEnabled ? "enabled" : "disabled");

    pluginEnabled = Boolean.parseBoolean(properties.getProperty("enabled", "false"));
    kickMessage = properties.getProperty("message", DEFAULT_KICK_MESSAGE);
    table = properties.getProperty("table", DEFAULT_TABLE);

    val host = properties.getProperty("host", "localhost");
    val port = properties.getProperty("port", "3306");
    val database = properties.getProperty("database", "velocity");
    databaseUrl = String.format(DATABASE_URL_FORMAT, host, port, database);

    databaseUser = properties.getProperty("user", "root");
    databasePassword = properties.getProperty("password", "1q2w3e4r");

    return properties;
  }

  /**
   * Saves the configuration to the config file.
   * This method writes the properties to the config file.
   * 
   * @param properties The Properties object containing the configuration to save.
   */
  public void saveConfig(Properties properties) {
    plugin.logDebug("Saving configuration");
    try (val output = Files.newOutputStream(configFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      val writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
      properties.store(writer, "Updated Configuration");
    } catch (IOException e) {
      throw new RuntimeException("Error while saving configuration", e);
    }
  }

  public void setDebugMode(boolean enabled) {
    debugEnabled = enabled;
    properties.setProperty("debug", String.valueOf(debugEnabled));
    saveConfig(properties);
  }

  public void setPluginEnabled(boolean enabled) {
    pluginEnabled = enabled;
    properties.setProperty("enabled", String.valueOf(pluginEnabled));
    saveConfig(properties);
  }

}
