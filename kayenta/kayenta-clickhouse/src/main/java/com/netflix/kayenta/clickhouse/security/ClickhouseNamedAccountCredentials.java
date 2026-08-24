package com.netflix.kayenta.clickhouse.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.kayenta.clickhouse.service.ClickhouseRemoteService;
import com.netflix.kayenta.security.AccountCredentials;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class ClickhouseNamedAccountCredentials extends AccountCredentials<ClickhouseCredentials> {

  @NotNull private ClickhouseCredentials credentials;

  @JsonIgnore private ClickhouseRemoteService clickhouseRemoteService;

  @Override
  public String getType() {
    return "clickhouse";
  }
}
