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

import static java.time.Instant.now;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService;
import com.netflix.spinnaker.echo.services.Front50Service;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PipelineCache implements MonitoredPoller {
  private final int pollingIntervalMs;
  private final int pollingSleepMs;
  private final Front50Service front50;
  private final OrcaService orca;
  private final Registry registry;
  private final ScheduledExecutorService executorService;
  private final ObjectMapper objectMapper;

  private volatile Boolean running;
  private volatile Instant lastPollTimestamp;

  @Nullable
  private volatile List<Pipeline> pipelines;

  @Nullable
  private volatile Map<String, List<Trigger>> triggersByType;

  @Autowired
  public PipelineCache(@Value("${front50.polling-interval-ms:10000}") int pollingIntervalMs,
                       @Value("${front50.polling-sleep-ms:100}") int pollingSleepMs,
                       ObjectMapper objectMapper,
                       @NonNull Front50Service front50,
                       @NonNull OrcaService orca,
                       @NonNull Registry registry) {
    this(Executors.newSingleThreadScheduledExecutor(), pollingIntervalMs, pollingSleepMs, objectMapper, front50, orca, registry);
  }

  // VisibleForTesting
  public PipelineCache(ScheduledExecutorService executorService,
                       int pollingIntervalMs,
                       int pollingSleepMs,
                       ObjectMapper objectMapper,
                       @NonNull Front50Service front50,
                       @NonNull OrcaService orca,
                       @NonNull Registry registry) {
    this.objectMapper = objectMapper;
    this.executorService = executorService;
    this.pollingIntervalMs = pollingIntervalMs;
    this.pollingSleepMs = pollingSleepMs;
    this.front50 = front50;
    this.orca = orca;
    this.registry = registry;
    this.running = false;
    this.pipelines = null;
    this.triggersByType = null;
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
      pipelines = fetchHydratedPipelines();

      // refresh the triggers view every time we fetch the latest pipelines
      triggersByType = extractEnabledTriggersFrom(pipelines);

      lastPollTimestamp = now();
      registry.counter("front50.requests").increment();
      log.debug("Fetched {} pipeline configs in {}ms", pipelines.size(), System.currentTimeMillis() - start);
    } catch (Exception e) {
      log.error("Error fetching pipelines from Front50", e);
      registry.counter("front50.errors").increment();
    }
  }

  private Map<String, Object> hydrate(Map<String, Object> rawPipeline) {
    Predicate<Map<String, Object>> isV2Pipeline = p -> {
      return p.getOrDefault("type", "").equals("templatedPipeline") &&
        p.getOrDefault("schema", "").equals("v2");
    };

    return planPipelineIfNeeded(rawPipeline, isV2Pipeline);
  }

  // converts a raw pipeline config from front50 into a processed Pipeline object
  private Optional<Pipeline> process(Map<String, Object> rawPipeline) {
    return Stream.of(rawPipeline)
      .map(this::hydrate)
      .filter(m -> !m.isEmpty())
      .map(this::convertToPipeline)
      .filter(Objects::nonNull)
      .map(PipelineCache::decorateTriggers)
      .findFirst();
  }

  private List<Map<String, Object>> fetchRawPipelines() {
    List<Map<String, Object>> rawPipelines = front50.getPipelines();
    return (rawPipelines == null) ? Collections.emptyList() : rawPipelines;
  }

  private List<Pipeline> fetchHydratedPipelines() {
    List<Map<String, Object>> rawPipelines = fetchRawPipelines();

    return rawPipelines.stream()
      .map(this::process)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }

  @Nonnull
  public Map<String, List<Trigger>> getEnabledTriggersSync() throws TimeoutException {
    List<Pipeline> pipelines = getPipelinesSync();

    // When getPipelinesSync returns, this means that we have populated the pipeline cache.
    // At this point, we don't expect triggers to be null but we check anyway to avoid a
    // potential race condition.
    return Optional.ofNullable(triggersByType)
      .orElse(extractEnabledTriggersFrom(pipelines));
  }

  private static Map<String, List<Trigger>> extractEnabledTriggersFrom(List<Pipeline> pipelines) {
    return pipelines.stream()
      .filter(p -> !p.isDisabled())
      .flatMap(p -> Optional.ofNullable(p.getTriggers()).orElse(Collections.emptyList()).stream())
      .filter(Trigger::isEnabled)
      .filter(t -> t.getType() != null)
      .collect(Collectors.groupingBy(Trigger::getType));
  }

  // looks up the latest version in front50 of a (potentially stale) pipeline config from the cache
  // if anything fails during the refresh, we fall back to returning the cached version
  public Pipeline refresh(Pipeline cached) {
    try {
      List<Map<String, Object>> latestVersion = front50.getLatestVersion(cached.getId());
      if (latestVersion.isEmpty()) {
        // if that corresponds to the case where the pipeline has been deleted, maybe we should not return the
        // cached version
        log.warn("Got empty results back from front50's /pipelines/{}/history?limit=1, falling back to cached={}",
          cached.getId(), cached);
        return cached;
      }

      Optional<Pipeline> processed = process(latestVersion.get(0));
      if (!processed.isPresent()) {
        log.warn("Failed to process raw pipeline, falling back to cached={}\n  latestVersion={}", cached, latestVersion);
        return cached;
      }

      // at this point, we are not updating the cache but just providing a fresh view
      return processed.get();
    } catch(Exception e) {
      log.error("Exception during pipeline refresh, falling back to cached={}", cached, e);
      return cached;
    }
  }

  /**
   * If the pipeline is a v2 pipeline, plan that pipeline.
   * Returns an empty map if the plan fails, so that the pipeline is skipped.
   */
  private Map<String, Object> planPipelineIfNeeded(Map<String, Object> pipeline, Predicate<Map<String, Object>> isV2Pipeline) {
    if (isV2Pipeline.test(pipeline)) {
      try {
        return orca.v2Plan(pipeline);
      } catch (Exception e) {
        // Don't fail the entire cache cycle if we fail a plan.
        log.error("Caught exception while planning templated pipeline: {}", pipeline, e);
        return Collections.emptyMap();
      }
    } else {
      return pipeline;
    }
  }

  /**
   * Converts map to pipeline.
   * Returns null if conversion fails so that the pipeline is skipped.
   */
  private Pipeline convertToPipeline(Map<String, Object> pipeline) {
    try {
      return objectMapper.convertValue(pipeline, Pipeline.class);
    } catch (Exception e) {
      log.warn("Pipeline failed to be converted to Pipeline.class: {}", pipeline, e);
      return null;
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

  public static Pipeline decorateTriggers(Pipeline pipeline) {
    List<Trigger> triggers = pipeline.getTriggers();
    if (triggers == null || triggers.isEmpty()) {
      return pipeline;
    }

    List<Trigger> newTriggers = new ArrayList<>(triggers.size());
    Pipeline newPipe = pipeline.withTriggers(newTriggers);

    for (Trigger oldTrigger: triggers) {
      Trigger newTrigger = oldTrigger.withParent(newPipe);
      newTrigger = newTrigger.withId(newTrigger.generateFallbackId());
      newTriggers.add(newTrigger);
    }

    return newPipe;
  }

  // visible for testing
  public static List<Pipeline> decorateTriggers(final List<Pipeline> pipelines) {
    return pipelines
      .stream()
      .map(PipelineCache::decorateTriggers)
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
