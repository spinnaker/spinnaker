/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import static net.logstash.logback.argument.StructuredArguments.kv;

import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Scheduler;
import rx.schedulers.Schedulers;

public abstract class CommonPollingMonitor<I extends DeltaItem, T extends PollingDelta<I>>
    implements PollingMonitor, PollAccess {

  protected Logger log = LoggerFactory.getLogger(getClass());

  private final Optional<DiscoveryClient> discoveryClient;
  protected Scheduler.Worker worker;

  protected final IgorConfigurationProperties igorProperties;
  protected final Registry registry;

  private final AtomicLong lastPoll = new AtomicLong();

  private final Id itemsCachedId;
  private final Id itemsOverThresholdId;
  private final Id pollCycleFailedId;
  private final Id pollCycleTimingId;
  protected final Id missedNotificationId;

  private final Optional<LockService> lockService;

  public CommonPollingMonitor(
      IgorConfigurationProperties igorProperties,
      Registry registry,
      Optional<DiscoveryClient> discoveryClient,
      Optional<LockService> lockService) {
    this(igorProperties, registry, discoveryClient, lockService, Schedulers.io());
  }

  public CommonPollingMonitor(
      IgorConfigurationProperties igorProperties,
      Registry registry,
      Optional<DiscoveryClient> discoveryClient,
      Optional<LockService> lockService,
      Scheduler scheduler) {
    this.igorProperties = igorProperties;
    this.registry = registry;
    this.discoveryClient = discoveryClient;
    this.lockService = lockService;
    this.worker = scheduler.createWorker();

    itemsCachedId = registry.createId("pollingMonitor.newItems");
    itemsOverThresholdId = registry.createId("pollingMonitor.itemsOverThreshold");
    pollCycleFailedId = registry.createId("pollingMonitor.failed");
    missedNotificationId = registry.createId("pollingMonitor.missedEchoNotification");
    pollCycleTimingId = registry.createId("pollingMonitor.pollTiming");
  }

  @Override
  public void onApplicationEvent(RemoteStatusChangedEvent event) {
    log.info("Started");
    initialize();
    worker.schedulePeriodically(
        () -> {
          registry
              .timer(pollCycleTimingId.withTag("monitor", getClass().getSimpleName()))
              .record(
                  () -> {
                    if (isInService()) {
                      poll(true);
                      lastPoll.set(System.currentTimeMillis());
                    } else {
                      log.info(
                          "not in service (lastPoll: {})",
                          (lastPoll == null) ? "n/a" : lastPoll.toString());
                      lastPoll.set(0);
                    }
                  });
        },
        0,
        getPollInterval(),
        TimeUnit.SECONDS);
  }

  @PreDestroy
  void stop() {
    log.info("Stopped");
    if (!worker.isUnsubscribed()) {
      worker.unsubscribe();
    }
  }

  protected abstract void initialize();

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
      final String lockName = format("%s.%s", getName(), ctx.partitionName);
      lockService
          .get()
          .acquire(lockName, Duration.ofSeconds(getPollInterval()), () -> internalPollSingle(ctx));
    } else {
      log.warn("****LOCKING NOT ENABLED***, not recommended running on more than one node.");
      internalPollSingle(ctx);
    }
  }

  protected void internalPollSingle(PollContext ctx) {
    String monitorName = getClass().getSimpleName();

    try {
      T delta = generateDelta(ctx);

      int upperThreshold =
          Optional.ofNullable(getPartitionUpperThreshold(ctx.partitionName))
              .orElse(igorProperties.getSpinnaker().getPollingSafeguard().getItemUpperThreshold());

      boolean sendEvents = !ctx.fastForward;
      int deltaSize = delta.getItems().size();
      if (deltaSize > upperThreshold) {
        registry
            .gauge(
                itemsOverThresholdId.withTags(
                    "monitor", monitorName, "partition", ctx.partitionName))
            .set(deltaSize);
        if (ctx.fastForward) {
          log.warn(
              "Fast forwarding items ({}) in {} {}",
              deltaSize,
              kv("monitor", monitorName),
              kv("partition", ctx.partitionName));
          sendEvents = false;
        } else {
          log.error(
              "Number of items ({}) to cache exceeds upper threshold ({}) in {} {}",
              deltaSize,
              upperThreshold,
              kv("monitor", monitorName),
              kv("partition", ctx.partitionName));
          return;
        }
      } else {
        registry
            .gauge(
                itemsOverThresholdId.withTags(
                    "monitor", monitorName, "partition", ctx.partitionName))
            .set(0);
      }

      commitDelta(delta, sendEvents);
      registry
          .gauge(itemsCachedId.withTags("monitor", monitorName, "partition", ctx.partitionName))
          .set(deltaSize);
    } catch (Exception e) {
      log.error(
          "Failed to update monitor items for {}:{}",
          kv("monitor", monitorName),
          kv("partition", ctx.partitionName),
          e);
      registry
          .counter(
              pollCycleFailedId.withTags("monitor", monitorName, "partition", ctx.partitionName))
          .increment();
      registry
          .gauge(itemsCachedId.withTags("monitor", monitorName, "partition", ctx.partitionName))
          .set(0);
      registry
          .gauge(
              itemsOverThresholdId.withTags("monitor", monitorName, "partition", ctx.partitionName))
          .set(0);
    }
  }

  @Override
  public boolean isInService() {
    if (discoveryClient.isPresent()) {
      InstanceStatus remoteStatus = discoveryClient.get().getInstanceRemoteStatus();
      log.info("current remote status {}", remoteStatus);
      return remoteStatus == InstanceStatus.UP;
    } else {
      log.info("no DiscoveryClient, assuming InService");
      return true;
    }
  }

  @Override
  public int getPollInterval() {
    return igorProperties.getSpinnaker().getBuild().getPollInterval();
  }

  @Override
  public Long getLastPoll() {
    return lastPoll.get();
  }

  protected @Nullable Integer getPartitionUpperThreshold(String partition) {
    return null;
  }

  public void setWorker(Scheduler.Worker worker) {
    this.worker = worker;
  }
}
