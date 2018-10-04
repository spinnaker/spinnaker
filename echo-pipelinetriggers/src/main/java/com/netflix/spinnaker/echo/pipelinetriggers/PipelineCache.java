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
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.services.Front50Service;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.subjects.ReplaySubject;
import rx.subjects.SerializedSubject;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.time.Instant.now;
import static java.util.concurrent.TimeUnit.SECONDS;

@Component
@Slf4j
public class PipelineCache implements MonitoredPoller {
  private final Scheduler scheduler;
  private final int pollingIntervalSeconds;
  private final Front50Service front50;
  private final Registry registry;

  private transient Instant lastPollTimestamp;
  private transient Subscription subscription;

  private transient SerializedSubject<List<Pipeline>, List<Pipeline>> pipelineSubject = ReplaySubject.<List<Pipeline>>createWithSize(1).toSerialized();

  @Autowired
  public PipelineCache(@NonNull Scheduler scheduler,
                       int pollingIntervalSeconds,
                       @NonNull Front50Service front50,
                       @NonNull Registry registry) {
    this.scheduler = scheduler;
    this.pollingIntervalSeconds = pollingIntervalSeconds;
    this.front50 = front50;
    this.registry = registry;
  }

  @PostConstruct
  public void start() {
    if (subscription == null || subscription.isUnsubscribed()) {
      subscription = Observable.interval(pollingIntervalSeconds, SECONDS, scheduler)
        .doOnNext(this::onFront50Request)
        .flatMap(tick -> front50.getPipelines())
        .doOnError(this::onFront50Error)
        .retry()
        .map(PipelineCache::decorateTriggers)
        .doOnNext(this::logRefresh)
        .subscribe(pipelineSubject);
    }
  }

  @PreDestroy
  public void stop() {
    if (subscription != null) {
      subscription.unsubscribe();
    }
  }

  @Override
  public boolean isRunning() {
    return subscription != null && !subscription.isUnsubscribed();
  }

  @Override
  public Instant getLastPollTimestamp() {
    return lastPollTimestamp;
  }

  @Override
  public int getPollingIntervalSeconds() {
    return pollingIntervalSeconds;
  }

  /**
   * Returns an observable that emits the configured pipelines as of the most recent polling cycle.
   * If no polling cycles have been completed, the observable will wait until the first cycle
   * completes and then emit the pipelines from that polling cycle.
   *
   * @return An observable emitting the pipelines as of the most recent polling cycle
   */
  public Observable<List<Pipeline>> getPipelines() {
    return pipelineSubject.take(1);
  }

  public List<Pipeline> getPipelinesSync() {
    return getPipelines().toBlocking().first();
  }

  private void logRefresh(final List<Pipeline> pipelines) {
    log.info("Refreshing pipelines");
  }

  private void onFront50Request(final long tick) {
    log.debug("Getting pipelines from Front50...");
    lastPollTimestamp = now();
    registry.counter("front50.requests").increment();
  }

  private void onFront50Error(Throwable e) {
    log.error("Error fetching pipelines from Front50: {}", e.getMessage());
    registry.counter("front50.errors").increment();
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
}
