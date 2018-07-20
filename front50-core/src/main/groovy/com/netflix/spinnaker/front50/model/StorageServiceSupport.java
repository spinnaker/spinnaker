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

import com.google.common.collect.Lists;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.support.ClosureHelper;
import com.netflix.spinnaker.hystrix.SimpleHystrixCommand;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import static net.logstash.logback.argument.StructuredArguments.value;

public abstract class StorageServiceSupport<T extends Timestamped> {
  private static final long HEALTH_MILLIS = TimeUnit.SECONDS.toMillis(90);
  private final Logger log = LoggerFactory.getLogger(getClass());
  protected final AtomicReference<Set<T>> allItemsCache = new AtomicReference<>();

  private final ObjectType objectType;
  private final StorageService service;
  private final Scheduler scheduler;
  private final ObjectKeyLoader objectKeyLoader;
  private final long refreshIntervalMs;
  private final boolean shouldWarmCache;
  private final Registry registry;
  private final Timer cacheRefreshTimer;      // All refreshes
  private final Timer autoRefreshTimer;       // Only spontaneous refreshes in all()
  private final Timer scheduledRefreshTimer;  // Only refreshes from scheduler
  private final Counter addCounter;      // Newly discovered files during refresh
  private final Counter removeCounter;   // Deletes discovered during refresh
  private final Counter updateCounter;   // Updates discovered during refresh

  private final AtomicLong lastRefreshedTime = new AtomicLong();
  private final AtomicLong lastSeenStorageTime = new AtomicLong();

  public StorageServiceSupport(ObjectType objectType,
                               StorageService service,
                               Scheduler scheduler,
                               ObjectKeyLoader objectKeyLoader,
                               long refreshIntervalMs,
                               boolean shouldWarmCache,
                               Registry registry) {
    this.objectType = objectType;
    this.service = service;
    this.scheduler = scheduler;
    this.objectKeyLoader = objectKeyLoader;
    this.refreshIntervalMs = refreshIntervalMs;
    if (refreshIntervalMs >= getHealthMillis()) {
      throw new IllegalArgumentException("Cache refresh time must be more frequent than cache health timeout");
    }
    this.shouldWarmCache = shouldWarmCache;
    this.registry = registry;

    String typeName = objectType.name();
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
          Set itemCache = allItemsCache.get();
          return itemCache != null ? itemCache.size() : 0;
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
      if (shouldWarmCache) {
        try {
          log.info("Warming Cache");
          refresh();
        } catch (Exception e) {
          log.error("Unable to warm cache: {}", e);
        }
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
    return all(true);
  }

  public Collection<T> all(boolean refresh) {
    if (!refresh) {
      return new ArrayList<>(allItemsCache.get());
    }

    long lastModified = readLastModified();
    if (lastModified > lastSeenStorageTime.get() || allItemsCache.get() == null) {
      // only refresh if there was a modification since our last refresh cycle
      log.debug("all() forcing refresh (lastModified: {}, lastRefreshed: {}, lastSeenStorageTime: {})",
        value("lastModified", new Date(lastModified)),
        value("lastRefreshed", new Date(lastRefreshedTime.get())),
        value("lastSeenStorageTime", new Date(lastSeenStorageTime.get())));
      long startTime = System.nanoTime();
      refresh();
      long elapsed = System.nanoTime() - startTime;
      autoRefreshTimer.record(elapsed, TimeUnit.NANOSECONDS);
    }

    return new ArrayList<>(allItemsCache.get());
  }

  public Collection<T> history(String id, int maxResults) {
    if (service.supportsVersioning()) {
      return service.listObjectVersions(objectType, id, maxResults);
    } else {
      return Lists.newArrayList(findById(id));
    }
  }

  /**
   * @return Healthy if refreshed in the past `getHealthMillis()`
   */
  public boolean isHealthy() {
    return (System.currentTimeMillis() - lastRefreshedTime.get()) < getHealthMillis() && allItemsCache.get() != null;
  }

  public long getHealthIntervalMillis() {
    return service.getHealthIntervalMillis();
  }

  public T findById(String id) throws NotFoundException {
    try {
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
    } catch (HystrixRuntimeException e) {
      // This handles the case where the hystrix command times out.
      if (e.getFallbackException() instanceof NotFoundException) {
        throw (NotFoundException)e.getFallbackException();
      } else {
        throw e;
      }
    }
  }

  public void update(String id, T item) {
    service.storeObject(objectType, buildObjectKey(id), item);
  }

  public void delete(String id) {
    service.deleteObject(objectType, buildObjectKey(id));
  }

  public void bulkImport(Collection<T> items) {
    User authenticatedUser = new User();
    authenticatedUser.setUsername(AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));

    Observable
        .from(items)
        .buffer(10)
        .flatMap(itemSet -> Observable
            .from(itemSet)
            .flatMap(item -> {
              try {
                return AuthenticatedRequest.propagate(() -> {
                  update(item.getId(), item);
                  return Observable.just(item);
                }, true, authenticatedUser).call();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            })
            .subscribeOn(scheduler)
        ).subscribeOn(scheduler)
        .toList()
        .toBlocking()
        .single();
  }

  public void bulkDelete(Collection<String> ids) {
    service.bulkDeleteObjects(objectType, ids);
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
    Long storageLastModified = readLastModified();
    Map<String, Long> keyUpdateTime = objectKeyLoader.listObjectKeys(objectType);

    // Expanded from a stream collector to avoid DuplicateKeyExceptions
    Map<String, T> resultMap = new HashMap<>();
    for (T item : existingItems) {
      if (keyUpdateTime.containsKey(buildObjectKey(item))) {
        if (resultMap.containsKey(item.getId())) {
          log.error("Duplicate item id found, last-write wins: (id: {})", value("id", item.getId()));
        }
        resultMap.put(item.getId(), item);
      }
    }

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

    if (!existingItems.isEmpty() && !modifiedKeys.isEmpty()) {
      // only log keys that have been modified after initial cache load
      log.debug("Modified object keys: {}", value("keys", modifiedKeys));
    }

    Observable
        .from(modifiedKeys)
        .buffer(10)
        .flatMap(ids -> Observable
            .from(ids)
            .flatMap(entry -> {
                  try {
                    T object = (T) service.loadObject(objectType, entry.getKey());

                    Long expectedLastModifiedTime = keyUpdateTime.get(entry.getKey());
                    Long currentLastModifiedTime = object.getLastModified();

                    if (expectedLastModifiedTime != null && currentLastModifiedTime != null) {
                      if (currentLastModifiedTime < expectedLastModifiedTime) {
                        log.warn(
                          "Unexpected stale read for {} (current: {}, expected: {})",
                          entry.getKey(),
                          new Date(currentLastModifiedTime),
                          new Date(expectedLastModifiedTime)
                        );
                      }
                    }

                    return Observable.just(object);
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
    this.lastSeenStorageTime.set(storageLastModified);

    int resultSize = result.size();
    addCounter.increment(numAdded.get());
    updateCounter.increment(numUpdated.get());
    removeCounter.increment(existingSize + numAdded.get() - resultSize);
    if (existingSize != resultSize) {
      log.info("{}={} delta={}",
        value("objectType", objectType.group),
        value("resultSize", resultSize),
        value("delta", resultSize - existingSize));
    }

    return result;
  }

  private Long readLastModified() {
    return service.getLastModified(objectType);
  }

  protected long getHealthMillis() {
    return HEALTH_MILLIS;
  }
}
