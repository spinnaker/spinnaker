package com.netflix.kayenta.clickhouse.security;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ClickhouseCredentials {

  private String endpointUrl;
  private String username;
  private String password;
  private String database;
}
