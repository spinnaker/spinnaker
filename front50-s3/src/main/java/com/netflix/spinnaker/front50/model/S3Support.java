/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.front50.model;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class S3Support<T extends Timestamped> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final ObjectMapper objectMapper;
  private final AmazonS3 amazonS3;
  private final Scheduler scheduler;
  private final int refreshIntervalMs;
  private final String bucket;

  private long lastRefreshedTime;

  protected final String rootFolder;

  protected final AtomicReference<Set<T>> allItemsCache = new AtomicReference<>();

  public S3Support(ObjectMapper objectMapper,
                   AmazonS3 amazonS3,
                   Scheduler scheduler,
                   int refreshIntervalMs,
                   String bucket,
                   String rootFolder) {
    this.objectMapper = objectMapper;
    this.amazonS3 = amazonS3;
    this.scheduler = scheduler;
    this.refreshIntervalMs = refreshIntervalMs;
    this.bucket = bucket;
    this.rootFolder = rootFolder;
  }

  @PostConstruct
  void startRefresh() {
    Observable
        .timer(refreshIntervalMs, TimeUnit.MILLISECONDS, scheduler)
        .repeat()
        .subscribe(interval -> {
          try {
            log.info("Refreshing");
            refresh();
            log.info("Refreshed");
          } catch (Exception e) {
            log.error("Unable to refresh", e);
          }
        });
  }

  public Collection<T> all() {
    return allItemsCache.get().stream().collect(Collectors.toList());
  }

  /**
   * @return Healthy if refreshed in the past 45s
   */
  public boolean isHealthy() {
    return (System.currentTimeMillis() - lastRefreshedTime) < 45000 && allItemsCache.get() != null;
  }

  public T findById(String id) throws NotFoundException {
    try {
      S3Object s3Object = amazonS3.getObject(bucket, buildS3Key(id));
      T item = deserialize(s3Object);
      item.setLastModified(s3Object.getObjectMetadata().getLastModified().getTime());
      return item;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (AmazonS3Exception e) {
      if (e.getStatusCode() == 404) {
        log.warn(String.format("No item found with id of %s", id.toLowerCase()));
        throw new NotFoundException(String.format("No item found with id of %s", id.toLowerCase()));
      }

      throw e;
    }
  }

  public void update(String id, T item) {
    try {
      amazonS3.putObject(
          bucket,
          buildS3Key(id),
          new ByteArrayInputStream(objectMapper.writeValueAsBytes(item)),
          new ObjectMetadata()
      );
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public void delete(String id) {
    amazonS3.deleteObject(bucket, buildS3Key(id));
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

    ObjectListing bucketListing = amazonS3.listObjects(
        new ListObjectsRequest(bucket, rootFolder, null, null, 10000)
    );
    List<S3ObjectSummary> summaries = bucketListing.getObjectSummaries();

    while (bucketListing.isTruncated()) {
      bucketListing = amazonS3.listNextBatchOfObjects(bucketListing);
      summaries.addAll(bucketListing.getObjectSummaries());
    }

    Map<String, S3ObjectSummary> summariesByName = summaries
        .stream()
        .filter(this::filterS3ObjectSummary)
        .collect(Collectors.toMap(S3ObjectSummary::getKey, Function.identity()));

    Map<String, T> existingItemsByName = existingItems
        .stream()
        .filter(a -> summariesByName.containsKey(buildS3Key(a)))
        .collect(Collectors.toMap(Timestamped::getId, Function.identity()));

    summaries = summariesByName
        .values()
        .stream()
        .filter(s3ObjectSummary -> {
          String itemName = extractItemName(s3ObjectSummary);
          T existingItem = existingItemsByName.get(itemName);

          return existingItem == null || existingItem.getLastModified() == null || s3ObjectSummary.getLastModified().after(
              new Date(existingItem.getLastModified())
          );
        })
        .collect(Collectors.toList());

    Observable
        .from(summaries)
        .buffer(10)
        .flatMap(ids -> Observable
            .from(ids)
            .flatMap(s3ObjectSummary ->
                Observable.just(amazonS3.getObject(s3ObjectSummary.getBucketName(), s3ObjectSummary.getKey()))
            )
            .subscribeOn(scheduler)
        )
        .map(s3Object -> {
          try {
            T item = deserialize(s3Object);
            item.setLastModified(s3Object.getObjectMetadata().getLastModified().getTime());
            return item;
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        })
        .subscribeOn(scheduler)
        .toList()
        .toBlocking()
        .single()
        .forEach(item -> {
          existingItemsByName.put(item.getId().toLowerCase(), item);
        });

    lastRefreshedTime = System.currentTimeMillis();
    return existingItemsByName.values().stream().collect(Collectors.toSet());
  }

  protected String buildS3Key(T item) {
    return buildS3Key(item.getId());
  }

  protected String buildS3Key(String id) {
    return rootFolder + id.toLowerCase() + "/" + getMetadataFilename();
  }

  private T deserialize(S3Object s3Object) throws IOException {
    return objectMapper.readValue(s3Object.getObjectContent(), getSerializedClass());
  }

  private boolean filterS3ObjectSummary(S3ObjectSummary s3ObjectSummary) {
    return s3ObjectSummary.getKey().endsWith(getMetadataFilename());
  }

  private String extractItemName(S3ObjectSummary s3ObjectSummary) {
    return s3ObjectSummary.getKey().replaceAll(rootFolder, "").replaceAll("/" + getMetadataFilename(), "");
  }

  abstract Class<T> getSerializedClass();
  abstract String getMetadataFilename();
}
