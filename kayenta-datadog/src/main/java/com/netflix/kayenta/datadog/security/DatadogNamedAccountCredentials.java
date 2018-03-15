package com.netflix.kayenta.datadog.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.kayenta.datadog.service.DatadogRemoteService;
import com.netflix.kayenta.retrofit.config.RemoteService;
import com.netflix.kayenta.security.AccountCredentials;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import javax.validation.constraints.NotNull;
import java.util.List;

@Builder
@Data
public class DatadogNamedAccountCredentials implements AccountCredentials<DatadogCredentials> {
  @NotNull
  private String name;

  @NotNull
  @Singular
  private List<Type> supportedTypes;

  @NotNull
  private DatadogCredentials credentials;

  @NotNull
  private RemoteService endpoint;

  @Override
  public String getType() {
    return "datadog";
  }

  @JsonIgnore
  DatadogRemoteService datadogRemoteService;
}
