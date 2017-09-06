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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.front50.exception.NotFoundException;

import com.google.api.services.storage.Storage;
import com.google.api.services.storage.StorageScopes;
import com.google.api.services.storage.model.*;

import com.google.api.services.storage.model.Bucket;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.util.DateTime;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Callable;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spinnaker.front50.retry.GcsSafeRetry;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import groovy.lang.Closure;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


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
  private final Timer deleteTimer;
  private final Timer purgeTimer;  // for deleting timestamp generations
  private final Timer loadTimer;
  private final Timer mediaDownloadTimer;
  private final Timer listTimer;
  private final Timer insertTimer;
  private final Timer patchTimer;

  /**
   * Bucket location for when a missing bucket is created. Has no effect if the bucket already
   * exists.
   */
  private String bucketLocation;

  public Storage getStorage() { return this.storage; }
  public ObjectMapper getObjectMapper() { return this.objectMapper; }

  private GoogleCredential loadCredential(
      HttpTransport transport, JsonFactory factory, String jsonPath)
      throws IOException {
    GoogleCredential credential;
    if (!jsonPath.isEmpty()) {
      FileInputStream stream = new FileInputStream(jsonPath);
      credential = GoogleCredential.fromStream(stream, transport, factory)
                      .createScoped(Collections.singleton(StorageScopes.DEVSTORAGE_FULL_CONTROL));
      log.info("Loaded credentials from " + jsonPath);
    } else {
      log.info("spinnaker.gcs.enabled without spinnaker.gcs.jsonPath. " +
                   "Using default application credentials. Using default credentials.");
      credential = GoogleCredential.getApplicationDefault();
    }
    return credential;
  }

  @VisibleForTesting
  GcsStorageService(String bucketName,
                    String bucketLocation,
                    String basePath,
                    String projectName,
                    Storage storage,
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
    this.maxRetries = -1L;

    Id id = registry.createId("google.storage.invocation");
    deleteTimer = registry.timer(id.withTag("method", "delete"));
    purgeTimer = registry.timer(id.withTag("method", "purgeTimestamp"));
    loadTimer = registry.timer(id.withTag("method", "load"));
    listTimer = registry.timer(id.withTag("method", "list"));
    mediaDownloadTimer = registry.timer(id.withTag("method", "mediaDownload"));
    insertTimer = registry.timer(id.withTag("method", "insert"));
    patchTimer = registry.timer(id.withTag("method", "patch"));
  }

  public GcsStorageService(String bucketName,
                           String bucketLocation,
                           String basePath,
                           String projectName,
                           String credentialsPath,
                           String applicationVersion,
                           Long maxWaitInterval,
                           Long retryIntervalBase,
                           Long jitterMultiplier,
                           Long maxRetries,
                           Registry registry) {
    this(bucketName,
         bucketLocation,
         basePath,
         projectName,
         credentialsPath,
         applicationVersion,
         DEFAULT_DATA_FILENAME,
         maxWaitInterval,
         retryIntervalBase,
         jitterMultiplier,
         maxRetries,
         registry);
  }

  public GcsStorageService(String bucketName,
                           String bucketLocation,
                           String basePath,
                           String projectName,
                           String credentialsPath,
                           String applicationVersion,
                           String dataFilename,
                           Long maxWaitInterval,
                           Long retryIntervalBase,
                           Long jitterMultiplier,
                           Long maxRetries,
                           Registry registry) {
    Storage storage;

    try {
      HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
      JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      GoogleCredential credential = loadCredential(httpTransport, jsonFactory,
                                                   credentialsPath);

      String applicationName = "Spinnaker/" + applicationVersion;
      storage = new Storage.Builder(httpTransport, jsonFactory, credential)
                           .setApplicationName(applicationName)
                           .build();
    } catch (IOException|java.security.GeneralSecurityException e) {
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
    this.registry = registry;

    Id id = registry.createId("google.storage.invocation");
    deleteTimer = registry.timer(id.withTag("method", "delete"));
    purgeTimer = registry.timer(id.withTag("method", "purgeTimestamp"));
    loadTimer = registry.timer(id.withTag("method", "load"));
    listTimer = registry.timer(id.withTag("method", "list"));
    mediaDownloadTimer = registry.timer(id.withTag("method", "mediaDownload"));
    insertTimer = registry.timer(id.withTag("method", "insert"));
    patchTimer = registry.timer(id.withTag("method", "patch"));
  }

  private <T> T timeExecute(Timer timer, AbstractGoogleClientRequest<T> request) throws IOException {
     try {
       return timer.record(new Callable<T>() {
          public T call() throws IOException {
            return request.execute();
          }
       });
     } catch (IOException ioex) {
         throw ioex;
     } catch (Exception ex) {
         throw new IllegalStateException(ex);
     }
  }

  /**
   * Check to see if the bucket exists, creating it if it is not there.
   */
  public void ensureBucketExists() {
    try {
      Bucket bucket = storage.buckets().get(bucketName).execute();
    } catch (HttpResponseException e) {
      if (e.getStatusCode() == 404) {
        log.warn("Bucket {} does not exist. Creating it in project={}",
                 bucketName, projectName);
        Bucket.Versioning versioning = new Bucket.Versioning().setEnabled(true);
        Bucket bucket = new Bucket().setName(bucketName).setVersioning(versioning);
        if (StringUtils.isNotBlank(bucketLocation)) {
          bucket.setLocation(bucketLocation);
        }
        try {
            storage.buckets().insert(projectName, bucket).execute();
        } catch (IOException e2) {
            log.error("Could not create bucket={} in project={}: {}",
                      bucketName, projectName, e2);
            throw new IllegalStateException(e2);
        }
      } else {
          log.error("Could not get bucket={}: {}", bucketName, e);
          throw new IllegalStateException(e);
      }
    } catch (IOException e) {
        log.error("Could not get bucket={}: {}", bucketName, e);
        throw new IllegalStateException(e);
    }
  }

  /**
   * Returns true if the storage service supports versioning.
   */
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
  public <T extends Timestamped> T loadObject(ObjectType objectType, String objectKey) throws NotFoundException {
    String path = keyToPath(objectKey, objectType.group);
    try {
      StorageObject[] storageObjectHolder = new StorageObject[1];
      Closure timeExecuteClosure = new Closure<String>(this, this) {
        public Object doCall() throws Exception {
          storageObjectHolder[0] = timeExecute(loadTimer, obj_api.get(bucketName, path));
          return Closure.DONE;
        }
      };
      doRetry(timeExecuteClosure, "get", objectType.group + " " + objectKey);

      T item = deserialize(storageObjectHolder[0], (Class<T>) objectType.clazz, true);
      item.setLastModified(storageObjectHolder[0].getUpdated().getValue());
      log.debug("Loaded bucket={} path={}", bucketName, path);
      return item;
    } catch (IOException e) {
      if (e instanceof HttpResponseException) {
        HttpResponseException hre = (HttpResponseException)e;
        log.error("Failed to load {} {}: {} {}",
                  objectType.group, objectKey, hre.getStatusCode(), hre.getStatusMessage());
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
    try {
      timeExecute(deleteTimer, obj_api.delete(bucketName, path));
      log.info("Deleted {} '{}'", objectType.group, objectKey);
      writeLastModified(objectType.group);
    } catch (HttpResponseException e) {
      if (e.getStatusCode() == 404) {
          return;
      }
      throw new IllegalStateException(e);
    } catch (IOException ioex) {
        log.error("Failed to delete path={}: {}", path, ioex);
      throw new IllegalStateException(ioex);
    }
  }

  @Override
  public <T extends Timestamped> void storeObject(ObjectType objectType, String objectKey, T obj) {
    obj.setLastModifiedBy(AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));

    String path = keyToPath(objectKey, objectType.group);
    try {
      byte[] bytes = objectMapper.writeValueAsBytes(obj);
      StorageObject object = new StorageObject().setBucket(bucketName).setName(path);
      ByteArrayContent content = new ByteArrayContent("application/json", bytes);
      timeExecute(insertTimer, obj_api.insert(bucketName, object, content));
      writeLastModified(objectType.group);
      log.info("Wrote {} '{}'", objectType.group, objectKey);
    } catch (IOException e) {
      log.error("Update failed on path={}: {}", path, e);
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Map<String, Long> listObjectKeys(ObjectType objectType) {
    String rootFolder = daoRoot(objectType.group);
    int skipToOffset = rootFolder.length() + 1;  // + Trailing slash
    int skipFromEnd = dataFilename.length() + 1;  // + Leading slash

    Map<String, Long> result = new HashMap<String, Long>();
    log.debug("Listing {}", objectType.group);
    try {
      Storage.Objects.List listObjects = obj_api.list(bucketName);
      listObjects.setPrefix(rootFolder);
      Objects[] objectsHolder = new Objects[1];
      do {
        Closure timeExecuteClosure = new Closure<String>(this, this) {
          public Object doCall() throws Exception {
            objectsHolder[0] = timeExecute(listTimer, listObjects);
            return Closure.DONE;
          }
        };
        doRetry(timeExecuteClosure, "list", objectType.group);

        List<StorageObject> items = objectsHolder[0].getItems();
        if (items != null) {
          for (StorageObject item: items) {
            String name = item.getName();
            if (name.endsWith(dataFilename)) {
              result.put(name.substring(skipToOffset, name.length() - skipFromEnd), item.getUpdated().getValue());
            }
          }
        }
        listObjects.setPageToken(objectsHolder[0].getNextPageToken());
      } while (objectsHolder[0].getNextPageToken() != null);
    } catch (IOException e) {
      log.error("Could not fetch items from Google Cloud Storage: {}", e);
      return new HashMap<String, Long>();
    }

    return result;
  }

  @Override
  public <T extends Timestamped> Collection<T> listObjectVersions(ObjectType objectType, String objectKey, int maxResults) throws NotFoundException {
    String path = keyToPath(objectKey, objectType.group);
    ArrayList<T> result = new ArrayList<T>();
    try {
      // NOTE: gcs only returns things in forward chronological order
      // so to get maxResults, we need to download everything then
      // take the last maxResults, not .setMaxResults(new Long(maxResults)) here.
      Storage.Objects.List listObjects = obj_api.list(bucketName)
        .setPrefix(path)
        .setVersions(true);
      Objects[] objectsHolder = new Objects[1];
      do {
        Closure timeExecuteClosure = new Closure<String>(this, this) {
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
      log.error("Could not fetch versions from Google Cloud Storage: {}", e);
      return new ArrayList<>();
    }

    Comparator<T> comp = (T a, T b) -> {
      // reverse chronological
      return b.getLastModified().compareTo(a.getLastModified());
    };
    Collections.sort(result, comp);
    if (result.size() > maxResults) {
      return result.subList(0, maxResults);
    }
    return result;
  }

  private <T extends Timestamped> T deserialize(StorageObject object, Class<T> clas, boolean current_version)
      throws java.io.UnsupportedEncodingException {
    try {
        ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        Storage.Objects.Get getter = obj_api.get(object.getBucket(), object.getName());
        if (!current_version) {
            getter.setGeneration(object.getGeneration());
        }

        Closure timeExecuteClosure = new Closure<String>(this, this) {
            public Object doCall() throws Exception {
              mediaDownloadTimer.record(new Callable() {
                public Void call() throws Exception {
                  getter.executeMediaAndDownloadTo(output);
                  return null;
                }
              });
              return Closure.DONE;
            }
        };
        doRetry(timeExecuteClosure, "deserialize", object.getName());

        String json = output.toString("UTF8");
        return objectMapper.readValue(json, clas);
      } catch (Exception ex) {
        if (current_version) {
          log.error("Error reading {}: {}", object.getName(), ex);
        } else {
          log.error("Error reading {} generation={}: {}",
                    object.getName(), object.getGeneration(), ex);
        }
        return null;
    }
  }

  private void writeLastModified(String daoTypeName) {
      // We'll just touch the file since the StorageObject manages a timestamp.
      String timestamp_path = daoRoot(daoTypeName) + '/' + LAST_MODIFIED_FILENAME;
      StorageObject object = new StorageObject()
          .setBucket(bucketName)
          .setName(timestamp_path)
          .setUpdated(new DateTime(System.currentTimeMillis()));
      try {
          timeExecute(patchTimer, obj_api.patch(bucketName, object.getName(), object));
      } catch (HttpResponseException e) {
          if (e.getStatusCode() == 404 || e.getStatusCode() == 400) {
              byte[] bytes = "{}".getBytes();
              ByteArrayContent content = new ByteArrayContent("application/json", bytes);

              try {
                log.info("Attempting to add {}", timestamp_path);
                timeExecute(insertTimer, obj_api.insert(bucketName, object, content));
              } catch (IOException ioex) {
                  log.error("writeLastModified failed to update {}\n{}",
                            timestamp_path, e.toString());
                  log.error("writeLastModified insert failed too: {}", ioex);
                throw new IllegalStateException(e);
              }
          } else {
              log.error("writeLastModified failed to update {}\n{}",
                        timestamp_path, e.toString());
              throw new IllegalStateException(e);
          }
      } catch (IOException e) {
          log.error("writeLastModified failed: {}", e);
          throw new IllegalStateException(e);
      }

      try {
          // If the bucket is versioned, purge the old timestamp versions
          // because they serve no value and just consume storage and extra time
          // if we eventually destroy this bucket.
          purgeOldVersions(timestamp_path);
      } catch (Exception e) {
          log.warn("Failed to purge old versions of {}. Ignoring error.",
                   timestamp_path);
      }
  }

  // Remove the old versions of a path.
  // Versioning is per-bucket but it doesnt make sense to version the
  // timestamp objects so we'll aggressively delete those.
  private void purgeOldVersions(String path) throws Exception {
      Storage.Objects.List listObjects = obj_api.list(bucketName)
          .setPrefix(path)
          .setVersions(true);

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
        log.debug("Remove {} generation {}", path, generation);
        timeExecute(purgeTimer, obj_api.delete(bucketName, path).setGeneration(generation));
      }
  }

  @Override
  public long getLastModified(ObjectType objectType) {
      String path = daoRoot(objectType.group) + '/' + LAST_MODIFIED_FILENAME;
      try {
        long[] updatedTimestampHolder = new long[1];
        Closure timeExecuteClosure = new Closure<String>(this, this) {
          public Object doCall() throws Exception {
            updatedTimestampHolder[0] = obj_api.get(bucketName, path).execute().getUpdated().getValue();
            return Closure.DONE;
          }
        };
        doRetry(timeExecuteClosure, "get last modified", objectType.group);

        return updatedTimestampHolder[0];
      } catch (Exception e) {
        if (e instanceof HttpResponseException) {
          HttpResponseException hre = (HttpResponseException)e;
          long now = System.currentTimeMillis();
          if (hre.getStatusCode() == 404) {
              log.info("No timestamp file at {}. Creating a new one.", path);
              writeLastModified(objectType.group);
              return now;
          }
          log.error("Error writing timestamp file {}", e.toString());
          return now;
        } else {
          log.error("Error accessing timestamp file {}", e.toString());
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

  public void doRetry(Closure operation,
                      String action,
                      String resource) {
      gcsSafeRetry.doRetry(operation,
                           resource,
                           null,
                           Arrays.asList(500),
                           null,
                           maxWaitInterval,
                           retryIntervalBase,
                           jitterMultiplier,
                           maxRetries,
                           ImmutableMap.of("action", action),
                           registry);
  }
}
