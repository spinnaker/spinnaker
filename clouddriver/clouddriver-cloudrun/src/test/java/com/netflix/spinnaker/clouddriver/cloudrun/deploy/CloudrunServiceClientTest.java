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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.run.v1.CloudRun;
import com.google.api.services.run.v1.model.GoogleCloudRunV1Condition;
import com.google.api.services.run.v1.model.ObjectMeta;
import com.google.api.services.run.v1.model.Service;
import com.google.api.services.run.v1.model.ServiceSpec;
import com.google.api.services.run.v1.model.ServiceStatus;
import com.google.api.services.run.v1.model.TrafficTarget;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CloudrunServiceClientTest {

  private static final String PARENT = "projects/my-project/locations/europe-west2";
  private static final String SERVICE = PARENT + "/services/myapp";

  private static final ObjectMapper JSON = new ObjectMapper();

  private CloudRun cloudRun;
  private CloudRun.Projects.Locations.Services services;
  private CloudrunServiceClient client;
  private int sleeps;

  @BeforeEach
  void setUp() {
    cloudRun = mock(CloudRun.class, RETURNS_DEEP_STUBS);
    services = cloudRun.projects().locations().services();
    sleeps = 0;
    client =
        new CloudrunServiceClient(
            cloudRun,
            "my-project",
            Duration.ofSeconds(1),
            Duration.ofSeconds(3),
            duration -> sleeps++);
  }

  @Test
  void createsTheServiceWhenItDoesNotExistAndWaitsUntilReady() throws IOException {
    when(services.get(SERVICE).execute()).thenThrow(notFound()).thenReturn(ready(1, 1, "True"));

    client.deployService("europe-west2", manifest("myapp"));

    ArgumentCaptor<Service> created = ArgumentCaptor.forClass(Service.class);
    verify(services).create(eq(PARENT), created.capture());
    assertThat(created.getValue().getMetadata().getName()).isEqualTo("myapp");
    verify(services, never()).replaceService(any(), any());
  }

  @Test
  void replacesAnExistingServiceUsingItsResourceVersion() throws IOException {
    Service existing = ready(3, 3, "True");
    existing.getMetadata().setResourceVersion("abc");
    when(services.get(SERVICE).execute()).thenReturn(existing);

    client.deployService("europe-west2", manifest("myapp"));

    ArgumentCaptor<Service> replaced = ArgumentCaptor.forClass(Service.class);
    verify(services).replaceService(eq(SERVICE), replaced.capture());
    assertThat(replaced.getValue().getMetadata().getResourceVersion()).isEqualTo("abc");
    verify(services, never()).create(any(), any());
  }

  @Test
  void sendsFieldsThatAreNotPartOfTheTypedModel() throws IOException {
    when(services.get(SERVICE).execute()).thenThrow(notFound()).thenReturn(ready(1, 1, "True"));
    Map<String, Object> manifest =
        parse(
            "{\"apiVersion\":\"serving.knative.dev/v1\",\"kind\":\"Service\","
                + "\"metadata\":{\"name\":\"myapp\"},"
                + "\"spec\":{\"template\":{\"spec\":{\"containers\":[{\"image\":\"example\","
                + "\"env\":[{\"name\":\"environment\",\"value\":\"test-utils\"}],"
                + "\"startupProbe\":{\"tcpSocket\":{\"port\":8182}},"
                + "\"someFutureField\":\"kept\"}]}}}}");

    client.deployService("europe-west2", manifest);

    ArgumentCaptor<Service> created = ArgumentCaptor.forClass(Service.class);
    verify(services).create(eq(PARENT), created.capture());
    String sent = GsonFactory.getDefaultInstance().toString(created.getValue());
    assertThat(sent)
        .contains("\"env\"", "\"test-utils\"", "\"startupProbe\"", "8182")
        .contains("\"someFutureField\":\"kept\"");
  }

  @Test
  void setsTheNamespaceToTheAccountsProject() throws IOException {
    when(services.get(SERVICE).execute()).thenThrow(notFound()).thenReturn(ready(1, 1, "True"));

    client.deployService("europe-west2", manifest("myapp"));

    ArgumentCaptor<Service> created = ArgumentCaptor.forClass(Service.class);
    verify(services).create(eq(PARENT), created.capture());
    assertThat(created.getValue().getMetadata().getNamespace()).isEqualTo("my-project");
  }

  @Test
  void rejectsAManifestNamespaceThatNamesAnotherProject() throws IOException {
    Map<String, Object> manifest =
        parse(
            "{\"apiVersion\":\"serving.knative.dev/v1\",\"kind\":\"Service\","
                + "\"metadata\":{\"name\":\"myapp\",\"namespace\":\"someone-elses-project\"},"
                + "\"spec\":{}}");

    assertThatThrownBy(() -> client.deployService("europe-west2", manifest))
        .isInstanceOf(CloudrunOperationException.class)
        .hasMessageContaining("someone-elses-project")
        .hasMessageContaining("my-project");
    verify(services, never()).create(any(), any());
    verify(services, never()).replaceService(any(), any());
  }

  @Test
  void waitsForTheControllerToObserveTheLatestGeneration() throws IOException {
    when(services.get(SERVICE).execute())
        .thenThrow(notFound())
        // stale Ready=True for the previous generation, then the new generation is observed
        .thenReturn(ready(2, 1, "True"))
        .thenReturn(ready(2, 2, "True"));

    client.deployService("europe-west2", manifest("myapp"));

    assertThat(sleeps).isEqualTo(1);
  }

  @Test
  void failsWithTheReasonWhenTheServiceDoesNotBecomeReady() throws IOException {
    Service failed = ready(1, 1, "False");
    failed.getStatus().getConditions().get(0).setReason("HealthCheckContainerError");
    failed.getStatus().getConditions().get(0).setMessage("startup probe failed");
    when(services.get(SERVICE).execute()).thenThrow(notFound()).thenReturn(failed);

    assertThatThrownBy(() -> client.deployService("europe-west2", manifest("myapp")))
        .isInstanceOf(CloudrunOperationException.class)
        .hasMessageContaining("HealthCheckContainerError")
        .hasMessageContaining("startup probe failed");
  }

  @Test
  void timesOutWhenTheServiceNeverBecomesReady() throws IOException {
    when(services.get(SERVICE).execute()).thenThrow(notFound()).thenReturn(ready(1, 1, "Unknown"));

    assertThatThrownBy(() -> client.deployService("europe-west2", manifest("myapp")))
        .isInstanceOf(CloudrunOperationException.class)
        .hasMessageContaining("Timed out");
    assertThat(sleeps).isEqualTo(3);
  }

  @Test
  void updatesTrafficOnAnExistingService() throws IOException {
    Service existing = ready(1, 1, "True");
    existing.setSpec(new ServiceSpec());
    when(services.get(SERVICE).execute()).thenReturn(existing).thenReturn(ready(2, 2, "True"));
    TrafficTarget target = new TrafficTarget().setRevisionName("myapp-v001").setPercent(100);

    client.updateTraffic("europe-west2", "myapp", List.of(target));

    ArgumentCaptor<Service> replaced = ArgumentCaptor.forClass(Service.class);
    verify(services).replaceService(eq(SERVICE), replaced.capture());
    assertThat(replaced.getValue().getSpec().getTraffic()).containsExactly(target);
    assertThat(replaced.getValue().getStatus()).isNull();
  }

  @Test
  void failsToUpdateTrafficOfAServiceThatDoesNotExist() throws IOException {
    when(services.get(SERVICE).execute()).thenThrow(notFound());

    assertThatThrownBy(() -> client.updateTraffic("europe-west2", "myapp", List.of()))
        .isInstanceOf(CloudrunOperationException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  void deletesAServiceInTheAccountsProject() throws IOException {
    client.deleteService("europe-west2", "myapp");

    verify(services).delete(SERVICE);
  }

  @Test
  void deletesARevisionInTheAccountsProject() throws IOException {
    client.deleteRevision("europe-west2", "myapp-v001");

    verify(cloudRun.projects().locations().revisions()).delete(PARENT + "/revisions/myapp-v001");
  }

  @Test
  void requiresARegion() {
    assertThatThrownBy(() -> client.deleteService(null, "myapp"))
        .isInstanceOf(CloudrunOperationException.class)
        .hasMessageContaining("region");
  }

  private static Map<String, Object> manifest(String name) {
    return parse(
        "{\"apiVersion\":\"serving.knative.dev/v1\",\"kind\":\"Service\","
            + "\"metadata\":{\"name\":\""
            + name
            + "\"},\"spec\":{\"template\":{\"spec\":{\"containers\":[{\"image\":\"example\"}]}}}}");
  }

  private static Map<String, Object> parse(String json) {
    try {
      return JSON.readValue(json, Map.class);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Service ready(int generation, int observedGeneration, String readyStatus) {
    return new Service()
        .setMetadata(new ObjectMeta().setGeneration(generation))
        .setStatus(
            new ServiceStatus()
                .setObservedGeneration(observedGeneration)
                .setConditions(
                    List.of(
                        new GoogleCloudRunV1Condition().setType("Ready").setStatus(readyStatus))));
  }

  private static GoogleJsonResponseException notFound() {
    return new GoogleJsonResponseException(
        new HttpResponseException.Builder(404, "Not Found", new HttpHeaders()), null);
  }
}
