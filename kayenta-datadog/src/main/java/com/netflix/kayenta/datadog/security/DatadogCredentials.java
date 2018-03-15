package com.netflix.kayenta.datadog.security;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Builder
@Data
@Slf4j
public class DatadogCredentials {
  private static String applicationVersion =
    Optional.ofNullable(DatadogCredentials.class.getPackage().getImplementationVersion()).orElse("Unknown");

  private String apiKey;
  private String applicationKey;
}
