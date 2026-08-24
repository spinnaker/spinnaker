package com.netflix.kayenta.clickhouse.config;

import com.netflix.kayenta.security.AccountCredentials;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Data;

@Data
public class ClickhouseManagedAccount {

  @NotNull private String name;

  /** e.g. https://my-clickhouse-host:8443 */
  @NotNull private String endpointUrl;

  @Nullable private String username;

  @Nullable private String password;

  /** Optional default database/schema for the connection. */
  @Nullable private String database;

  private List<AccountCredentials.Type> supportedTypes =
      Collections.singletonList(AccountCredentials.Type.METRICS_STORE);
}
