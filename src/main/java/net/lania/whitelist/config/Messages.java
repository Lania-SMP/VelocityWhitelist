package net.lania.whitelist.config;

import lombok.Data;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.Component;

@Data
@Accessors(chain = true)
public class Messages {

  private Component kicked;
  private Component failedToCheckWhitelist;
  private Component insufficientPermission;

}
