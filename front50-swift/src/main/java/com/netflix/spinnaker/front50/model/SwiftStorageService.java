/*
 * Copyright 2017 Veritas Technologies, LLC.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.storage.ObjectStorageService;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.common.Payloads;
import org.openstack4j.model.identity.v3.Token;
import org.openstack4j.model.storage.object.SwiftContainer;
import org.openstack4j.model.storage.object.SwiftObject;
import org.openstack4j.model.storage.object.options.ContainerListOptions;
import org.openstack4j.model.storage.object.options.CreateUpdateContainerOptions;
import org.openstack4j.model.storage.object.options.ObjectListOptions;
import org.openstack4j.model.storage.object.options.ObjectPutOptions;
import org.openstack4j.openstack.OSFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwiftStorageService implements StorageService {
  private static final Logger log = LoggerFactory.getLogger(SwiftStorageService.class);

  private final ObjectStorageService swift;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String containerName;

  private Token token = null;

  public ObjectStorageService getSwift() {
    if (token != null) {
      return OSFactory.clientFromToken(token).objectStorage();
    } else return this.swift;
  }

  public ObjectMapper getObjectMapper() {
    return this.objectMapper;
  }

  public SwiftStorageService(String containerName, ObjectStorageService swift) {
    this.swift = swift;
    this.containerName = containerName;
  }

  public SwiftStorageService(
      String containerName,
      String identityEndpoint,
      String username,
      String password,
      String projectName,
      String domainName) {
    OSClient.OSClientV3 os =
        OSFactory.builderV3()
            .endpoint(identityEndpoint)
            .credentials(username, password, Identifier.byName(domainName))
            .scopeToProject(Identifier.byName(projectName), Identifier.byName(domainName))
            .authenticate();
    this.token = os.getToken();
    this.swift = os.objectStorage();
    this.containerName = containerName;
  }

  /** Check to see if the bucket (Swift container) exists, creating it if it is not there. */
  @Override
  public void ensureBucketExists() {
    List<? extends SwiftContainer> containers =
        getSwift().containers().list(ContainerListOptions.create().startsWith(containerName));

    boolean exists = false;

    if (containers != null) {
      for (SwiftContainer c : containers) {
        if (c.getName().equals(containerName)) {
          exists = true;
          break;
        }
      }
    }

    if (!exists) {
      getSwift()
          .containers()
          .create(
              containerName,
              CreateUpdateContainerOptions.create().versionsLocation(containerName + "-versions"));
    }
  }

  @Override
  public boolean supportsVersioning() {
    Map metadata = getSwift().containers().getMetadata(containerName);
    if (metadata.containsKey("X-Versions-Location")) {
      return true;
    }
    return false;
  }

  @Override
  public <T extends Timestamped> T loadObject(ObjectType objectType, String objectKey)
      throws NotFoundException {
    SwiftObject o = getSwift().objects().get(containerName, objectKey);
    return deserialize(o, (Class<T>) objectType.clazz);
  }

  @Override
  public void deleteObject(ObjectType objectType, String objectKey) {
    getSwift().objects().delete(containerName, objectKey);
  }

  @Override
  public <T extends Timestamped> void storeObject(ObjectType objectType, String objectKey, T item) {
    try {
      byte[] bytes = new ObjectMapper().writeValueAsBytes(item);
      InputStream is = new ByteArrayInputStream(bytes);

      getSwift()
          .objects()
          .put(
              containerName,
              objectKey,
              Payloads.create(is),
              ObjectPutOptions.create().path(objectType.group));
    } catch (IOException e) {
      log.error("failed to write object={}: {}", value("key", objectKey), e);
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Map<String, Long> listObjectKeys(ObjectType objectType) {
    Map<String, Long> result = new HashMap<String, Long>();
    List<? extends SwiftObject> objects =
        getSwift().objects().list(containerName, ObjectListOptions.create().path(objectType.group));
    for (SwiftObject o : objects) {
      Long timestamp =
          Long.parseLong(
              getSwift().objects().getMetadata(containerName, o.getName()).get("X-Timestamp"));
      result.put(o.getName(), timestamp);
    }
    return result;
  }

  // TODO: getting previous versions is not yet supported in Openstack4j
  // https://github.com/ContainX/openstack4j/issues/970 created to track this issue
  @Override
  public <T extends Timestamped> Collection<T> listObjectVersions(
      ObjectType objectType, String objectKey, int maxResults) throws NotFoundException {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public long getLastModified(ObjectType objectType) {
    List<? extends SwiftObject> objects =
        getSwift().objects().list(containerName, ObjectListOptions.create().path(objectType.group));
    ZonedDateTime lastModified = Instant.now().atZone(ZoneOffset.UTC);
    for (SwiftObject o : objects) {
      ZonedDateTime timestamp =
          ZonedDateTime.parse(
              getSwift().objects().getMetadata(containerName, o.getName()).get("Last-Modified"),
              DateTimeFormatter.RFC_1123_DATE_TIME);
      if (timestamp.isBefore(lastModified)) {
        lastModified = timestamp;
      }
    }
    return lastModified.toEpochSecond();
  }

  private <T extends Timestamped> T deserialize(SwiftObject object, Class<T> clazz) {
    try {
      T item = objectMapper.readValue(object.download().getInputStream(), clazz);
      item.setLastModified(object.getLastModified().getTime());
      return item;
    } catch (Exception e) {
      log.error("Error reading {}: {}", value("key", object.getName()), e);
    }
    return null;
  }
}
