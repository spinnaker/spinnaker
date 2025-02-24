/*
 * Copyright 2025 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.actuator.observability.datadog;

import com.netflix.spinnaker.kork.actuator.observability.model.MetricsDatadogConfig;
import java.util.Optional;

public class DataDogRegistryConfig implements io.micrometer.datadog.DatadogConfig {
  private final MetricsDatadogConfig datadogConfig;

  public DataDogRegistryConfig(MetricsDatadogConfig datadogConfig) {
    this.datadogConfig = datadogConfig;
  }

  @Override
  public String get(String key) {
    return null; // NOOP, source config from the PluginConfig that is injected
  }

  @Override
  public String apiKey() {
    return Optional.ofNullable(datadogConfig.getApiKey())
        .orElseThrow(
            () -> new RuntimeException("The datadog API key is a required plugin config property"));
  }

  @Override
  public String applicationKey() {
    return datadogConfig.getApplicationKey();
  }

  @Override
  public String uri() {
    return datadogConfig.getUri();
  }

  @Override
  public boolean descriptions() {
    return false;
  }

  @Override
  public String hostTag() {
    return "hostTag";
  }

  @Override
  public int batchSize() {
    return datadogConfig.getBatchSize();
  }
}
