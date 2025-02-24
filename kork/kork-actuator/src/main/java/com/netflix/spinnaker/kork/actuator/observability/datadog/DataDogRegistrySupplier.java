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
import com.netflix.spinnaker.kork.actuator.observability.model.ObservabilityConfigurationProperites;
import com.netflix.spinnaker.kork.actuator.observability.registry.RegistryConfigWrapper;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.datadog.DatadogMeterRegistry;
import java.time.Duration;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

public class DataDogRegistrySupplier implements Supplier<RegistryConfigWrapper> {

  private final MetricsDatadogConfig datadogConfig;
  protected HttpUrlConnectionSender sender;

  public DataDogRegistrySupplier(@NotNull ObservabilityConfigurationProperites pluginConfig) {
    datadogConfig = pluginConfig.getMetrics().getDatadog();
    this.sender =
        new HttpUrlConnectionSender(
            Duration.ofSeconds(datadogConfig.getConnectDurationSeconds()),
            Duration.ofSeconds(datadogConfig.getReadDurationSeconds()));
  }

  @Override
  public RegistryConfigWrapper get() {
    if (!datadogConfig.isEnabled()) {
      return null;
    }
    var config = new DataDogRegistryConfig(datadogConfig);
    var registry = DatadogMeterRegistry.builder(config).httpClient(sender).build();

    return RegistryConfigWrapper.builder()
        .meterRegistry(registry)
        .meterRegistryConfig(datadogConfig.getRegistry())
        .build();
  }
}
