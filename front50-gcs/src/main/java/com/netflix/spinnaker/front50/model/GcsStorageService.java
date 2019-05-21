/*
 * Copyright 2016 Google, Inc.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.StorageScopes;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.Objects;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.retry.GcsSafeRetry;
import groovy.lang.Closure;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

public class GcsStorageService implements StorageService {
  private static final String DEFAULT_DATA_FILENAME = "specification.json";
  private static final String LAST_MODIFIED_FILENAME = "last-modified";
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final GcsSafeRetry gcsSafeRetry = new GcsSafeRetry();

  private final Registry registry;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String projectName;
  private final String bucketName;
  private final String basePath;
  private final Storage storage;
  private final Storage.Objects obj_api;
  private final String dataFilename;
  private final Long maxWaitInterval;
  private final Long retryIntervalBase;
  private final Long jitterMultiplier;
  private final Long maxRetries;
  private final Id deleteTimer;
  private final Id purgeTimer; // for deleting timestamp generations
  private final Id loadTimer;
  private final Id mediaDownloadTimer;
  private final Id listTimer;
  private final Id insertTimer;
  private final Id patchTimer;
  private final TaskScheduler taskScheduler;

  private ConcurrentHashMap<String, AtomicBoolean> updateLockMap = new ConcurrentHashMap<>();
  private ConcurrentHashMap<String, AtomicBoolean> scheduledUpdateLockMap =
      new ConcurrentHashMap<>();

  @VisibleForTesting final HashSet<String> purgeOldVersionPaths = new HashSet<String>();

  /**
   * Bucket location for when a missing bucket is created. Has no effect if the bucket already
   * exists.
   */
  private String bucketLocation;

  public Storage getStorage() {
    return this.storage;
  }

  public ObjectMapper getObjectMapper() {
    return this.objectMapper;
  }

  private GoogleCredential loadCredential(
      HttpTransport transport, JsonFactory factory, String jsonPath) throws IOException {
    GoogleCredential credential;
    if (!jsonPath.isEmpty()) {
      FileInputStream stream = new FileInputStream(jsonPath);
      credential =
          GoogleCredential.fromStream(stream, transport, factory)
              .createScoped(Collections.singleton(StorageScopes.DEVSTORAGE_FULL_CONTROL));
      log.info("Loaded credentials from {}", value("jsonPath", jsonPath));
    } else {
      log.info(
          "spinnaker.gcs.enabled without spinnaker.gcs.jsonPath. "
              + "Using default application credentials. Using default credentials.");
      credential = GoogleCredential.getApplicationDefault();
    }
    return credential;
  }

  @VisibleForTesting
  GcsStorageService(
      String bucketName,
      String bucketLocation,
      String basePath,
      String projectName,
      Storage storage,
      int maxRetries,
      TaskScheduler taskScheduler,
      Registry registry) {
    this.bucketName = bucketName;
    this.bucketLocation = bucketLocation;
    this.basePath = basePath;
    this.projectName = projectName;
    this.storage = storage;
    this.registry = registry;
    this.obj_api = storage.objects();
    this.dataFilename = DEFAULT_DATA_FILENAME;
    this.maxWaitInterval = -1L;
    this.retryIntervalBase = -1L;
    this.jitterMultiplier = -1L;
    this.maxRetries = new Long(maxRetries);
    this.taskScheduler = taskScheduler;

    Id id = registry.createId("google.storage.invocation");
    deleteTimer = id.withTag("method", "delete");
    purgeTimer = id.withTag("method", "purgeTimestamp");
    loadTimer = id.withTag("method", "load");
    listTimer = id.withTag("method", "list");
    mediaDownloadTimer = id.withTag("method", "mediaDownload");
    insertTimer = id.withTag("method", "insert");
    patchTimer = id.withTag("method", "patch");
  }

  public GcsStorageService(
      String bucketName,
      String bucketLocation,
      String basePath,
      String projectName,
      String credentialsPath,
      String applicationVersion,
      Integer connectTimeoutSec,
      Integer readTimeoutSec,
      Long maxWaitInterval,
      Long retryIntervalBase,
      Long jitterMultiplier,
      Long maxRetries,
      TaskScheduler taskScheduler,
      Registry registry) {
    this(
        bucketName,
        bucketLocation,
        basePath,
        projectName,
        credentialsPath,
        applicationVersion,
        DEFAULT_DATA_FILENAME,
        connectTimeoutSec,
        readTimeoutSec,
        maxWaitInterval,
        retryIntervalBase,
        jitterMultiplier,
        maxRetries,
        taskScheduler,
        registry);
  }

  public GcsStorageService(
      String bucketName,
      String bucketLocation,
      String basePath,
      String projectName,
      String credentialsPath,
      String applicationVersion,
      String dataFilename,
      Integer connectTimeoutSec,
      Integer readTimeoutSec,
      Long maxWaitInterval,
      Long retryIntervalBase,
      Long jitterMultiplier,
      Long maxRetries,
      TaskScheduler taskScheduler,
      Registry registry) {
    Storage storage;

    try {
      HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
      JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      GoogleCredential credential = loadCredential(httpTransport, jsonFactory, credentialsPath);
      HttpRequestInitializer requestInitializer =
          new HttpRequestInitializer() {
            public void initialize(HttpRequest request) throws IOException {
              credential.initialize(request);
              request.setConnectTimeout(connectTimeoutSec * 1000);
              request.setReadTimeout(readTimeoutSec * 1000);
            }
          };

      String applicationName = "Spinnaker/" + applicationVersion;
      storage =
          new Storage.Builder(httpTransport, jsonFactory, credential)
              .setApplicationName(applicationName)
              .setHttpRequestInitializer(requestInitializer)
              .build();
    } catch (IOException | java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }

    // "google.com:" is deprecated but may be in certain old projects.
    this.bucketName = bucketName.replace("google.com:", "");
    this.bucketLocation = bucketLocation;
    this.basePath = basePath;
    this.projectName = projectName;
    this.storage = storage;
    this.obj_api = this.storage.objects();
    this.dataFilename = dataFilename;
    this.maxWaitInterval = maxWaitInterval;
    this.retryIntervalBase = retryIntervalBase;
    this.jitterMultiplier = jitterMultiplier;
    this.maxRetries = maxRetries;
    this.taskScheduler = taskScheduler;
    this.registry = registry;

    Id id = registry.createId("google.storage.invocation");
    deleteTimer = id.withTag("method", "delete");
    purgeTimer = id.withTag("method", "purgeTimestamp");
    loadTimer = id.withTag("method", "load");
    listTimer = id.withTag("method", "list");
    mediaDownloadTimer = id.withTag("method", "mediaDownload");
    insertTimer = id.withTag("method", "insert");
    patchTimer = id.withTag("method", "patch");
  }

  private <T> T timeExecute(Id timerId, AbstractGoogleClientRequest<T> request) throws IOException {
    T result;
    Clock clock = registry.clock();
    long startTime = clock.monotonicTime();
    int statusCode = -1;

    try {
      result = request.execute();
      statusCode = request.getLastStatusCode();
    } catch (HttpResponseException e) {
      statusCode = e.getStatusCode();
      throw e;
    } catch (IOException ioex) {
      throw ioex;
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    } finally {
      long nanos = clock.monotonicTime() - startTime;
      String status = Integer.toString(statusCode).charAt(0) + "xx";

      Id id = timerId.withTags("status", status, "statusCode", Integer.toString(statusCode));
      registry.timer(id).record(nanos, TimeUnit.NANOSECONDS);
    }
    return result;
  }

  /** Check to see if the bucket exists, creating it if it is not there. */
  public void ensureBucketExists() {
    try {
      Bucket bucket = storage.buckets().get(bucketName).execute();
    } catch (HttpResponseException e) {
      if (e.getStatusCode() == 404) {
        log.warn(
            "Bucket {} does not exist. Creating it in project={}",
            value("bucket", bucketName),
            value("project", projectName));
        Bucket.Versioning versioning = new Bucket.Versioning().setEnabled(true);
        Bucket bucket = new Bucket().setName(bucketName).setVersioning(versioning);
        if (StringUtils.isNotBlank(bucketLocation)) {
          bucket.setLocation(bucketLocation);
        }
        try {
          storage.buckets().insert(projectName, bucket).execute();
        } catch (IOException e2) {
          log.error(
              "Could not create bucket={} in project={}: {}",
              value("bucket", bucketName),
              value("project", projectName),
              e2.getMessage());
          throw new IllegalStateException(e2);
        }
      } else {
        log.error("Could not get bucket={}: {}", value("bucket", bucketName), e.getMessage());
        throw new IllegalStateException(e);
      }
    } catch (IOException e) {
      log.error("Could not get bucket={}: {}", value("bucket", bucketName), e.getMessage());
      throw new IllegalStateException(e);
    }
  }

  /** Returns true if the storage service supports versioning. */
  @Override
  public boolean supportsVersioning() {
    try {
      Bucket bucket = storage.buckets().get(bucketName).execute();
      Bucket.Versioning v = bucket.getVersioning();
      return v != null && v.getEnabled();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public <T extends Timestamped> T loadObject(ObjectType objectType, String objectKey)
      throws NotFoundException {
    String path = keyToPath(objectKey, objectType.group);
    try {
      StorageObject[] storageObjectHolder = new StorageObject[1];
      Closure timeExecuteClosure =
          new Closure<String>(this, this) {
            public Object doCall() throws Exception {
              storageObjectHolder[0] = timeExecute(loadTimer, obj_api.get(bucketName, path));
              return Closure.DONE;
            }
          };
      doRetry(timeExecuteClosure, "get", objectType.group + " " + objectKey);

      T item = deserialize(storageObjectHolder[0], (Class<T>) objectType.clazz, true);
      item.setLastModified(storageObjectHolder[0].getUpdated().getValue());
      log.debug("Loaded bucket={} path={}", value("bucket", bucketName), value("path", path));
      return item;
    } catch (IOException e) {
      if (e instanceof HttpResponseException) {
        HttpResponseException hre = (HttpResponseException) e;
        log.error(
            "Failed to load {} {}: {} {}",
            value("group", objectType.group),
            value("key", objectKey),
            value("responseStatus", hre.getStatusCode()),
            value("errorMsg", hre.getStatusMessage()));
        if (hre.getStatusCode() == 404) {
          throw new NotFoundException(String.format("No file at path=%s", path));
        }
      }
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void deleteObject(ObjectType objectType, String objectKey) {
    String path = keyToPath(objectKey, objectType.group);
    Closure timeExecuteClosure =
        new Closure(this, this) {
          public Object doCall() throws Exception {
            timeExecute(deleteTimer, obj_api.delete(bucketName, path));
            return Closure.DONE;
          }
        };
    doRetry(timeExecuteClosure, "delete", objectType.group, Arrays.asList(500), Arrays.asList(404));
    log.info("Deleted {} '{}'", value("group", objectType.group), value("key", objectKey));
    writeLastModified(objectType.group);
  }

  @Override
  public <T extends Timestamped> void storeObject(ObjectType objectType, String objectKey, T obj) {
    byte[] bytes;
    String path = keyToPath(objectKey, objectType.group);
    try {
      bytes = objectMapper.writeValueAsBytes(obj);
    } catch (JsonProcessingException e) {
      log.error("storeObject failed encoding object", e);
      throw new IllegalStateException(e);
    }
    StorageObject object = new StorageObject().setBucket(bucketName).setName(path);
    ByteArrayContent content = new ByteArrayContent("application/json", bytes);

    Closure timeExecuteClosure =
        new Closure(this, this) {
          public Object doCall() throws Exception {
            timeExecute(insertTimer, obj_api.insert(bucketName, object, content));
            return Closure.DONE;
          }
        };
    doRetry(timeExecuteClosure, "store", objectType.group);
    writeLastModified(objectType.group);
    log.info("Wrote {} '{}'", value("group", objectType.group), value("key", objectKey));
  }

  @Override
  public Map<String, Long> listObjectKeys(ObjectType objectType) {
    String rootFolder = daoRoot(objectType.group);
    int skipToOffset = rootFolder.length() + 1; // + Trailing slash
    int skipFromEnd = dataFilename.length() + 1; // + Leading slash

    Map<String, Long> result = new HashMap<String, Long>();
    log.debug("Listing {}", objectType.group);
    try {
      Storage.Objects.List listObjects = obj_api.list(bucketName);
      listObjects.setPrefix(rootFolder);
      Objects[] objectsHolder = new Objects[1];
      do {
        Closure timeExecuteClosure =
            new Closure<String>(this, this) {
              public Object doCall() throws Exception {
                objectsHolder[0] = timeExecute(listTimer, listObjects);
                return Closure.DONE;
              }
            };
        doRetry(timeExecuteClosure, "list", objectType.group);

        List<StorageObject> items = objectsHolder[0].getItems();
        if (items != null) {
          for (StorageObject item : items) {
            String name = item.getName();
            if (name.endsWith('/' + dataFilename)) {
              result.put(
                  name.substring(skipToOffset, name.length() - skipFromEnd),
                  item.getUpdated().getValue());
            }
          }
        }
        listObjects.setPageToken(objectsHolder[0].getNextPageToken());
      } while (objectsHolder[0].getNextPageToken() != null);
    } catch (IOException e) {
      log.error("Could not fetch items from Google Cloud Storage: {}", e.getMessage());
      return new HashMap<String, Long>();
    }

    return result;
  }

  @Override
  public <T extends Timestamped> Collection<T> listObjectVersions(
      ObjectType objectType, String objectKey, int maxResults) throws NotFoundException {
    String path = keyToPath(objectKey, objectType.group);
    ArrayList<T> result = new ArrayList<T>();
    try {
      // NOTE: gcs only returns things in forward chronological order
      // so to get maxResults, we need to download everything then
      // take the last maxResults, not .setMaxResults(new Long(maxResults)) here.
      Storage.Objects.List listObjects = obj_api.list(bucketName).setPrefix(path).setVersions(true);
      Objects[] objectsHolder = new Objects[1];
      do {
        Closure timeExecuteClosure =
            new Closure<String>(this, this) {
              public Object doCall() throws Exception {
                objectsHolder[0] = timeExecute(listTimer, listObjects);
                return Closure.DONE;
              }
            };
        doRetry(timeExecuteClosure, "list versions", objectType.group);

        List<StorageObject> items = objectsHolder[0].getItems();
        if (items != null) {
          for (StorageObject item : items) {
            T have = deserialize(item, (Class<T>) objectType.clazz, false);
            if (have != null) {
              have.setLastModified(item.getUpdated().getValue());
              result.add(have);
            }
          }
        }
        listObjects.setPageToken(objectsHolder[0].getNextPageToken());
      } while (objectsHolder[0].getNextPageToken() != null);
    } catch (IOException e) {
      log.error("Could not fetch versions from Google Cloud Storage: {}", e.getMessage());
      return new ArrayList<>();
    }

    Comparator<T> comp =
        (T a, T b) -> {
          // reverse chronological
          return b.getLastModified().compareTo(a.getLastModified());
        };
    Collections.sort(result, comp);
    if (result.size() > maxResults) {
      return result.subList(0, maxResults);
    }
    return result;
  }

  private <T extends Timestamped> T deserialize(
      StorageObject object, Class<T> clas, boolean current_version)
      throws java.io.UnsupportedEncodingException {
    try {
      ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
      Storage.Objects.Get getter = obj_api.get(object.getBucket(), object.getName());
      if (!current_version) {
        getter.setGeneration(object.getGeneration());
      }

      Closure timeExecuteClosure =
          new Closure<String>(this, this) {
            public Object doCall() throws Exception {
              Clock clock = registry.clock();
              long startTime = clock.monotonicTime();
              int statusCode = -1;

              try {
                getter.executeMediaAndDownloadTo(output);
                statusCode = getter.getLastStatusCode();
                if (statusCode < 0) {
                  // getLastStatusCode is returning -1
                  statusCode = 200;
                }
              } catch (HttpResponseException e) {
                statusCode = e.getStatusCode();
                throw e;
              } catch (Exception e) {
                log.error("mediaDownload exception from {}", object.getName(), e);
                throw e;
              } finally {
                long nanos = clock.monotonicTime() - startTime;
                String status = Integer.toString(statusCode).charAt(0) + "xx";
                Id id =
                    mediaDownloadTimer.withTags(
                        "status", status, "statusCode", Integer.toString(statusCode));
                registry.timer(id).record(nanos, TimeUnit.NANOSECONDS);
              }
              return Closure.DONE;
            }
          };
      doRetry(timeExecuteClosure, "deserialize", object.getName());

      String json = output.toString("UTF8");
      return objectMapper.readValue(json, clas);
    } catch (Exception ex) {
      if (current_version) {
        log.error("Error reading {}: ", value("object", object.getName()), ex);
      } else {
        log.error(
            "Error reading {} generation={}: ",
            value("object", object.getName()),
            value("generation", object.getGeneration()),
            ex);
      }
      return null;
    }
  }

  // Returns a boolean that a thread is preparing to update lastmodified (but has not yet sent the
  // request to GCS). If another thread observes this value as true, it can safely skip updating
  // lastmodified itself as this will be done by the other thread (and said update is guaranteed
  // to completely happen after the value was observed as true).
  private AtomicBoolean updateLock(String daoTypeName) {
    return updateLockMap.computeIfAbsent(daoTypeName, (String s) -> new AtomicBoolean(false));
  }

  // Returns a boolean that indicates a deferred update to lastmodified has been scheduled due to
  // receiving an error response from a prior update. If this value is true, a thread can safely
  // skip updating lastmodified, as this will be handled by the deferred update.
  private AtomicBoolean scheduledUpdateLock(String daoTypeName) {
    return scheduledUpdateLockMap.computeIfAbsent(
        daoTypeName, (String s) -> new AtomicBoolean(false));
  }

  @VisibleForTesting
  public void scheduleWriteLastModified(String daoTypeName) {
    Date when = new Date();
    when.setSeconds(when.getSeconds() + 2);
    GcsStorageService service = this;
    Runnable task =
        new Runnable() {
          public void run() {
            // Release the scheduled update lock, and perform the actual update
            scheduledUpdateLock(daoTypeName).set(false);
            log.info("RUNNING {}", daoTypeName);
            service.writeLastModified(daoTypeName);
          }
        };
    if (scheduledUpdateLock(daoTypeName).compareAndSet(false, true)) {
      log.info("Scheduling deferred update {} timestamp.", daoTypeName);
      taskScheduler.schedule(task, when);
    }
  }

  private void writeLastModified(String daoTypeName) {
    // We'll just touch the file since the StorageObject manages a timestamp.
    String timestamp_path = daoRoot(daoTypeName) + '/' + LAST_MODIFIED_FILENAME;
    StorageObject object =
        new StorageObject()
            .setBucket(bucketName)
            .setName(timestamp_path)
            .setUpdated(new DateTime(System.currentTimeMillis()));
    // Short-circuit if there's a scheduled update, or if another thread has already acquired the
    // lock and is updating lastModified.
    if (!scheduledUpdateLock(daoTypeName).get()
        && updateLock(daoTypeName).compareAndSet(false, true)) {
      try {
        synchronized (updateLock(daoTypeName)) {
          // Release the update lock *before* actually updating lastModified as any thread observing
          // the lock as set must know that the last modified time will be updated *after* it
          // observed
          // the lock
          // That is also the reason this block is synchronized; if a thread acquires the lock while
          // we're
          // writing lastModified, we want it to hold the lock and block until the current write is
          // done.
          // (At most one other thread will block in this manner; any further threads will
          // short-circuit
          // and piggy-back on the blocked thread's update.)
          updateLock(daoTypeName).set(false);
          timeExecute(patchTimer, obj_api.patch(bucketName, object.getName(), object));
        }
      } catch (HttpResponseException e) {
        if (e.getStatusCode() == 503 || e.getStatusCode() == 429) {
          log.warn("Could not write {}: {}", timestamp_path, e.getMessage());
          scheduleWriteLastModified(daoTypeName);
          return;
        }
        if (e.getStatusCode() == 404 || e.getStatusCode() == 400) {
          byte[] bytes = "{}".getBytes();
          ByteArrayContent content = new ByteArrayContent("application/json", bytes);

          try {
            log.info("Attempting to add {}", value("path", timestamp_path));
            timeExecute(insertTimer, obj_api.insert(bucketName, object, content));
          } catch (IOException ioex) {
            log.error(
                "writeLastModified failed to update {}\n{}",
                value("path", timestamp_path),
                e.toString());
            log.error("writeLastModified insert failed too", ioex);
            throw new IllegalStateException(e);
          }
        } else {
          log.error(
              "writeLastModified failed to update {}\n{}",
              value("path", timestamp_path),
              value("exception", e.toString()));
          throw new IllegalStateException(e);
        }
      } catch (IOException e) {
        log.error("writeLastModified failed:", e.getMessage());
        throw new IllegalStateException(e);
      }

      synchronized (purgeOldVersionPaths) {
        // If the bucket is versioned, purge the old timestamp versions
        // because they serve no value and just consume storage and extra time
        // if we eventually destroy this bucket.
        // These are queued to reduce rate limiting contention on the file since
        // it is a long term concern rather than a short term one.
        purgeOldVersionPaths.add(timestamp_path);
      }
    }
  }

  public void purgeBatchedVersionPaths() {
    String[] paths;
    synchronized (purgeOldVersionPaths) {
      if (purgeOldVersionPaths.isEmpty()) {
        return;
      }
      paths = purgeOldVersionPaths.toArray(new String[purgeOldVersionPaths.size()]);
      purgeOldVersionPaths.clear();
    }
    for (String path : paths) {
      try {
        purgeOldVersions(path);
      } catch (Exception e) {
        synchronized (purgeOldVersionPaths) {
          purgeOldVersionPaths.add(path); // try again next time.
        }
        log.warn("Failed to purge old versions of {}. Ignoring error.", value("path", path));
      }
    }
  }

  // Remove the old versions of a path.
  // Versioning is per-bucket but it doesnt make sense to version the
  // timestamp objects so we'll aggressively delete those.
  private void purgeOldVersions(String path) throws Exception {
    Storage.Objects.List listObjects = obj_api.list(bucketName).setPrefix(path).setVersions(true);

    com.google.api.services.storage.model.Objects objects;

    // Keep the 0th object on the first page (which is current).
    List<Long> generations = new ArrayList(32);
    do {
      objects = timeExecute(listTimer, listObjects);
      List<StorageObject> items = objects.getItems();
      if (items != null) {
        int n = items.size();
        while (--n >= 0) {
          generations.add(items.get(n).getGeneration());
        }
      }
      listObjects.setPageToken(objects.getNextPageToken());
    } while (objects.getNextPageToken() != null);

    for (long generation : generations) {
      if (generation == generations.get(0)) {
        continue;
      }
      log.debug("Remove {} generation {}", value("path", path), value("generation", generation));
      timeExecute(purgeTimer, obj_api.delete(bucketName, path).setGeneration(generation));
    }
  }

  @Override
  public long getLastModified(ObjectType objectType) {
    return getLastModifiedOfTypeName(objectType.group);
  }

  private long getLastModifiedOfTypeName(String daoTypeName) {
    String path = daoRoot(daoTypeName) + '/' + LAST_MODIFIED_FILENAME;
    try {
      long[] updatedTimestampHolder = new long[1];
      Closure timeExecuteClosure =
          new Closure<String>(this, this) {
            public Object doCall() throws Exception {
              updatedTimestampHolder[0] =
                  obj_api.get(bucketName, path).execute().getUpdated().getValue();
              return Closure.DONE;
            }
          };
      doRetry(timeExecuteClosure, "get last modified", daoTypeName);

      return updatedTimestampHolder[0];
    } catch (Exception e) {
      if (e instanceof HttpResponseException) {
        HttpResponseException hre = (HttpResponseException) e;
        long now = System.currentTimeMillis();
        if (hre.getStatusCode() == 404) {
          log.info("No timestamp file at {}. Creating a new one.", value("path", path));
          writeLastModified(daoTypeName);
          return now;
        }
        log.error("Error writing timestamp file:", e);
        return now;
      } else {
        log.error("Error accessing timestamp file:", e);
        return System.currentTimeMillis();
      }
    }
  }

  private String daoRoot(String daoTypeName) {
    return basePath + '/' + daoTypeName;
  }

  private String keyToPath(String key, String daoTypeName) {
    return daoRoot(daoTypeName) + '/' + key + '/' + dataFilename;
  }

  public void doRetry(Closure operation, String action, String resource) {
    doRetry(operation, action, resource, Arrays.asList(500), null);
  }

  public void doRetry(
      Closure operation, String action, String resource, List errorCodes, List successCodes) {
    gcsSafeRetry.doRetry(
        operation,
        resource,
        errorCodes,
        successCodes,
        maxWaitInterval,
        retryIntervalBase,
        jitterMultiplier,
        maxRetries,
        ImmutableMap.of("action", action),
        registry);
  }
}
