/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.spinnaker.kork.observability.newrelic;

import com.netflix.spinnaker.kork.observability.model.MetricsNewRelicConfig;
import com.netflix.spinnaker.kork.observability.model.ObservabilityConfigurationProperites;
import com.netflix.spinnaker.kork.observability.registry.RegistryConfigWrapper;
import com.netflix.spinnaker.kork.observability.service.TagsService;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.newrelic.NewRelicRegistry;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.springframework.context.annotation.Configuration;

/**
 * A Supplier bean that can be registered with Spring without providing an actual registry
 * implementation to confuse Spectator/Micrometer. This Supplier configures a New Relic Micrometer
 * Registry Instance.
 */
@Configuration
public class NewRelicRegistrySupplier implements Supplier<RegistryConfigWrapper> {

  protected HttpUrlConnectionSender sender;
  private final MetricsNewRelicConfig newRelicConfig;
  private final TagsService tagsService;
  private static final double ONE_MINUTE_IN_SECONDS = 60d;

  public NewRelicRegistrySupplier(
      ObservabilityConfigurationProperites pluginConfig, TagsService tagsService) {

    newRelicConfig = pluginConfig.getMetrics().getNewrelic();
    this.tagsService = tagsService;
    Proxy proxy = null;
    if (newRelicConfig.getProxyHost() != null && newRelicConfig.getProxyPort() != null) {
      proxy =
          new Proxy(
              Proxy.Type.HTTP,
              new InetSocketAddress(newRelicConfig.getProxyHost(), newRelicConfig.getProxyPort()));
    }
    this.sender =
        new HttpUrlConnectionSender(
            Duration.ofSeconds(newRelicConfig.getConnectDurationSeconds()),
            Duration.ofSeconds(newRelicConfig.getReadDurationSeconds()),
            proxy);
  }

  @Override
  public RegistryConfigWrapper get() {
    if (!newRelicConfig.isEnabled()) {
      return null;
    }

    var config = new NewRelicRegistryConfig(newRelicConfig);
    var registry = new NewRelicRegistry.NewRelicRegistryBuilder(config).httpSender(sender).build();

    registry.gauge(
        "metrics.dpm",
        newRelicConfig.getRegistry().isDefaultTagsDisabled()
            ? tagsService.getDefaultTags()
            : List.of(),
        registry,
        reg ->
            reg.getMeters().size() * (ONE_MINUTE_IN_SECONDS / newRelicConfig.getStepInSeconds()));

    registry.start(Executors.defaultThreadFactory());
    return RegistryConfigWrapper.builder()
        .meterRegistry(registry)
        .meterRegistryConfig(newRelicConfig.getRegistry())
        .build();
  }
}
