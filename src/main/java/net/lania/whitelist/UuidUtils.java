package net.lania.whitelist;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import lombok.val;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UuidUtils {

  private static final String UUID_SOURCE_FORMAT = "OfflinePlayer:%s";

  public static UUID generateUniqueId(@NotNull String username) {
    val source = String.format(UUID_SOURCE_FORMAT, username);
    return UUID.nameUUIDFromBytes(source.getBytes());
  }
}
