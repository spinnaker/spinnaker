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
import com.netflix.spinnaker.front50.exception.NotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.Long;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;


public abstract class BucketDAO<T extends Timestamped> {
  public static long HEALTH_MILLIS = 45000;
  private final Logger log = LoggerFactory.getLogger(getClass());
  protected final AtomicReference<Set<T>> allItemsCache = new AtomicReference<>();

  private final StorageService service;
  private final String daoTypeName;
  private final String rootFolder;
  private final Class<T> serializedClass;
  private final Scheduler scheduler;
  private final int refreshIntervalMs;

  private long lastRefreshedTime;

  public BucketDAO(Class<T> serializedClass, String daoTypeName, String basePath,
                   StorageService service,
                   Scheduler scheduler, int refreshIntervalMs) {
    this.serializedClass = serializedClass;
    this.daoTypeName = daoTypeName;
    this.service = service;
    this.rootFolder = basePath + '/' + daoTypeName + '/';
    this.scheduler = scheduler;
    this.refreshIntervalMs = refreshIntervalMs;
  }

  @PostConstruct
  void startRefresh() {
    // TODO(ewiseblatt): 20160526
    // Make this start executing now but in another thread.
    // I dont know how to say that using this API so am
    // using a timer instead.
    Observable
        .timer(0, TimeUnit.MILLISECONDS, scheduler)
        .subscribe(interval -> {
          try {
            log.info("Warming Cache");
            refresh();
          } catch (Exception e) {
            log.error("Unable to refresh: {}", e);
          }
        });
    Observable
        .timer(refreshIntervalMs, TimeUnit.MILLISECONDS, scheduler)
        .repeat()
        .subscribe(interval -> {
          try {
            log.debug("Refreshing");
            refresh();
          } catch (Exception e) {
            log.error("Unable to refresh: {}", e);
          }
        });
  }

  public Collection<T> all() {
    long lastModified = readLastModified();
    if (lastModified > lastRefreshedTime || allItemsCache.get() == null) {
        // only refresh if there was a modification since our last refresh cycle
        log.debug("all() forcing refresh");
        refresh();
    }

    return allItemsCache.get().stream().collect(Collectors.toList());
  }

  /**
   * @return Healthy if refreshed in the past HEALTH_MILLIS
   */
  public boolean isHealthy() {
    return (System.currentTimeMillis() - lastRefreshedTime) < HEALTH_MILLIS
            && allItemsCache.get() != null;
  }


  public T findById(String id) throws NotFoundException {
      return service.loadCurrentObject(buildObjectKey(id), daoTypeName,
                                       serializedClass);
  }

  public Collection<T> allVersionsOf(String id, int limit)
      throws NotFoundException {
    return service.listObjectVersions(buildObjectKey(id), daoTypeName, 
                                      serializedClass, limit);
  }

  public void update(String id, T item) {
    service.storeObject(buildObjectKey(id), daoTypeName, item);
  }

  public void delete(String id) {
    service.deleteObject(buildObjectKey(id), daoTypeName);
  }

  public void bulkImport(Collection<T> items) {
    Observable
        .from(items)
        .buffer(10)
        .flatMap(itemSet -> Observable
            .from(itemSet)
            .flatMap(item -> {
                    service.loadCurrentObject(buildObjectKey(item.getId()),
                                              daoTypeName, serializedClass);
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
    allItemsCache.set(fetchAllItems(allItemsCache.get()));
  }

  /**
   * Fetch any previously cached applications that have been updated since last retrieved.
   *
   * @param existingItems Previously cached applications
   * @return Refreshed applications
   */
  protected Set<T> fetchAllItems(Set<T> existingItems) {
    if (existingItems == null) {
      existingItems = new HashSet<>();
    }
    int existing_size = existingItems.size();

    Map<String, String> keyToId = new HashMap<String, String>();
    for (T item : existingItems) {
      String id = item.getId();
      keyToId.put(buildObjectKey(id), id);
    }

    Long refreshTime = System.currentTimeMillis();
    Map<String, Long> keyUpdateTime = service.listObjectKeys(daoTypeName);
  
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
            return true;
          }
          Long modTime = existingItem.getLastModified();
          return modTime == null || entry.getValue() > modTime;
         })
        .collect(Collectors.toList());

    Observable
        .from(modifiedKeys)
        .buffer(10)
        .flatMap(ids -> Observable
            .from(ids)
            .flatMap(entry -> {
                  try {
                      return Observable.just(service.loadCurrentObject(
                                                      entry.getKey(), daoTypeName,
                                                      serializedClass));
                  } catch (NotFoundException e) {
                    resultMap.remove(keyToId.get(entry.getKey()));
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
    this.lastRefreshedTime = refreshTime;

    int result_size = result.size();
    if (existing_size != result_size) {
      log.info("#{}={} delta={}",
               daoTypeName, result_size, result_size - existing_size);
    }

    return result;
  }

  protected String buildObjectKey(T item) {
    return buildObjectKey(item.getId());
  }

  protected String buildObjectKey(String id) {
    return id.toLowerCase();
  }

  private Long readLastModified() {
    return service.getLastModified(this.daoTypeName);
  }
}

