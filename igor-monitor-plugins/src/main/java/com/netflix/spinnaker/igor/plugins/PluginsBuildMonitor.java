/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.igor.plugins;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.plugins.front50.PluginReleaseService;
import com.netflix.spinnaker.igor.plugins.model.PluginEvent;
import com.netflix.spinnaker.igor.plugins.model.PluginRelease;
import com.netflix.spinnaker.igor.polling.*;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Data;

public class PluginsBuildMonitor
    extends CommonPollingMonitor<
        PluginsBuildMonitor.PluginDelta, PluginsBuildMonitor.PluginPollingDelta> {

  private final PluginReleaseService pluginInfoService;
  private final PluginCache cache;
  private final Optional<EchoService> echoService;

  public PluginsBuildMonitor(
      IgorConfigurationProperties igorProperties,
      Registry registry,
      DynamicConfigService dynamicConfigService,
      Optional<DiscoveryClient> discoveryClient,
      Optional<LockService> lockService,
      PluginReleaseService pluginInfoService,
      PluginCache cache,
      Optional<EchoService> echoService) {
    super(igorProperties, registry, dynamicConfigService, discoveryClient, lockService);
    this.pluginInfoService = pluginInfoService;
    this.cache = cache;
    this.echoService = echoService;
  }

  @Override
  protected PluginPollingDelta generateDelta(PollContext ctx) {
    return new PluginPollingDelta(
        pluginInfoService.getPluginReleasesSince(cache.getLastPollCycleTimestamp()).stream()
            .map(PluginDelta::new)
            .collect(Collectors.toList()));
  }

  @Override
  protected void commitDelta(PluginPollingDelta delta, boolean sendEvents) {
    log.info("Found {} new plugin releases", delta.items.size());
    delta.items.forEach(
        item -> {
          if (sendEvents) {
            postEvent(item.pluginRelease);
          } else {
            log.debug("{} processed, but not sending event", item.pluginRelease);
          }
        });

    delta.items.stream()
        .map(it -> Instant.parse(it.pluginRelease.getReleaseDate()))
        .max(Comparator.naturalOrder())
        .ifPresent(cache::setLastPollCycleTimestamp);
  }

  private void postEvent(PluginRelease release) {
    if (!echoService.isPresent()) {
      log.warn("Cannot send new plugin notification: Echo is not configured");
      registry.counter(missedNotificationId.withTag("monitor", getName())).increment();
    } else if (release != null) {
      AuthenticatedRequest.allowAnonymous(
          () -> echoService.get().postEvent(new PluginEvent(release)));
      log.debug("{} event posted", release);
    }
  }

  @Override
  public void poll(boolean sendEvents) {
    pollSingle(new PollContext("front50"));
  }

  @Override
  public String getName() {
    return "pluginsMonitor";
  }

  @Data
  static class PluginDelta implements DeltaItem {
    private final PluginRelease pluginRelease;
  }

  @Data
  static class PluginPollingDelta implements PollingDelta<PluginDelta> {
    private final List<PluginDelta> items;
  }
}
