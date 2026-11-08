/*
 * Copyright 2026 spinnaker.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.cloudrun.deploy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.run.v1.CloudRun;
import com.google.api.services.run.v1.model.GoogleCloudRunV1Condition;
import com.google.api.services.run.v1.model.ObjectMeta;
import com.google.api.services.run.v1.model.Service;
import com.google.api.services.run.v1.model.ServiceStatus;
import com.google.api.services.run.v1.model.TrafficTarget;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies changes to Cloud Run services through the Cloud Run Admin API, using the credentials of
 * the Spinnaker account. This replaces shelling out to {@code gcloud run ...}, which relied on
 * credentials activated in a gcloud configuration shared by every account.
 */
public class CloudrunServiceClient {

  private static final Logger log = LoggerFactory.getLogger(CloudrunServiceClient.class);

  public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(2);

  public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

  private static final String READY_CONDITION = "Ready";

  /** Allows tests to run the wait loop without sleeping. */
  @FunctionalInterface
  public interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;
  }

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final CloudRun cloudRun;
  private final String project;
  private final Duration pollInterval;
  private final int maxPolls;
  private final Sleeper sleeper;

  public CloudrunServiceClient(
      CloudRun cloudRun, String project, Duration pollInterval, Duration timeout, Sleeper sleeper) {
    this.cloudRun = cloudRun;
    this.project = project;
    this.pollInterval = pollInterval;
    this.maxPolls = (int) Math.max(1, timeout.toMillis() / Math.max(1, pollInterval.toMillis()));
    this.sleeper = sleeper;
  }

  public static CloudrunServiceClient forAccount(CloudrunNamedAccountCredentials credentials) {
    return new CloudrunServiceClient(
        credentials.getCloudRun(),
        credentials.getProject(),
        DEFAULT_POLL_INTERVAL,
        DEFAULT_TIMEOUT,
        duration -> Thread.sleep(duration.toMillis()));
  }

  /**
   * Creates the service described by {@code manifest}, or replaces it if it already exists (the
   * behavior of {@code gcloud run services replace}), then waits for the new revision to be ready.
   * Fields that are not part of the typed API model are preserved.
   */
  public void deployService(String region, Map<String, Object> manifest) throws IOException {
    Service service = toService(manifest);
    ObjectMeta metadata = service.getMetadata();
    if (metadata == null || isEmpty(metadata.getName())) {
      throw new CloudrunOperationException("The manifest must have a metadata.name");
    }
    // The API resolves the service from the account's project in the request path, so a
    // namespace naming any other project must never be accepted from a manifest.
    if (!isEmpty(metadata.getNamespace()) && !project.equals(metadata.getNamespace())) {
      throw new CloudrunOperationException(
          String.format(
              "metadata.namespace '%s' in the manifest does not match the project '%s' of the account",
              metadata.getNamespace(), project));
    }
    metadata.setNamespace(project);

    String parent = parent(region);
    String name = serviceName(region, metadata.getName());
    Service existing = getServiceOrNull(name);
    if (existing == null) {
      log.info("Creating Cloud Run service {}", name);
      cloudRun.projects().locations().services().create(parent, service).execute();
    } else {
      if (isEmpty(metadata.getResourceVersion()) && existing.getMetadata() != null) {
        metadata.setResourceVersion(existing.getMetadata().getResourceVersion());
      }
      log.info("Replacing Cloud Run service {}", name);
      cloudRun.projects().locations().services().replaceService(name, service).execute();
    }
    awaitReady(name);
  }

  /** Replaces the traffic allocation of an existing service, then waits for it to be ready. */
  public void updateTraffic(String region, String serviceName, List<TrafficTarget> traffic)
      throws IOException {
    String name = serviceName(region, serviceName);
    Service service = getServiceOrNull(name);
    if (service == null) {
      throw new CloudrunOperationException("Cloud Run service " + name + " does not exist");
    }
    service.getSpec().setTraffic(traffic);
    // status is output only
    service.setStatus(null);
    cloudRun.projects().locations().services().replaceService(name, service).execute();
    awaitReady(name);
  }

  public void deleteService(String region, String serviceName) throws IOException {
    cloudRun.projects().locations().services().delete(serviceName(region, serviceName)).execute();
  }

  public void deleteRevision(String region, String revisionName) throws IOException {
    cloudRun
        .projects()
        .locations()
        .revisions()
        .delete(parent(region) + "/revisions/" + revisionName)
        .execute();
  }

  private Service toService(Map<String, Object> manifest) {
    Map<String, Object> withoutStatus = new LinkedHashMap<>(manifest);
    // status is output only, so it is intentionally not sent
    withoutStatus.remove("status");
    try {
      return GsonFactory.getDefaultInstance()
          .fromString(objectMapper.writeValueAsString(withoutStatus), Service.class);
    } catch (IOException | RuntimeException e) {
      throw new CloudrunOperationException(
          "The manifest is not a valid Cloud Run service: " + e.getMessage(), e);
    }
  }

  private Service getServiceOrNull(String name) throws IOException {
    try {
      return cloudRun.projects().locations().services().get(name).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() == 404) {
        return null;
      }
      throw e;
    }
  }

  private void awaitReady(String name) throws IOException {
    for (int poll = 0; poll < maxPolls; poll++) {
      Service service = cloudRun.projects().locations().services().get(name).execute();
      ServiceStatus status = service.getStatus();
      if (status != null && observed(service, status)) {
        GoogleCloudRunV1Condition ready = readyCondition(status);
        if (ready != null && "True".equals(ready.getStatus())) {
          return;
        }
        if (ready != null && "False".equals(ready.getStatus())) {
          throw new CloudrunOperationException(
              String.format(
                  "Cloud Run service %s failed to become ready: %s %s",
                  name, ready.getReason(), ready.getMessage()));
        }
      }
      try {
        sleeper.sleep(pollInterval);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new CloudrunOperationException("Interrupted while waiting for " + name, e);
      }
    }
    throw new CloudrunOperationException(
        "Timed out waiting for Cloud Run service " + name + " to become ready");
  }

  /** The Ready condition is only meaningful once the controller has seen the latest generation. */
  private static boolean observed(Service service, ServiceStatus status) {
    Integer generation =
        service.getMetadata() == null ? null : service.getMetadata().getGeneration();
    Integer observed = status.getObservedGeneration();
    return generation == null || (observed != null && observed >= generation);
  }

  private static GoogleCloudRunV1Condition readyCondition(ServiceStatus status) {
    if (status.getConditions() == null) {
      return null;
    }
    return status.getConditions().stream()
        .filter(condition -> READY_CONDITION.equals(condition.getType()))
        .findFirst()
        .orElse(null);
  }

  private String parent(String region) {
    if (isEmpty(region)) {
      throw new CloudrunOperationException("A region is required to talk to Cloud Run");
    }
    return "projects/" + project + "/locations/" + region;
  }

  private String serviceName(String region, String service) {
    return parent(region) + "/services/" + service;
  }

  private static boolean isEmpty(String value) {
    return value == null || value.isEmpty();
  }
}
