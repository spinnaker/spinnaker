/*
 * Copyright 2020 Apple, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.helm;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.build.model.GenericArtifact;
import com.netflix.spinnaker.igor.helm.accounts.HelmAccount;
import com.netflix.spinnaker.igor.helm.accounts.HelmAccounts;
import com.netflix.spinnaker.igor.helm.cache.HelmCache;
import com.netflix.spinnaker.igor.helm.model.HelmIndex;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.history.model.HelmEvent;
import com.netflix.spinnaker.igor.polling.*;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.exceptions.ConstraintViolationException;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty("helm.enabled")
public class HelmMonitor
    extends CommonPollingMonitor<HelmMonitor.HelmDelta, HelmMonitor.HelmPollingDelta> {
  private final HelmCache cache;
  private final HelmAccounts helmAccounts;
  private final Optional<EchoService> echoService;

  @Autowired
  public HelmMonitor(
      IgorConfigurationProperties properties,
      Registry registry,
      DynamicConfigService dynamicConfigService,
      DiscoveryStatusListener discoveryStatusListener,
      Optional<LockService> lockService,
      HelmCache cache,
      HelmAccounts helmAccounts,
      Optional<EchoService> echoService,
      TaskScheduler taskScheduler) {
    super(
        properties,
        registry,
        dynamicConfigService,
        discoveryStatusListener,
        lockService,
        taskScheduler);
    this.cache = cache;
    this.helmAccounts = helmAccounts;
    this.echoService = echoService;
  }

  @Override
  public void poll(boolean sendEvents) {
    helmAccounts.updateAccounts();
    helmAccounts.accounts.forEach(
        account -> pollSingle(new PollContext(account.name, account.toMap(), !sendEvents)));
  }

  @Override
  public PollContext getPollContext(String partition) {
    Optional<HelmAccount> account =
        helmAccounts.accounts.stream().filter(it -> it.name.equals(partition)).findFirst();
    if (!account.isPresent()) {
      throw new ConstraintViolationException(
          String.format("Cannot find Helm account named '%s'", partition));
    }
    return new PollContext(account.get().name, account.get().toMap());
  }

  @Override
  protected HelmPollingDelta generateDelta(PollContext ctx) {
    final String account = ctx.partitionName;
    log.info("Checking for new Helm Charts {}", kv("account", account));

    List<HelmDelta> deltas = new ArrayList<>();

    HelmIndex index = helmAccounts.getIndex(account);
    if (index == null) {
      log.error("Failed to fetch Helm index {}", kv("account", account));
    } else {
      Set<String> cachedCharts = cache.getChartDigests(account);

      // If we have no cache at all, do not fire any trigger events
      // This is so we don't trigger every chart version if Redis dies
      boolean eventable = !cachedCharts.isEmpty();

      index.entries.forEach(
          (key, charts) ->
              charts.forEach(
                  chartEntry -> {
                    if (!StringUtils.isEmpty(chartEntry.digest)
                        && !cachedCharts.contains(chartEntry.digest)) {
                      deltas.add(
                          new HelmDelta(
                              chartEntry.name, chartEntry.version, chartEntry.digest, eventable));
                    }
                  }));
    }

    if (!deltas.isEmpty()) {
      List<HelmDelta> eventableDeltas =
          deltas.stream().filter(it -> it.eventable).collect(Collectors.toList());
      log.info(
          "Found Helm charts: {} {} {}",
          kv("account", account),
          kv("total", deltas.size()),
          kv("eventable", eventableDeltas.size()));
    }

    return new HelmPollingDelta(account, deltas);
  }

  @Override
  protected void commitDelta(HelmPollingDelta deltas, boolean sendEvents) {
    List<String> digests = deltas.items.stream().map(i -> i.digest).collect(Collectors.toList());

    // Cache results
    cache.cacheChartDigests(deltas.account, digests);

    // Send events, if needed
    deltas.items.forEach(
        item -> {
          if (sendEvents && item.eventable) {
            sendEvent(deltas.account, item);
          }
        });

    log.info(
        "Last Helm poll took {} ms {}",
        System.currentTimeMillis() - deltas.startTime,
        kv("account", deltas.account));
  }

  @Override
  public String getName() {
    return "helmMonitor";
  }

  private void sendEvent(String account, HelmDelta delta) {
    if (!echoService.isPresent()) {
      log.warn("Cannot send Helm notification: Echo is not enabled");
      registry
          .counter(missedNotificationId.withTag("monitor", getClass().getSimpleName()))
          .increment();
      return;
    }

    log.info(
        "Sending trigger event for {}:{} {}", delta.name, delta.version, kv("account", account));
    GenericArtifact helmArtifact =
        new GenericArtifact("helm/chart", delta.name, delta.version, account);

    HelmEvent.Content helmContent =
        new HelmEvent.Content(account, delta.name, delta.version, delta.digest);

    AuthenticatedRequest.allowAnonymous(
        () ->
            Retrofit2SyncCall.execute(
                echoService.get().postEvent(new HelmEvent(helmContent, helmArtifact))));
  }

  @AllArgsConstructor
  protected static class HelmDelta implements DeltaItem {
    final String name;
    final String version;
    final String digest;
    final boolean eventable;
  }

  protected static class HelmPollingDelta implements PollingDelta<HelmDelta> {
    final String account;
    final List<HelmDelta> items;
    final Long startTime;

    public HelmPollingDelta(String account, List<HelmDelta> items) {
      this.account = account;
      this.items = items;
      startTime = System.currentTimeMillis();
    }

    @Override
    public List<HelmDelta> getItems() {
      return items;
    }
  }
}
