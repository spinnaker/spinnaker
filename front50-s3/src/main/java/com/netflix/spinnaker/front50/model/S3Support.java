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
import com.netflix.spinnaker.front50.support.ClosureHelper;
import com.netflix.spinnaker.hystrix.SimpleHystrixCommand;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import org.apache.commons.codec.digest.DigestUtils;
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
    return all(true);
  }

  public Collection<T> all(boolean refreshCache) {
    if (allItemsCache.get() == null || (refreshCache && readLastModified() > lastRefreshedTime)) {
      // only refresh if there was a modification since our last refresh cycle
      refresh();
    }

    return allItemsCache.get().stream().collect(Collectors.toList());
  }

  /**
   * @return Healthy if refreshed in the past 45s
   */
  public boolean isHealthy() {
    return (System.currentTimeMillis() - lastRefreshedTime) < 45000 && allItemsCache.get() != null;
  }

  public T findById(String id) throws NotFoundException {
    return new SimpleHystrixCommand<T>(
        getClass().getSimpleName(),
        getClass().getSimpleName() + "-findById",
        ClosureHelper.toClosure(args -> {
          try {
            S3Object s3Object = amazonS3.getObject(bucket, buildS3Key(id));
            T item = deserialize(s3Object);
            item.setLastModified(s3Object.getObjectMetadata().getLastModified().getTime());
            return item;
          } catch (IOException e) {
            throw new IllegalStateException(e);
          } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404) {
              throw new NotFoundException(String.format("No item found with id of %s", id.toLowerCase()));
            }

            throw e;
          }
        }),
        ClosureHelper.toClosure(args -> allItemsCache.get().stream()
            .filter(item -> item.getId().equalsIgnoreCase(id))
            .findFirst()
            .orElseThrow(() -> new NotFoundException(String.format("No item found in cache with id of %s", id.toLowerCase()))))
    ).execute();
  }

  public Collection<T> allVersionsOf(String id, int limit) throws NotFoundException {
    try {
      VersionListing versionListing = amazonS3.listVersions(new ListVersionsRequest(bucket, buildS3Key(id), null, null, null, limit));
      return versionListing.getVersionSummaries().stream().map(s3VersionSummary -> {
        try {
          S3Object s3Object = amazonS3.getObject(
              new GetObjectRequest(bucket, buildS3Key(id), s3VersionSummary.getVersionId())
          );
          T item = deserialize(s3Object);
          item.setLastModified(s3Object.getObjectMetadata().getLastModified().getTime());
          return item;
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      }).collect(Collectors.toList());
    } catch (AmazonS3Exception e) {
      if (e.getStatusCode() == 404) {
        throw new NotFoundException(String.format("No item found with id of %s", id.toLowerCase()));
      }

      throw e;
    }
  }

  public void update(String id, T item) {
    try {
      item.setLastModifiedBy(AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));
      byte[] bytes = objectMapper.writeValueAsBytes(item);

      ObjectMetadata objectMetadata = new ObjectMetadata();
      objectMetadata.setContentLength(bytes.length);
      objectMetadata.setContentMD5(new String(org.apache.commons.codec.binary.Base64.encodeBase64(DigestUtils.md5(bytes))));

      amazonS3.putObject(
          bucket,
          buildS3Key(id),
          new ByteArrayInputStream(bytes),
          objectMetadata
      );
      writeLastModified();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public void delete(String id) {
    amazonS3.deleteObject(bucket, buildS3Key(id));
    writeLastModified();
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

    Long refreshTime = System.currentTimeMillis();

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
            .flatMap(s3ObjectSummary -> {
                  try {
                    return Observable.just(amazonS3.getObject(s3ObjectSummary.getBucketName(), s3ObjectSummary.getKey()));
                  } catch (AmazonS3Exception e) {
                    if (e.getStatusCode() == 404) {
                      // an item has been removed between the time that object summaries were fetched and now
                      existingItemsByName.remove(extractItemName(s3ObjectSummary));
                      return Observable.empty();
                    }

                    throw e;
                  }
                }
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

    existingItems = existingItemsByName.values().stream().collect(Collectors.toSet());
    this.lastRefreshedTime = refreshTime;
    return existingItems;
  }

  protected String buildS3Key(T item) {
    return buildS3Key(item.getId());
  }

  protected String buildS3Key(String id) {
    return rootFolder + id.toLowerCase() + "/" + getMetadataFilename();
  }

  private void writeLastModified() {
    try {
      byte[] bytes = objectMapper.writeValueAsBytes(Collections.singletonMap("lastModified", System.currentTimeMillis()));

      ObjectMetadata objectMetadata = new ObjectMetadata();
      objectMetadata.setContentLength(bytes.length);
      objectMetadata.setContentMD5(new String(org.apache.commons.codec.binary.Base64.encodeBase64(DigestUtils.md5(bytes))));

      amazonS3.putObject(
          bucket,
          rootFolder + "last-modified.json",
          new ByteArrayInputStream(bytes),
          objectMetadata
      );
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private Long readLastModified() {
    try {
      Map<String, Long> lastModified = objectMapper.readValue(
          amazonS3.getObject(bucket, rootFolder + "last-modified.json").getObjectContent(),
          Map.class
      );
      return lastModified.get("lastModified");
    } catch (Exception e) {
      return 0L;
    }
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
