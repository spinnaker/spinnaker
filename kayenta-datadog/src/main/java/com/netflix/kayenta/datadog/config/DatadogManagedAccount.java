package com.netflix.kayenta.datadog.config;

import com.netflix.kayenta.retrofit.config.RemoteService;
import com.netflix.kayenta.security.AccountCredentials;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class DatadogManagedAccount {
  @NotNull
  private String name;
  private String apiKey;
  private String applicationKey;

  @NotNull
  private RemoteService endpoint;

  private List<AccountCredentials.Type> supportedTypes;
}

