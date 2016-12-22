/*
 * Copyright 2016 Google, Inc.
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
package com.netflix.spinnaker.front50.model;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.support.ClosureHelper;
import com.netflix.spinnaker.hystrix.SimpleHystrixCommand;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;

import javax.annotation.PostConstruct;
import java.lang.Long;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;


public abstract class StorageServiceSupport<T extends Timestamped> {
  private static final long HEALTH_MILLIS = TimeUnit.SECONDS.toMillis(90);
  private final Logger log = LoggerFactory.getLogger(getClass());
  protected final AtomicReference<Set<T>> allItemsCache = new AtomicReference<>();

  private final ObjectType objectType;
  private final StorageService service;
  private final Scheduler scheduler;
  private final int refreshIntervalMs;
  private final Registry registry;
  private final Timer cacheRefreshTimer;      // All refreshes
  private final Timer autoRefreshTimer;       // Only spontaneous refreshes in all()
  private final Timer scheduledRefreshTimer;  // Only refreshes from scheduler
  private final Counter addCounter;      // Newly discovered files during refresh
  private final Counter removeCounter;   // Deletes discovered during refresh
  private final Counter updateCounter;   // Updates discovered during refresh

  private final AtomicLong lastRefreshedTime = new AtomicLong();

  public StorageServiceSupport(ObjectType objectType,
                               StorageService service,
                               Scheduler scheduler,
                               int refreshIntervalMs,
                               Registry registry) {
    this.objectType = objectType;
    this.service = service;
    this.scheduler = scheduler;
    this.refreshIntervalMs = refreshIntervalMs;
    if (refreshIntervalMs >= HEALTH_MILLIS) {
      throw new IllegalArgumentException("Cache refresh time must be more frequent than cache health timeout");
    }
    String typeName = objectType.name();
    this.registry = registry;

    this.cacheRefreshTimer = registry.timer(
      registry.createId("storageServiceSupport.cacheRefreshTime", "objectType", typeName));
    this.autoRefreshTimer = registry.timer(
      registry.createId("storageServiceSupport.autoRefreshTime", "objectType", typeName));
    this.scheduledRefreshTimer = registry.timer(
      registry.createId("storageServiceSupport.scheduledRefreshTime", "objectType", typeName));
    this.addCounter = registry.counter(
      registry.createId("storageServiceSupport.numAdded", "objectType", typeName));
    this.removeCounter = registry.counter(
      registry.createId("storageServiceSupport.numRemoved", "objectType", typeName));
    this.updateCounter = registry.counter(
      registry.createId("storageServiceSupport.numUpdated", "objectType", typeName));

    registry.gauge(
      registry.createId("storageServiceSupport.cacheSize", "objectType", typeName),
      this, new ToDoubleFunction() {
        @Override
        public double applyAsDouble(Object ignore) {
          return allItemsCache.get().size();
        }
    });
    registry.gauge(
      registry.createId("storageServiceSupport.cacheAge", "objectType", typeName),
      lastRefreshedTime,
      (lrt) -> Long.valueOf(System.currentTimeMillis() - lrt.get()).doubleValue());
  }

  @PostConstruct
  void startRefresh() {
    if (refreshIntervalMs > 0) {
      try {
        log.info("Warming Cache");
        refresh();
      } catch (Exception e) {
        log.error("Unable to warm cache: {}", e);
      }

      Observable
        .timer(refreshIntervalMs, TimeUnit.MILLISECONDS, scheduler)
        .repeat()
        .subscribe(interval -> {
          try {
            long startTime = System.nanoTime();
            refresh();
            long elapsed = System.nanoTime() - startTime;
            scheduledRefreshTimer.record(elapsed, TimeUnit.NANOSECONDS);
          } catch (Exception e) {
            log.error("Unable to refresh: {}", e);
          }
        });
    }
  }

  public Collection<T> all() {
    long lastModified = readLastModified();
    if (lastModified > lastRefreshedTime.get() || allItemsCache.get() == null) {
        // only refresh if there was a modification since our last refresh cycle
        log.debug("all() forcing refresh");
        long startTime = System.nanoTime();
        refresh();
        long elapsed = System.nanoTime() - startTime;
        autoRefreshTimer.record(elapsed, TimeUnit.NANOSECONDS);
    }

    return allItemsCache.get().stream().collect(Collectors.toList());
  }

  public Collection<T> all(String prefix, int maxResults) {
    return service.loadObjectsWithPrefix(objectType, prefix, maxResults);
  }

  public Collection<T> history(String id, int maxResults) {
    return service.listObjectVersions(objectType, id, maxResults);
  }

  /**
   * @return Healthy if refreshed in the past HEALTH_MILLIS
   */
  public boolean isHealthy() {
    return (System.currentTimeMillis() - lastRefreshedTime.get()) < HEALTH_MILLIS && allItemsCache.get() != null;
  }

  public T findById(String id) throws NotFoundException {
    return new SimpleHystrixCommand<T>(
        getClass().getSimpleName(),
        getClass().getSimpleName() + "-findById",
        ClosureHelper.toClosure(args -> service.loadObject(objectType, buildObjectKey(id))),
        ClosureHelper.toClosure(
            args -> allItemsCache.get().stream()
            .filter(item -> item.getId().equalsIgnoreCase(id))
            .findFirst()
            .orElseThrow(() -> new NotFoundException(
                String.format("No item found in cache with id of %s", id.toLowerCase()))))
    ).execute();
  }

  public void update(String id, T item) {
    service.storeObject(objectType, buildObjectKey(id), item);
  }

  public void delete(String id) {
    service.deleteObject(objectType, buildObjectKey(id));
  }

  public void bulkImport(Collection<T> items) {
    Observable
        .from(items)
        .buffer(10)
        .flatMap(itemSet -> Observable
            .from(itemSet)
            .flatMap(item -> {
              update(item.getId(), item);
              return Observable.just(item);
            })
            .subscribeOn(scheduler)
        ).subscribeOn(scheduler)
        .toList()
        .toBlocking()
        .single();
  }

  /**
   * Update local cache with any recently modified items.
   */
  protected void refresh() {
    long startTime = System.nanoTime();
    allItemsCache.set(fetchAllItems(allItemsCache.get()));
    long elapsed = System.nanoTime() - startTime;
    registry.timer("storageServiceSupport.cacheRefreshTime",
                   "objectType", objectType.name())
        .record(elapsed, TimeUnit.NANOSECONDS);

    log.debug("Refreshed (" + TimeUnit.NANOSECONDS.toMillis(elapsed) + "ms)");
  }

  private String buildObjectKey(T item) {
    return buildObjectKey(item.getId());
  }

  private String buildObjectKey(String id) {
    return id.toLowerCase();
  }

  /**
   * Fetch any previously cached applications that have been updated since last retrieved.
   *
   * @param existingItems Previously cached applications
   * @return Refreshed applications
   */
  private Set<T> fetchAllItems(Set<T> existingItems) {
    if (existingItems == null) {
      existingItems = new HashSet<>();
    }
    int existingSize = existingItems.size();
    AtomicLong numAdded = new AtomicLong();
    AtomicLong numRemoved = new AtomicLong();
    AtomicLong numUpdated = new AtomicLong();

    Map<String, String> keyToId = new HashMap<String, String>();
    for (T item : existingItems) {
      String id = item.getId();
      keyToId.put(buildObjectKey(id), id);
    }

    Long refreshTime = System.currentTimeMillis();
    Map<String, Long> keyUpdateTime = service.listObjectKeys(objectType);

    Map<String, T> resultMap = existingItems
        .stream()
        .filter(a -> keyUpdateTime.containsKey(buildObjectKey(a)))
        .collect(Collectors.toMap(Timestamped::getId, Function.identity()));

    List<Map.Entry<String, Long>> modifiedKeys = keyUpdateTime
        .entrySet()
        .stream()
        .filter(entry -> {
          T existingItem = resultMap.get(entry.getKey());
          if (existingItem == null) {
            numAdded.getAndIncrement();
            return true;
          }
          Long modTime = existingItem.getLastModified();
          if (modTime == null || entry.getValue() > modTime) {
            numUpdated.getAndIncrement();
            return true;
          }
          return false;
         })
        .collect(Collectors.toList());

    Observable
        .from(modifiedKeys)
        .buffer(10)
        .flatMap(ids -> Observable
            .from(ids)
            .flatMap(entry -> {
                  try {
                    return Observable.just((T) service.loadObject(objectType, entry.getKey()));
                  } catch (NotFoundException e) {
                    resultMap.remove(keyToId.get(entry.getKey()));
                    numRemoved.getAndIncrement();
                    return Observable.empty();
                  }
                }
            )
            .subscribeOn(scheduler)
        )
        .subscribeOn(scheduler)
        .toList()
        .toBlocking()
        .single()
        .forEach(item -> {
          resultMap.put(item.getId().toLowerCase(), item);
        });

    Set<T> result = resultMap.values().stream().collect(Collectors.toSet());
    this.lastRefreshedTime.set(refreshTime);

    int resultSize = result.size();
    addCounter.increment(numAdded.get());
    updateCounter.increment(numUpdated.get());
    removeCounter.increment(existingSize + numAdded.get() - resultSize);
    if (existingSize != resultSize) {
      log.info("{}={} delta={}",
        objectType.group, resultSize, resultSize - existingSize);
    }

    return result;
  }

  private Long readLastModified() {
    return service.getLastModified(objectType);
  }
}
