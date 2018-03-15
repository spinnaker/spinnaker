package com.netflix.kayenta.datadog.config;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class DatadogConfigurationProperties {
  @Getter
  private List<DatadogManagedAccount> accounts = new ArrayList<>();
}
