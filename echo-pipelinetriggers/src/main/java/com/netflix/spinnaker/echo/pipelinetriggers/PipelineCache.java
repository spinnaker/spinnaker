/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers;

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.services.Front50Service;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static java.time.Instant.now;

@Component
@Slf4j
public class PipelineCache implements MonitoredPoller {
  private final int pollingIntervalMs;
  private final int pollingSleepMs;
  private final Front50Service front50;
  private final Registry registry;
  private final ScheduledExecutorService executorService;

  private transient Boolean running;
  private transient Instant lastPollTimestamp;

  @Nullable
  private transient List<Pipeline> pipelines;

  @Autowired
  public PipelineCache(@Value("${front50.pollingIntervalMs:10000}") int pollingIntervalMs,
                       @Value("${front50.pollingSleepMs:100}") int pollingSleepMs,
                       @NonNull Front50Service front50,
                       @NonNull Registry registry) {
    this(Executors.newSingleThreadScheduledExecutor(), pollingIntervalMs, pollingSleepMs, front50, registry);
  }

  // VisibleForTesting
  public PipelineCache(ScheduledExecutorService executorService,
                       int pollingIntervalMs,
                       int pollingSleepMs,
                       @NonNull Front50Service front50,
                       @NonNull Registry registry) {
    this.executorService = executorService;
    this.pollingIntervalMs = pollingIntervalMs;
    this.pollingSleepMs = pollingSleepMs;
    this.front50 = front50;
    this.registry = registry;
    this.running = false;
    this.pipelines = null;
  }

  @PreDestroy
  public void stop() {
    running = false;
    executorService.shutdown();
  }

  @PostConstruct
  public void start() {
    running = true;

    executorService.scheduleWithFixedDelay(
      new Runnable() {
        @Override
        public void run() {
          pollPipelineConfigs();
        }
      },
      0, pollingIntervalMs, TimeUnit.MILLISECONDS);

    PolledMeter
      .using(registry)
      .withName("front50.lastPoll")
      .monitorValue(this, PipelineCache::getDurationSeconds);
  }

  private Double getDurationSeconds() {
    return lastPollTimestamp == null
      ? -1d
      : (double) Duration.between(lastPollTimestamp, now()).getSeconds();
  }

  // VisibleForTesting
  void pollPipelineConfigs() {
    if (!isRunning()) {
      return;
    }

    try {
      log.debug("Getting pipelines from Front50...");
      long start = System.currentTimeMillis();
      pipelines = decorateTriggers(front50.getPipelines());

      lastPollTimestamp = now();
      registry.counter("front50.requests").increment();
      log.debug("Fetched {} pipeline configs in {}ms", pipelines.size(), System.currentTimeMillis() - start);
    } catch (Exception e) {
      log.error("Error fetching pipelines from Front50", e);
      registry.counter("front50.errors").increment();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public Instant getLastPollTimestamp() {
    return lastPollTimestamp;
  }

  @Override
  public int getPollingIntervalSeconds() {
    return (int) TimeUnit.MILLISECONDS.toSeconds(pollingIntervalMs);
  }

  /**
   * The list of pipelines as of the last successful polling cycle, or null if not available yet
   */
  @Nullable
  public List<Pipeline> getPipelines() {
    return pipelines;
  }

  @Nonnull
  public List<Pipeline> getPipelinesSync() throws TimeoutException {
    return getPipelinesSync(pollingIntervalMs);
  }

  @Nonnull
  public List<Pipeline> getPipelinesSync(long timeoutMillis) throws TimeoutException {
    // block until pipelines != null
    long start = System.currentTimeMillis();
    while (pipelines == null) {
      try {
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > timeoutMillis) {
          throw new TimeoutException("Pipeline configs are still not available after " + elapsed + "ms");
        }

        log.trace("Waiting for initial load of pipeline configs (elapsed={}ms)...", elapsed);
        Thread.sleep(pollingSleepMs);
      } catch (InterruptedException e) {
        // ignore
      }
    }

    return pipelines;
  }

  // visible for testing
  public static List<Pipeline> decorateTriggers(final List<Pipeline> pipelines) {
    return pipelines
      .stream()
      .map(p -> {
        List<Trigger> triggers = p.getTriggers();
        if (triggers == null || triggers.size() == 0) {
          return p;
        }

        List<Trigger> newTriggers = new ArrayList<>(triggers.size());
        Pipeline newPipe = p.withTriggers(newTriggers);

        for (Trigger oldTrigger: triggers) {
          Trigger newTrigger = oldTrigger.withParent(newPipe);
          newTrigger = newTrigger.withId(newTrigger.generateFallbackId());
          newTriggers.add(newTrigger);
        }

        return newPipe;
      })
      .collect(Collectors.toList());
  }

  @Override
  public String toString() {
    return "PipelineCache{" +
      "pollingIntervalMs=" + pollingIntervalMs +
      ", running=" + running +
      ", lastPollTimestamp=" + lastPollTimestamp +
      ", pipelines=" + (pipelines == null ? "null" : "<" + pipelines.size() + " pipelines>") +
      '}';
  }
}
