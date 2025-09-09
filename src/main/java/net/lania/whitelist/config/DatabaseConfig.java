package net.lania.whitelist.config;

import lombok.Data;

@Data
public class DatabaseConfig {

  private String url = "jdbc:mariadb://%s:%s/%s?useSSL=false";
  private String user = "root";
  private String password = "1q2w3e4r";
  private String whitelistTable = "g_whitelist";
  private boolean createTables = true;

  // HikariCP fields
  private int maxPoolSize;
  private int minIdle;
  private long connectionTimeout;
  private long idleTimeout;
  private long maxLifetime;
  private boolean cacheStmt;
  private int prepStmtCacheSize;
  private int prepStmtCacheSqlLimit;
  private boolean useServerPrepStmts;
  private boolean useLocalSessionState;
  private boolean cacheServerConfiguration;
  private boolean elideSetAutoCommit;
  private boolean maintainTimeStats;

}
