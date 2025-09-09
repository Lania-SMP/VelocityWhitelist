package net.lania.whitelist;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import lombok.Getter;
import lombok.val;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.lania.whitelist.config.DatabaseConfig;
import net.lania.whitelist.config.Messages;

@Getter
public class ConfigManager {

  @Getter(lombok.AccessLevel.NONE)
  private final VelocityWhitelist plugin;
  @Getter(lombok.AccessLevel.NONE)
  private final Logger logger;
  @Getter(lombok.AccessLevel.NONE)
  private final File configFile;
  @Getter(lombok.AccessLevel.NONE)
  private YamlDocument config;

  @Getter
  private boolean debugEnabled = false;
  @Getter
  private boolean pluginEnabled = false;
  @Getter
  private String defaultLocale = "en";
  @Getter
  private DatabaseConfig database = new DatabaseConfig();
  @Getter
  private Map<String, Messages> localizedMessages = new HashMap<>();

  public ConfigManager(VelocityWhitelist plugin, Logger logger, Path dataDirectory) {
    this.plugin = plugin;
    this.logger = logger;
    this.configFile = new File(dataDirectory.toFile(), "config.yml");
  }

  public void initConfig() {
    try {
      config = YamlDocument.create(configFile,
          Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")),
          GeneralSettings.DEFAULT,
          LoaderSettings.builder().setAutoUpdate(true).build(),
          DumperSettings.DEFAULT,
          UpdaterSettings.builder().setVersioning(new BasicVersioning("file-version"))
              .setOptionSorting(UpdaterSettings.OptionSorting.SORT_BY_DEFAULTS).build());

      config.update();
      config.save();

      loadConfig();
    } catch (IOException e) {
      throw new RuntimeException("Config initialize error. ", e); // Need to throw error, otherwise it won't unload.
    }
  }

  private void modifyConfigFile(String path, Object value) {
    config.set(path, value);
    try {
      config.save();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Reloads the plugin configuration.
   * This method reloads the configuration from the config file and updates the
   * plugin's settings.
   */
  public boolean reloadConfig() {
    plugin.logDebug("Reloading configuration");

    try {
      config.reload();
      loadConfig();
    } catch (IOException e) {
      logger.error("Error while reloading configuration", e);
      return false;
    }

    return true;
  }

  /**
   * Loads the configuration from the config file.
   * This method reads the config file and loads the properties into a Properties
   * object.
   *
   * @return The Properties object containing the configuration.
   */
  public void loadConfig() {
    plugin.logDebug("Loading configuration");

    debugEnabled = config.getBoolean("debug");
    pluginEnabled = config.getBoolean("enabled");
    defaultLocale = config.getString("defaultLocale");

    loadDatabaseCfg();
    loadMessages();

    logger.info("Debug mode is {}", debugEnabled ? "enabled" : "disabled");
  }

  private void loadDatabaseCfg() {
    val section = config.getSection("database");
    // construct the url
    val urlFormat = "jdbc:mariadb://%s:%s/%s%s";
    val host = section.getString("host");
    val port = section.getInt("port");
    val dbName = section.getString("database");
    val params = section.getString("params");
    val url = String.format(urlFormat, host, port, dbName, params);

    database.setUrl(url);
    database.setUser(section.getString("user"));
    database.setPassword(section.getString("password"));
    database.setWhitelistTable(section.getString("whitelistTable"));
    database.setCreateTables(section.getBoolean("createTables"));
    database.setMaxPoolSize(section.getInt("maxPoolSize"));
    database.setMinIdle(section.getInt("minIdle"));
    database.setConnectionTimeout(section.getLong("connectionTimeout"));
    database.setIdleTimeout(section.getLong("idleTimeout"));
    database.setMaxLifetime(section.getLong("maxLifetime"));
    database.setCacheStmt(section.getBoolean("cacheStmt"));
    database.setPrepStmtCacheSize(section.getInt("prepStmtCacheSize"));
    database.setPrepStmtCacheSqlLimit(section.getInt("prepStmtCacheSqlLimit"));
    database.setUseServerPrepStmts(section.getBoolean("useServerPrepStmts"));
    database.setUseLocalSessionState(section.getBoolean("useLocalSessionState"));
    database.setCacheServerConfiguration(section.getBoolean("cacheServerConfiguration"));
    database.setElideSetAutoCommit(section.getBoolean("elideSetAutoCommit"));
    database.setMaintainTimeStats(section.getBoolean("maintainTimeStats"));
  }

  private void loadMessages() {
    localizedMessages.clear();

    val kicked = initComp("messages.kicked");
    val insufficientPermission = initComp("messages.insufficientPermission");

    val messages = new Messages()
        .setKicked(kicked)
        .setInsufficientPermission(insufficientPermission);
    localizedMessages.put(defaultLocale, messages);
  }

  public void setDebugMode(boolean enabled) {
    debugEnabled = enabled;
    modifyConfigFile("debug", enabled);
  }

  public void setPluginEnabled(boolean enabled) {
    pluginEnabled = enabled;
    modifyConfigFile("enabled", enabled);
  }

  private Component initComp(String path) {
    val str = getConfStr(path);
    return MiniMessage.miniMessage().deserialize(str);
  }

  private String getConfStr(String path) {
    return config.getString(path).replace("\\n", "\n");
  }

}
