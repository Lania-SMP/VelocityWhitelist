package net.lania.whitelist.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import lombok.val;
import net.lania.whitelist.ConfigManager;
import net.lania.whitelist.VelocityWhitelist;

public class MySqlStorage {

  private final VelocityWhitelist plugin;
  private final Logger logger;
  private final ConfigManager configHandler;

  private Connection connection;

  public MySqlStorage(VelocityWhitelist plugin, Logger logger, ConfigManager configHandler) {
    this.plugin = plugin;
    this.logger = logger;
    this.configHandler = configHandler;
    new org.mariadb.jdbc.Driver();
  }

  private void checkConnection() {
    if (connection == null) {
      openConnection();
    }
  }

  /**
   * Opens a connection to the MySQL database.
   * This method uses the configuration properties to establish a connection.
   * This method is synchronized to prevent concurrent access issues.
   */
  public synchronized void openConnection() {
    plugin.logDebug("Opening database connection");

    if (connection != null) {
      closeConnection();
    }

    try {
      connection = DriverManager.getConnection(
          configHandler.getDatabaseUrl(),
          configHandler.getDatabaseUser(),
          configHandler.getDatabasePassword());
    } catch (SQLException e) {
      throw new RuntimeException("Error while opening connection", e);
    }
  }

  /**
   * Closes the database connection.
   * This method is synchronized to prevent concurrent access issues.
   */
  public synchronized void closeConnection() {
    try {
      connection.close();
    } catch (SQLException e) {
      throw new RuntimeException("Error while closing connection", e);
    }
  }

  private final String CREATE_TABLE_SQL = """
      CREATE TABLE IF NOT EXISTS %s (
        unique_id varchar(36) PRIMARY KEY,
        username varchar(100) NOT NULL
      )
      """;

  /**
   * Creates the database table if it doesn't exist.
   * This method opens a connection to the database, executes the SQL query,
   * and then closes the connection.
   */
  public void createDatabaseTable() {
    plugin.logDebug("Creating database table");

    checkConnection();

    val query = String.format(CREATE_TABLE_SQL, configHandler.getTable());
    try (val sql = connection.prepareStatement(query)) {
      sql.execute();
    } catch (SQLException e) {
      logger.error("Error while creating database table", e);
    }
  }

  private final String FIND_ENTRY_BY_UNIQUE_ID_SQL = """
      SELECT * FROM %s
      WHERE unique_id = ?
      """;

  public boolean findEntryByUniqueId(@NotNull UUID uniqueId) {
    checkConnection();
    val query = String.format(FIND_ENTRY_BY_UNIQUE_ID_SQL, configHandler.getTable());
    try (val sql = connection.prepareStatement(query)) {
      sql.setString(1, uniqueId.toString());
      val result = sql.executeQuery();
      return result.next();
    } catch (SQLException e) {
      logger.error("Error while checking if user is whitelisted", e);
      return false;
    }
  }

  private final String UPDATE_USERNAME_BY_UNIQUE_ID_SQL = """
      UPDATE %s
      SET username = ?
      WHERE unique_id = ?
      """;

  public void updateUsernameByUniqueId(@NotNull UUID uniqueId, @NotNull String username) {
    checkConnection();
    val query = String.format(UPDATE_USERNAME_BY_UNIQUE_ID_SQL, configHandler.getTable());
    try (val sql = connection.prepareStatement(query)) {
      sql.setString(1, username);
      sql.setString(2, uniqueId.toString());
      sql.executeUpdate();
    } catch (SQLException e) {
      logger.error("Error while updating username by unique id", e);
    }
  }

  private final String FIND_USERNAME_LIKE_STRING_SQL = """
      SELECT username FROM %s
      WHERE username LIKE ?
      LIMIT ?
      """;

  public List<String> findUsernameLikeString(@NotNull String remaining, int limit) {
    checkConnection();
    val resultList = new ArrayList<String>();

    val query = String.format(FIND_USERNAME_LIKE_STRING_SQL, configHandler.getTable());
    try (val sql = connection.prepareStatement(query)) {
      sql.setString(1, remaining + "%");
      sql.setInt(2, limit);
      try (val result = sql.executeQuery()) {
        while (result.next()) {
          resultList.add(result.getString("username"));
        }
      }
    } catch (SQLException e) {
      logger.error("Error while finding username like string", e);
      return Collections.emptyList();
    }
    return resultList;
  }

  private final String INSERT_WHITELIST_SQL = """
      INSERT INTO %s (unique_id, username)
      VALUES (?, ?)
      ON DUPLICATE KEY UPDATE unique_id = VALUES(unique_id)
      """;

  public boolean insertWhitelist(@NotNull UUID uniqueId, @NotNull String username) {
    checkConnection();
    val query = String.format(INSERT_WHITELIST_SQL, configHandler.getTable());
    try (val sql = connection.prepareStatement(query)) {
      sql.setString(1, uniqueId.toString());
      sql.setString(2, username);
      sql.executeUpdate();
      return true;
    } catch (SQLException e) {
      logger.error("Error while inserting whitelist", e);
      return false;
    }
  }

  private final String DELETE_WHITELIST_SQL = """
      DELETE FROM %s
      WHERE unique_id = ?
      """;

  public boolean deleteWhitelist(@NotNull UUID uniqueId) {
    checkConnection();
    val query = String.format(DELETE_WHITELIST_SQL, configHandler.getTable());
    try (val sql = connection.prepareStatement(query)) {
      sql.setString(1, uniqueId.toString());
      sql.executeUpdate();
      return true;
    } catch (SQLException e) {
      logger.error("Error while deleting whitelist", e);
      return false;
    }
  }

}
