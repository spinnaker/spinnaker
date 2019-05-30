/*
 * Copyright 2017 Netflix, Inc.
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

import static net.logstash.logback.argument.StructuredArguments.value;

import com.amazonaws.services.sqs.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.config.S3Properties;
import com.netflix.spinnaker.front50.model.events.S3Event;
import com.netflix.spinnaker.front50.model.events.S3EventWrapper;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An ObjectKeyLoader is responsible for returning a last modified timestamp for all objects of a
 * particular type.
 *
 * <p>This implementation listens to an S3 event stream and applies incremental updates whenever an
 * event is received indicating that an object has been modified (add/update/delete).
 *
 * <p>It is significantly faster than delegating to `s3StorageService.listObjectKeys()` with some
 * slight latency attributed to the time taken for an event to be received and processed.
 *
 * <p>Expected latency is less than 1s (Amazon
 */
public class EventingS3ObjectKeyLoader implements ObjectKeyLoader, Runnable {
  private static final Logger log = LoggerFactory.getLogger(EventingS3ObjectKeyLoader.class);
  private static final Executor executor = Executors.newFixedThreadPool(5);

  private final ObjectMapper objectMapper;
  private final TemporarySQSQueue temporarySQSQueue;
  private final StorageService storageService;
  private final Registry registry;

  private final Cache<KeyWithObjectType, Long> objectKeysByLastModifiedCache;
  private final LoadingCache<ObjectType, Map<String, Long>> objectKeysByObjectTypeCache;

  private final String rootFolder;

  private boolean pollForMessages = true;

  public EventingS3ObjectKeyLoader(
      ExecutorService executionService,
      ObjectMapper objectMapper,
      S3Properties s3Properties,
      TemporarySQSQueue temporarySQSQueue,
      StorageService storageService,
      Registry registry,
      boolean scheduleImmediately) {
    this.objectMapper = objectMapper;
    this.temporarySQSQueue = temporarySQSQueue;
    this.storageService = storageService;
    this.registry = registry;

    this.objectKeysByLastModifiedCache =
        CacheBuilder.newBuilder()
            // ensure that these keys only expire _after_ their object type has been refreshed
            .expireAfterWrite(
                s3Properties.getEventing().getRefreshIntervalMs() + 60000, TimeUnit.MILLISECONDS)
            .recordStats()
            .build();

    this.objectKeysByObjectTypeCache =
        CacheBuilder.newBuilder()
            .refreshAfterWrite(
                s3Properties.getEventing().getRefreshIntervalMs(), TimeUnit.MILLISECONDS)
            .recordStats()
            .build(
                new CacheLoader<ObjectType, Map<String, Long>>() {
                  @Override
                  public Map<String, Long> load(ObjectType objectType) throws Exception {
                    log.debug("Loading object keys for {}", value("type", objectType));
                    return storageService.listObjectKeys(objectType);
                  }

                  @Override
                  public ListenableFuture<Map<String, Long>> reload(
                      ObjectType objectType, Map<String, Long> previous) throws Exception {
                    ListenableFutureTask<Map<String, Long>> task =
                        ListenableFutureTask.create(
                            () -> {
                              log.debug(
                                  "Refreshing object keys for {} (asynchronous)",
                                  value("type", objectType));
                              return storageService.listObjectKeys(objectType);
                            });
                    executor.execute(task);
                    return task;
                  }
                });

    this.rootFolder = s3Properties.getRootFolder();

    if (scheduleImmediately) {
      executionService.submit(this);
    }
  }

  @Override
  @PreDestroy
  public void shutdown() {
    log.debug("Stopping ...");
    pollForMessages = false;
    log.debug("Stopped");
  }

  @Override
  public Map<String, Long> listObjectKeys(ObjectType objectType) {
    if (!storageService.supportsEventing(objectType)) {
      return storageService.listObjectKeys(objectType);
    }

    try {
      Map<String, Long> objectKeys = objectKeysByObjectTypeCache.get(objectType);
      objectKeysByLastModifiedCache.asMap().entrySet().stream()
          .filter(e -> e.getKey().objectType == objectType)
          .forEach(
              e -> {
                String key = e.getKey().key;
                if (objectKeys.containsKey(key)) {
                  Long currentLastModifiedTime = e.getValue();
                  Long previousLastModifiedTime = objectKeys.get(key);
                  if (currentLastModifiedTime > previousLastModifiedTime) {
                    log.info(
                        "Detected Recent Modification (type: {}, key: {}, previous: {}, current: {})",
                        value("type", objectType),
                        value("key", key),
                        value("previousTime", new Date(previousLastModifiedTime)),
                        value("currentTime", new Date(e.getValue())));
                    objectKeys.put(key, currentLastModifiedTime);
                  }
                } else {
                  log.info(
                      "Detected Recent Modification (type: {}, key: {}, current: {})",
                      value("type", objectType),
                      value("key", key),
                      value("currentTime", new Date(e.getValue())));
                  objectKeys.put(key, e.getValue());
                }
              });
      return objectKeys;
    } catch (ExecutionException e) {
      log.error("Unable to fetch keys from cache", e);
      return storageService.listObjectKeys(objectType);
    }
  }

  @Override
  public void run() {
    while (pollForMessages) {
      try {
        List<Message> messages = temporarySQSQueue.fetchMessages();

        if (messages.isEmpty()) {
          continue;
        }

        messages.forEach(
            message -> {
              S3Event s3Event = unmarshall(objectMapper, message.getBody());
              if (s3Event != null) {
                tick(s3Event);
              }
              temporarySQSQueue.markMessageAsHandled(message.getReceiptHandle());
            });
      } catch (Exception e) {
        log.error("Failed to poll for messages", e);
        registry.counter("s3.eventing.pollErrors").increment();
      }
    }
  }

  private void tick(S3Event s3Event) {
    s3Event.records.forEach(
        record -> {
          if (record.s3.object.key.endsWith("last-modified.json")) {
            return;
          }

          String eventType = record.eventName;
          KeyWithObjectType keyWithObjectType = buildObjectKey(rootFolder, record.s3.object.key);
          DateTime eventTime = new DateTime(record.eventTime);

          log.debug(
              "Received Event (objectType: {}, type: {}, key: {}, delta: {})",
              value("objectType", keyWithObjectType.objectType),
              value("type", eventType),
              value("key", keyWithObjectType.key),
              value("delta", System.currentTimeMillis() - eventTime.getMillis()));

          objectKeysByLastModifiedCache.put(keyWithObjectType, eventTime.getMillis());
        });
  }

  private static KeyWithObjectType buildObjectKey(String rootFolder, String s3ObjectKey) {
    if (!rootFolder.endsWith("/")) {
      rootFolder = rootFolder + "/";
    }

    s3ObjectKey = s3ObjectKey.replace(rootFolder, "");
    s3ObjectKey = s3ObjectKey.substring(s3ObjectKey.indexOf("/") + 1);

    String metadataFilename = s3ObjectKey.substring(s3ObjectKey.lastIndexOf("/") + 1);
    s3ObjectKey = s3ObjectKey.substring(0, s3ObjectKey.lastIndexOf("/"));

    try {
      s3ObjectKey = URLDecoder.decode(s3ObjectKey, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalArgumentException("Invalid key '" + s3ObjectKey + "' (non utf-8)");
    }

    ObjectType objectType =
        Arrays.stream(ObjectType.values())
            .filter(o -> o.defaultMetadataFilename.equalsIgnoreCase(metadataFilename))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No ObjectType found (defaultMetadataFileName: " + metadataFilename + ")"));

    return new KeyWithObjectType(objectType, s3ObjectKey);
  }

  private static S3Event unmarshall(ObjectMapper objectMapper, String messageBody) {
    S3EventWrapper s3EventWrapper;
    try {
      s3EventWrapper = objectMapper.readValue(messageBody, S3EventWrapper.class);
    } catch (IOException e) {
      log.debug("Unable unmarshal S3EventWrapper (body: {})", value("message", messageBody), e);
      return null;
    }

    try {
      return objectMapper.readValue(s3EventWrapper.message, S3Event.class);
    } catch (IOException e) {
      log.debug("Unable unmarshal S3Event (body: {})", value("body", s3EventWrapper.message), e);
      return null;
    }
  }

  private static class KeyWithObjectType {
    final ObjectType objectType;
    final String key;

    KeyWithObjectType(ObjectType objectType, String key) {
      this.objectType = objectType;
      this.key = key;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      KeyWithObjectType that = (KeyWithObjectType) o;

      if (objectType != that.objectType) return false;
      return key.equals(that.key);
    }

    @Override
    public int hashCode() {
      int result = objectType.hashCode();
      result = 31 * result + key.hashCode();
      return result;
    }
  }
}
