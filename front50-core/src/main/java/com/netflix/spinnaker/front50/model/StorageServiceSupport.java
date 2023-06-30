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

import static net.logstash.logback.argument.StructuredArguments.value;

import com.google.common.collect.Lists;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spinnaker.front50.api.model.Timestamped;
import com.netflix.spinnaker.front50.config.StorageServiceConfigurationProperties;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.User;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.SupplierUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;

public abstract class StorageServiceSupport<T extends Timestamped> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  protected final AtomicReference<Set<T>> allItemsCache = new AtomicReference<>();

  private final ObjectType objectType;
  private final StorageService service;
  private final Scheduler scheduler;
  private final ObjectKeyLoader objectKeyLoader;
  private final Registry registry;
  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private StorageServiceConfigurationProperties.PerObjectType configProperties;

  private final Timer autoRefreshTimer; // Only spontaneous refreshes in all()
  private final Timer scheduledRefreshTimer; // Only refreshes from scheduler
  private final Counter addCounter; // Newly discovered files during refresh
  private final Counter removeCounter; // Deletes discovered during refresh
  private final Counter updateCounter; // Updates discovered during refresh
  private final Counter mismatchedIdCounter; // Items whose id does not match its cache key

  private final AtomicLong lastRefreshedTime = new AtomicLong();
  private final AtomicLong lastSeenStorageTime = new AtomicLong();

  AtomicReference<CountDownLatch> globalLatch = new AtomicReference<>(null);

  public StorageServiceSupport(
      ObjectType objectType,
      StorageService service,
      Scheduler scheduler,
      ObjectKeyLoader objectKeyLoader,
      StorageServiceConfigurationProperties.PerObjectType configurationProperties,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    this.objectType = objectType;
    this.service = service;
    this.scheduler = scheduler;
    this.objectKeyLoader = objectKeyLoader;
    this.configProperties = configurationProperties;
    this.registry = registry;
    this.circuitBreakerRegistry = circuitBreakerRegistry;

    String typeName = objectType.name();
    this.autoRefreshTimer =
        registry.timer(
            registry.createId("storageServiceSupport.autoRefreshTime", "objectType", typeName));
    this.scheduledRefreshTimer =
        registry.timer(
            registry.createId(
                "storageServiceSupport.scheduledRefreshTime", "objectType", typeName));
    this.addCounter =
        registry.counter(
            registry.createId("storageServiceSupport.numAdded", "objectType", typeName));
    this.removeCounter =
        registry.counter(
            registry.createId("storageServiceSupport.numRemoved", "objectType", typeName));
    this.updateCounter =
        registry.counter(
            registry.createId("storageServiceSupport.numUpdated", "objectType", typeName));
    this.mismatchedIdCounter =
        registry.counter(
            registry.createId("storageServiceSupport.mismatchedIds", "objectType", typeName));

    registry.gauge(
        registry.createId("storageServiceSupport.cacheSize", "objectType", typeName),
        this,
        new ToDoubleFunction() {
          @Override
          public double applyAsDouble(Object ignore) {
            Set<T> itemCache = allItemsCache.get();
            return itemCache != null ? itemCache.size() : 0;
          }
        });
    registry.gauge(
        registry.createId("storageServiceSupport.cacheAge", "objectType", typeName),
        lastRefreshedTime,
        (lrt) -> Long.valueOf(System.currentTimeMillis() - lrt.get()).doubleValue());

    if (configProperties.isOptimizeCacheRefreshes()) {
      if (!service.supportsVersioning()) {
        log.warn(
            "Optimized refresh is not available to un-versioned {} objects since they don't support soft deletes.",
            objectType);
        configProperties.setOptimizeCacheRefreshes(false);
      } else {
        log.info("Optimized refreshes are now enabled for versioned {} objects.", objectType);
      }
    }
  }

  @PostConstruct
  void startRefresh() {
    if (configProperties.getRefreshMs() >= getHealthMillis()) {
      throw new IllegalArgumentException(
          "Cache refresh time must be more frequent than cache health timeout");
    }

    if (configProperties.getRefreshMs() > 0) {
      if (configProperties.isShouldWarmCache()) {
        try {
          log.info("Warming Cache");
          refresh();
        } catch (Exception e) {
          log.error("Unable to warm cache: ", e);
        }
      }

      Observable.timer(configProperties.getRefreshMs(), TimeUnit.MILLISECONDS, scheduler)
          .repeat()
          .subscribe(
              interval -> {
                try {
                  long startTime = System.nanoTime();
                  refresh();
                  long elapsed = System.nanoTime() - startTime;
                  scheduledRefreshTimer.record(elapsed, TimeUnit.NANOSECONDS);
                } catch (Exception e) {
                  log.error("Unable to refresh: ", e);
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

    log.debug(
        "performing cache refresh with synchronization: {}",
        configProperties.isSynchronizeCacheRefresh());
    if (configProperties.isSynchronizeCacheRefresh()) {
      doSynchronizedRefresh();
    } else {
      doRefresh();
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

  /** @return Healthy if refreshed in the past `getHealthMillis()` */
  public boolean isHealthy() {
    boolean isHealthy =
        (System.currentTimeMillis() - lastRefreshedTime.get()) < getHealthMillis()
            && allItemsCache.get() != null;

    if (!isHealthy) {
      log.warn(
          "{} is unhealthy (lag: {}, populatedCache: {})",
          getClass().getSimpleName(),
          System.currentTimeMillis() - lastRefreshedTime.get(),
          allItemsCache.get() != null);
    }

    return isHealthy;
  }

  /**
   * How frequently to refresh health information (e.g. to call isHealthy() to provide info to the
   * health endpoint).
   *
   * <p>By itself, this is independent from ItemDAO.getHealthIntervalMillis, but when e.g.
   * DefaultPipelineDAO both extends this class, and implements ItemDAO (via PipelineDAO), this
   * method serves as the override to ItemDAO.getHealthIntervalMillis.
   *
   * @return the period from the underlying StorageService
   */
  public long getHealthIntervalMillis() {
    return service.getHealthIntervalMillis();
  }

  public T findById(String id) throws NotFoundException {
    CircuitBreaker breaker =
        circuitBreakerRegistry.circuitBreaker(
            getClass().getSimpleName() + "-findById",
            CircuitBreakerConfig.custom()
                .ignoreException(e -> e instanceof NotFoundException)
                .build());

    Supplier<T> recoverableSupplier =
        SupplierUtils.recover(
            () -> service.loadObject(objectType, buildObjectKey(id)),
            e ->
                Optional.ofNullable(allItemsCache.get()).orElseGet(HashSet::new).stream()
                    .filter(item -> item.getId().equalsIgnoreCase(id))
                    .findFirst()
                    .orElseThrow(
                        () ->
                            new NotFoundException(
                                String.format(
                                    "No item found in cache with id of %s",
                                    id == null ? "null" : id.toLowerCase()))));

    return breaker.executeSupplier(recoverableSupplier);
  }

  public void update(String id, T item) {
    item.setLastModifiedBy(AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));
    item.setLastModified(System.currentTimeMillis());
    service.storeObject(objectType, buildObjectKey(id), item);
  }

  public void delete(String id) {
    service.deleteObject(objectType, buildObjectKey(id));
  }

  public void bulkImport(Collection<T> items) {
    User authenticatedUser = new User();
    authenticatedUser.setUsername(AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));

    if (service instanceof BulkStorageService) {
      String lastModifiedBy = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous");
      Long lastModified = System.currentTimeMillis();

      items.forEach(
          item -> {
            item.setLastModifiedBy(lastModifiedBy);
            item.setLastModified(lastModified);
          });

      ((BulkStorageService) service).storeObjects(objectType, items);
      return;
    }

    Observable.from(items)
        .buffer(10)
        .flatMap(
            itemSet ->
                Observable.from(itemSet)
                    .flatMap(
                        item -> {
                          try {
                            return AuthenticatedRequest.propagate(
                                    () -> {
                                      update(item.getId(), item);
                                      return Observable.just(item);
                                    },
                                    true,
                                    authenticatedUser)
                                .call();
                          } catch (Exception e) {
                            throw new RuntimeException(e);
                          }
                        })
                    .subscribeOn(scheduler))
        .subscribeOn(scheduler)
        .toList()
        .toBlocking()
        .single();
  }

  public void bulkDelete(Collection<String> ids) {
    service.bulkDeleteObjects(objectType, ids);
  }

  /** Update local cache with any recently modified items. */
  protected void refresh() {
    long startTime = System.nanoTime();
    if (configProperties.isOptimizeCacheRefreshes()) {
      log.debug("Running optimized cache refresh");
      allItemsCache.set(fetchAllItemsOptimized(allItemsCache.get()));
    } else {
      log.debug("Running unoptimized cache refresh");
      allItemsCache.set(fetchAllItems(allItemsCache.get()));
    }
    long elapsed = System.nanoTime() - startTime;
    registry
        .timer("storageServiceSupport.cacheRefreshTime", "objectType", objectType.name())
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

    Map<String, String> keyToId = new HashMap<>();
    for (T item : existingItems) {
      String id = item.getId();
      keyToId.put(buildObjectKey(id), id);
    }

    long refreshTime = System.currentTimeMillis();
    long storageLastModified = readLastModified();
    Map<String, Long> keyUpdateTime = objectKeyLoader.listObjectKeys(objectType);

    // Expanded from a stream collector to avoid DuplicateKeyExceptions
    Map<String, T> resultMap = new HashMap<>();
    for (T item : existingItems) {
      if (keyUpdateTime.containsKey(buildObjectKey(item))) {
        String itemId = buildObjectKey(item.getId());
        if (resultMap.containsKey(itemId)) {
          log.error("Duplicate item id found, last-write wins: (id: {})", value("id", itemId));
        }
        resultMap.put(itemId, item);
      }
    }

    List<Map.Entry<String, Long>> modifiedKeys =
        keyUpdateTime.entrySet().stream()
            .filter(
                entry -> {
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

    try {
      List<String> objectKeys =
          modifiedKeys.stream().map(Map.Entry::getKey).collect(Collectors.toList());
      List<T> objects = service.loadObjects(objectType, objectKeys);

      Map<String, T> objectsById =
          objects.stream()
              .collect(Collectors.toMap(this::buildObjectKey, Function.identity(), (o1, o2) -> o1));

      for (String objectKey : objectKeys) {
        if (objectsById.containsKey(objectKey)) {
          resultMap.put(objectKey, objectsById.get(objectKey));
        } else {
          // equivalent to the NotFoundException handling in the exceptional case below
          resultMap.remove(keyToId.get(objectKey));
          numRemoved.getAndIncrement();

          log.warn("Unable to find result for {}:{} (filtering!)", objectType, objectKey);
        }
      }
    } catch (UnsupportedOperationException e) {
      Observable.from(modifiedKeys)
          .buffer(10)
          .flatMap(
              ids ->
                  Observable.from(ids)
                      .flatMap(
                          entry -> {
                            try {
                              String key = entry.getKey();
                              T object = (T) service.loadObject(objectType, key);

                              if (!key.equals(buildObjectKey(object))) {
                                mismatchedIdCounter.increment();
                                log.warn(
                                    "{} '{}' has non-matching id '{}'",
                                    objectType.group,
                                    key,
                                    buildObjectKey(object));
                                // Should return Observable.empty() to skip caching, but will wait
                                // until the
                                // logging has been present for a release.
                              }

                              return Observable.just(object);
                            } catch (NotFoundException e2) {
                              resultMap.remove(keyToId.get(entry.getKey()));
                              numRemoved.getAndIncrement();
                              return Observable.empty();
                            }
                          })
                      .subscribeOn(scheduler))
          .subscribeOn(scheduler)
          .toList()
          .toBlocking()
          .single()
          .forEach(item -> resultMap.put(buildObjectKey(item), item));
    }

    Set<T> result = new HashSet<>(resultMap.values());
    this.lastRefreshedTime.set(refreshTime);
    this.lastSeenStorageTime.set(storageLastModified);

    int resultSize = result.size();
    addCounter.increment(numAdded.get());
    updateCounter.increment(numUpdated.get());
    removeCounter.increment(existingSize + numAdded.get() - resultSize);
    if (numAdded.get() > 0 || numUpdated.get() > 0 || numRemoved.get() > 0) {
      log.info(
          "Fetched {} {} objects after adding {} objects, updating {} objects and removing {} objects with a delta of {}.",
          value("resultSize", resultSize),
          value("objectType", objectType.group),
          value("numAdded", numAdded.get()),
          value("numUpdated", numUpdated.get()),
          value("numRemoved", numRemoved.get()),
          value("delta", resultSize - existingSize));
    }

    return result;
  }

  private Set<T> fetchAllItemsOptimized(Set<T> existingItems) {
    if (existingItems == null) {
      existingItems = new HashSet<>();
    }
    int existingSize = existingItems.size();
    AtomicLong numAdded = new AtomicLong();
    AtomicLong numRemoved = new AtomicLong();
    AtomicLong numUpdated = new AtomicLong();

    long refreshTime = System.currentTimeMillis();
    long storageLastModified = readLastModified();

    // Get lists of modified and deleted objects from the store
    Map<String, List<T>> newerItems =
        service.loadObjectsNewerThan(objectType, lastSeenStorageTime.get());
    Map<String, T> modifiedItems =
        newerItems.get("not_deleted").stream()
            .collect(Collectors.toMap(Timestamped::getId, item -> item));
    Map<String, T> deletedItems =
        newerItems.get("deleted").stream()
            .collect(Collectors.toMap(Timestamped::getId, item -> item));

    // Expanded from a stream collector to avoid DuplicateKeyExceptions
    Map<String, T> resultMap = new HashMap<>();
    existingItems.forEach(
        existingItem -> {
          String existingItemId = buildObjectKey(existingItem.getId());
          if (deletedItems.containsKey(existingItemId)) {
            // item was deleted, skip it
            log.debug(
                "Item with id {} deleted from the store. Will not add to the result.",
                existingItemId);
            numRemoved.getAndIncrement();
          } else if (!modifiedItems.containsKey(existingItemId)) {
            // item was unchanged, add it back as is
            resultMap.put(existingItemId, existingItem);
          }
        });

    // add all modified items to the result map
    modifiedItems.forEach(
        (id, item) -> {
          if (resultMap.containsKey(id)) {
            log.debug(
                "Item with id {} was modified in the store. Will update it in the result.", id);
            numUpdated.getAndIncrement();
          } else {
            log.debug("Item with id {} was added to the store. Will add it in the result.", id);
            numAdded.getAndIncrement();
          }
          resultMap.put(id, item);
        });

    if (!existingItems.isEmpty() && !modifiedItems.isEmpty()) {
      // only log keys that have been modified/deleted after initial cache load
      log.debug("Modified object keys: {}", value("keys", modifiedItems.keySet()));
      log.debug("Deleted object keys: {}", value("keys", deletedItems.keySet()));
    }

    Set<T> result = new HashSet<>(resultMap.values());
    this.lastRefreshedTime.set(refreshTime);
    this.lastSeenStorageTime.set(storageLastModified);

    int resultSize = result.size();
    addCounter.increment(numAdded.get());
    updateCounter.increment(numUpdated.get());
    removeCounter.increment(numRemoved.get());
    if (numAdded.get() > 0 || numUpdated.get() > 0 || numRemoved.get() > 0) {
      log.info(
          "Fetched {} {} objects after adding {} objects, updating {} objects and removing {} objects with a delta of {}.",
          value("resultSize", resultSize),
          value("objectType", objectType.group),
          value("numAdded", numAdded.get()),
          value("numUpdated", numUpdated.get()),
          value("numRemoved", numRemoved.get()),
          value("delta", resultSize - existingSize));
    }
    return result;
  }

  private Long readLastModified() {
    return service.getLastModified(objectType);
  }

  protected long getHealthMillis() {
    return TimeUnit.SECONDS.toMillis(configProperties.getCacheHealthCheckTimeoutSeconds());
  }

  /*
   only allow those threads to refresh the cache for whom the db's lastRefreshTime precedes the time at which
   it attempted to do a refresh.
   We have seen that when multiple threads attempt to refresh at the same time, primarily due to writes to the db, it
   causes DB performance to degrade. At normal loads, we have seen around 12 refreshes per min on avg. This only gets
    worse during peak load time. This function limits which thread actually needs to do the cache refresh and which
    thread can exit with a no-op. It works by allowing multiple threads to attempt a refresh, but only allowing one
    thread at a time to obtain a CountDownLatch to actually perform the refresh. These other threads continuously check
    to see if the db's lastRefreshTime > the time at which it entered this function. If yes, that means some other
    thread has already refreshed the cache, so these threads exit with a no-op.
  */
  private void doSynchronizedRefresh() {
    // this is the timestamp at which a thread attempts to refresh
    long refreshAttemptTime = System.currentTimeMillis();
    log.debug(
        "Attempting to perform cache refresh at: {}",
        value("refreshAttemptTime", new Date(refreshAttemptTime)));

    while (true) {
      boolean isRefreshAllowed = false; // flag to control which thread can refresh the cache
      // since countdown latches can't be reset, we keep a pointer to a global latch
      // so that others can wait on it
      CountDownLatch localLatch;
      synchronized (this) {
        if (globalLatch.get() == null) {
          log.debug("Latch obtained. Attempting to refresh cache");
          isRefreshAllowed = true;
          globalLatch.set(new CountDownLatch(1));
        } else {
          log.debug("Waiting to obtain latch");
        }
        localLatch = globalLatch.get();
      }

      if (isRefreshAllowed) {
        try {
          doRefresh();
        } finally {
          synchronized (this) {
            localLatch.countDown(); // release all other threads waiting on this one
            globalLatch.set(null);
          }
        }
        break;
      } else {
        try {
          localLatch.await(); // all other threads will be waiting here
        } catch (Exception e) {
          log.warn("current thread was interrupted while waiting to obtain the latch", e);
        }
        // this thread doesn't need to refresh anymore, since some other thread already refreshed
        // the db while this was
        // waiting
        if (lastRefreshedTime.get() > refreshAttemptTime) {
          log.info(
              "Not refreshing the cache since the lastRefreshedTime: {} is later than this thread's "
                  + "refreshAttemptTime: {}",
              value("lastRefreshedTime", new Date(lastRefreshedTime.get())),
              value("refreshAttemptTime", new Date(refreshAttemptTime)));
          break;
        }
      }
    }
    log.debug("Synchronized refresh completed");
  }

  /** Refresh if the data store is empty, or has been modified since the last refresh */
  private void doRefresh() {
    long lastModified = readLastModified();
    if (lastModified > lastSeenStorageTime.get() || allItemsCache.get() == null) {
      // only refresh if there was a modification since our last refresh cycle
      log.debug(
          "all() forcing refresh (lastModified: {}, lastRefreshed: {}, lastSeenStorageTime: {})",
          value("lastModified", new Date(lastModified)),
          value("lastRefreshed", new Date(lastRefreshedTime.get())),
          value("lastSeenStorageTime", new Date(lastSeenStorageTime.get())));
      long startTime = System.nanoTime();
      refresh();
      long elapsed = System.nanoTime() - startTime;
      autoRefreshTimer.record(elapsed, TimeUnit.NANOSECONDS);
    } else {
      log.info("refresh not required");
    }
  }
}
