/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.igor.polling;

import static java.lang.String.format;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.discovery.RemoteStatusChangedEvent;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import net.logstash.logback.argument.StructuredArguments;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

public abstract class CommonPollingMonitor<I extends DeltaItem, T extends PollingDelta<I>>
    implements PollingMonitor, PollAccess {

  protected final IgorConfigurationProperties igorProperties;
  protected final Registry registry;
  protected final Id missedNotificationId;
  private final DiscoveryStatusListener discoveryStatusListener;
  private final AtomicLong lastPoll = new AtomicLong();
  private final Optional<LockService> lockService;
  private ScheduledFuture<?> monitor;
  private final CommonPollingMonitorInstrumentation instrumentation;
  protected Logger log = LoggerFactory.getLogger(getClass());
  protected TaskScheduler scheduler;
  protected final DynamicConfigService dynamicConfigService;

  private Map<String, AtomicInteger> itemsOverThresholdMap = new ConcurrentHashMap<>();
  private Map<String, AtomicInteger> itemsCachedMap = new ConcurrentHashMap<>();

  public CommonPollingMonitor(
      IgorConfigurationProperties igorProperties,
      Registry registry,
      DynamicConfigService dynamicConfigService,
      DiscoveryStatusListener discoveryStatusListener,
      Optional<LockService> lockService,
      TaskScheduler scheduler) {
    this.igorProperties = igorProperties;
    this.registry = registry;
    this.dynamicConfigService = dynamicConfigService;
    this.discoveryStatusListener = discoveryStatusListener;
    this.lockService = lockService;
    this.scheduler = scheduler;
    this.instrumentation = new CommonPollingMonitorInstrumentation(registry);

    missedNotificationId = registry.createId("pollingMonitor.missedEchoNotification");
  }

  @Override
  public void onApplicationEvent(RemoteStatusChangedEvent event) {
    if (!isPollingEnabled()) {
      log.info("Polling disabled, not scheduling periodic work");
      return;
    }

    initialize();
    this.monitor =
        scheduler.schedule(
            () ->
                instrumentation.trackPollCycleTime(
                    this.getName(),
                    () -> {
                      if (isInService()) {
                        poll(true);
                        lastPoll.set(System.currentTimeMillis());
                      } else {
                        log.info(
                            "not in service (lastPoll: {})",
                            (lastPoll.get() == 0) ? "n/a" : lastPoll.toString());
                        lastPoll.set(0);
                      }
                    }),
            new PeriodicTrigger(getPollInterval(), TimeUnit.SECONDS));
  }

  @PreDestroy
  void stop() {
    log.info("Stopped");
    if (monitor != null && !monitor.isDone()) {
      monitor.cancel(false);
    }
  }

  protected void initialize() {}

  /**
   * Returns a delta of stored state versus newly polled data. A polling monitor must not perform
   * writes in this method.
   */
  protected abstract T generateDelta(PollContext ctx);

  /**
   * Commits a delta of polled state that was created in generateDelta, assuming circuit breakers
   * have not been tripped.
   */
  protected abstract void commitDelta(T delta, boolean sendEvents);

  public PollContext getPollContext(String partition) {
    return new PollContext(partition);
  }

  @Override
  public void pollSingle(PollContext ctx) {
    if (lockService.isPresent()) {
      // Lock duration of the full poll interval; if the work is completed ahead of that time, it'll
      // be released.
      // If anything, this will mean builds are polled more often, rather than less.
      final String lockName = getLockName(getName(), ctx.partitionName);
      lockService
          .get()
          .acquire(lockName, Duration.ofSeconds(getPollInterval()), () -> internalPollSingle(ctx));
    } else {
      log.warn("****LOCKING NOT ENABLED***, not recommended running on more than one node.");
      internalPollSingle(ctx);
    }
  }

  /**
   * Method to construct a lockName and sanitise it to remove special characters as per kork
   * validation
   *
   * @param name
   * @param partition
   * @return
   */
  protected String getLockName(String name, String partition) {

    String lockName = format("%s.%s", name, partition);
    if (!lockName.matches("^[a-zA-Z0-9.-]+$")) {
      return lockName.replaceAll("[^a-zA-Z0-9.-]", "");
    }
    return lockName;
  }

  protected void internalPollSingle(PollContext ctx) {
    String monitorName =
        !StringUtils.isBlank(this.getName()) ? this.getName() : getClass().getSimpleName();

    itemsCachedMap.putIfAbsent(ctx.partitionName, new AtomicInteger(0));
    itemsOverThresholdMap.putIfAbsent(ctx.partitionName, new AtomicInteger(0));

    instrumentation.trackItemsCached(
        itemsCachedMap.get(ctx.partitionName), monitorName, ctx.partitionName);

    instrumentation.trackItemsOverThreshold(
        itemsOverThresholdMap.get(ctx.partitionName), monitorName, ctx.partitionName);

    try {
      T delta = generateDelta(ctx);

      int upperThreshold =
          Optional.ofNullable(getPartitionUpperThreshold(ctx.partitionName))
              .orElse(igorProperties.getSpinnaker().getPollingSafeguard().getItemUpperThreshold());

      boolean sendEvents = !ctx.fastForward;
      int deltaSize = delta.getItems().size();
      if (deltaSize > upperThreshold) {
        itemsOverThresholdMap.get(ctx.partitionName).set(deltaSize);
        if (ctx.fastForward) {
          log.warn(
              "Fast forwarding items ({}) in {} {}",
              itemsOverThresholdMap.get(ctx.partitionName).get(),
              StructuredArguments.kv("monitor", monitorName),
              StructuredArguments.kv("partition", ctx.partitionName));
          sendEvents = false;
        } else {
          log.error(
              "Number of items ({}) to cache exceeds upper threshold ({}) in {} {}",
              itemsOverThresholdMap.get(ctx.partitionName).get(),
              upperThreshold,
              StructuredArguments.kv("monitor", monitorName),
              StructuredArguments.kv("partition", ctx.partitionName));
          return;
        }
      } else {
        itemsOverThresholdMap.get(ctx.partitionName).set(0);
      }

      sendEvents = sendEvents && isSendEventsEnabled();

      commitDelta(delta, sendEvents);
      itemsCachedMap.get(ctx.partitionName).set(deltaSize);
    } catch (Exception e) {
      log.error(
          "Failed to update monitor items for {}:{}",
          StructuredArguments.kv("monitor", monitorName),
          StructuredArguments.kv("partition", ctx.partitionName),
          e);
      instrumentation.trackPollCycleFailed(monitorName, ctx.partitionName);
      itemsCachedMap.get(ctx.partitionName).set(0);
      itemsOverThresholdMap.get(ctx.partitionName).set(0);
    }
  }

  @Override
  public boolean isInService() {
    if (!isPollingEnabled()) {
      log.info("not in service because spinnaker.build.pollingEnabled is set to false");
      return false;
    }

    return discoveryStatusListener.isEnabled();
  }

  @Override
  public int getPollInterval() {
    return igorProperties.getSpinnaker().getBuild().getPollInterval();
  }

  @Override
  public boolean isPollingEnabled() {
    return igorProperties.getSpinnaker().getBuild().isPollingEnabled();
  }

  @Override
  public Long getLastPoll() {
    return lastPoll.get();
  }

  private boolean isSendEventsEnabled() {
    return dynamicConfigService.getConfig(Boolean.class, "spinnaker.build.sendEventsEnabled", true);
  }

  protected @Nullable Integer getPartitionUpperThreshold(String partition) {
    return null;
  }
}
