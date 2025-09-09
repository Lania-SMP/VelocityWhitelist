package net.lania.whitelist.storage;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.val;
import net.lania.whitelist.ConfigManager;
import net.lania.whitelist.VelocityWhitelist;

public class MySqlStorage {

  private final VelocityWhitelist plugin;
  private final Logger logger;
  private final ConfigManager configHandler;

  private HikariDataSource ds;

  public MySqlStorage(VelocityWhitelist plugin, Logger logger, ConfigManager configHandler) {
    this.plugin = plugin;
    this.logger = logger;
    this.configHandler = configHandler;
  }

  public boolean init() {
    loadDriver();
    if (!openConnection()) {
      return false;
    }
    
    try (val conn = ds.getConnection()) {
      if (conn.isValid(1)) {
        logger.info("Successfully connected to the database");
      } else {
        logger.error("Failed to connect to the database");
        return false;
      }
    } catch (SQLException sqe) {
      logger.error("Failed to connect to the database", sqe);
      return false;
    }
    return true;
  }

  public boolean openConnection() {
    val cfg = configHandler.getDatabase();
    try {
      val config = new HikariConfig();
      config.setJdbcUrl(cfg.getUrl());
      config.setUsername(cfg.getUser());
      config.setPassword(cfg.getPassword());
      config.setDriverClassName("org.mariadb.jdbc.Driver");
      config.setMaximumPoolSize(cfg.getMaxPoolSize());
      config.setMinimumIdle(cfg.getMinIdle());
      config.setConnectionTimeout(cfg.getConnectionTimeout());
      config.setIdleTimeout(cfg.getIdleTimeout());
      config.setMaxLifetime(cfg.getMaxLifetime());
      config.setConnectionTestQuery("SELECT 1");
      config.addDataSourceProperty("cachePrepStmts", String.valueOf(cfg.isCacheStmt()));
      config.addDataSourceProperty("prepStmtCacheSize", String.valueOf(cfg.getPrepStmtCacheSize()));
      config.addDataSourceProperty("prepStmtCacheSqlLimit", String.valueOf(cfg.getPrepStmtCacheSqlLimit()));
      config.addDataSourceProperty("useServerPrepStmts", String.valueOf(cfg.isUseServerPrepStmts()));
      config.addDataSourceProperty("useLocalSessionState", String.valueOf(cfg.isUseLocalSessionState()));
      config.addDataSourceProperty("cacheServerConfiguration", String.valueOf(cfg.isCacheServerConfiguration()));
      config.addDataSourceProperty("elideSetAutoCommits", String.valueOf(cfg.isElideSetAutoCommit()));
      config.addDataSourceProperty("maintainTimeStats", String.valueOf(cfg.isMaintainTimeStats()));
      ds = new HikariDataSource(config);

      if (cfg.isCreateTables()) {
        createDatabaseTable();
      }
      return true;
    } catch (SQLException sqe) {
      logger.error("Error while connecting to the database: {}", sqe.getMessage());
      return false;
    }
  }

  private void loadDriver() {
    try {
      Class.forName("org.mariadb.jdbc.Driver");
    } catch (ClassNotFoundException e) {
      logger.error("MariaDB JDBC Driver not found: {}", e.getMessage());
      throw new RuntimeException("MariaDB JDBC Driver not found", e);
    }
  }

  public void closeConnection() {
    if (ds != null && !ds.isClosed()) {
      ds.close();
    }
  }

  private final String CREATE_TABLE_SQL = """
      CREATE TABLE IF NOT EXISTS %s (
        mc_uuid varchar(36) PRIMARY KEY,
        username varchar(100) NOT NULL
      )
      """;

  /**
   * Creates the database table if it doesn't exist.
   * This method opens a connection to the database, executes the SQL query,
   * and then closes the connection.
   */
  public void createDatabaseTable() throws SQLException {
    plugin.logDebug("Creating database table");

    val query = String.format(CREATE_TABLE_SQL, configHandler.getDatabase().getWhitelistTable());
    try (val conn = ds.getConnection(); val st = conn.prepareStatement(query)) {
      st.executeUpdate();
    }
  }

  private final String FIND_ENTRY_BY_UNIQUE_ID_SQL = """
      SELECT mc_uuid FROM %s WHERE mc_uuid = ?
      """;

  public int findEntryByUniqueId(@NotNull UUID uniqueId) {
    val query = String.format(FIND_ENTRY_BY_UNIQUE_ID_SQL, configHandler.getDatabase().getWhitelistTable());
    try (val conn = ds.getConnection(); val st = conn.prepareStatement(query)) {
      st.setString(1, uniqueId.toString());
      val result = st.executeQuery();
      return result.next() ? 1 : 0;
    } catch (SQLException e) {
      logger.error("Error while checking if user is whitelisted", e);
      return -1;
    }
  }

  private final String FIND_USERNAME_LIKE_STRING_SQL = """
      SELECT p.mc_username
      FROM %s a
      INNER JOIN profiles p ON p.mc_uuid = a.mc_uuid
      WHERE p.mc_username LIKE ?
      LIMIT ?
      """;

  public List<String> findUsernameLikeString(@NotNull String remaining, int limit) {
    val resultList = new ArrayList<String>();

    val query = String.format(FIND_USERNAME_LIKE_STRING_SQL, configHandler.getDatabase().getWhitelistTable());
    try (val conn = ds.getConnection(); val st = conn.prepareStatement(query)) {
      st.setString(1, remaining + "%");
      st.setInt(2, limit);
      try (val result = st.executeQuery()) {
        while (result.next()) {
          resultList.add(result.getString("mc_username"));
        }
      }
    } catch (SQLException e) {
      logger.error("Error while finding username like string", e);
      return Collections.emptyList();
    }
    return resultList;
  }

  // TODO: Call backend API to insert whitelist
  private final String INSERT_WHITELIST_SQL = """
      INSERT INTO %s (mc_uuid, mc_username)
      VALUES (?, ?)
      ON DUPLICATE KEY UPDATE unique_id = VALUES(unique_id)
      """;

  public boolean insertWhitelist(@NotNull UUID uniqueId, @NotNull String username) {
    val query = String.format(INSERT_WHITELIST_SQL, configHandler.getDatabase().getWhitelistTable());
    try (val conn = ds.getConnection(); val st = conn.prepareStatement(query)) {
      st.setString(1, uniqueId.toString());
      st.setString(2, username);
      st.executeUpdate();
      return true;
    } catch (SQLException e) {
      logger.error("Error while inserting whitelist", e);
      return false;
    }
  }

  private final String DELETE_WHITELIST_SQL = """
      DELETE FROM %s
      WHERE mc_uuid = ?
      """;

  // TODO: Call backend API to delete whitelist
  public boolean deleteWhitelist(@NotNull UUID uniqueId) {
    val query = String.format(DELETE_WHITELIST_SQL, configHandler.getDatabase().getWhitelistTable());
    try (val conn = ds.getConnection(); val st = conn.prepareStatement(query)) {
      st.setString(1, uniqueId.toString());
      st.executeUpdate();
      return true;
    } catch (SQLException e) {
      logger.error("Error while deleting whitelist", e);
      return false;
    }
  }

}
