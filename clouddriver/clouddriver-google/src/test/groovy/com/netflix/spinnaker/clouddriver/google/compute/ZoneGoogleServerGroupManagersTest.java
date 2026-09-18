/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry;
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class ZoneGoogleServerGroupManagersTest {

  private static final String ZONE = "us-central1-f";
  private static final int CLOCK_STEP_TIME_MS = 1234;
  private static final int CLOCK_STEP_TIME_NS = 1234 * 1000000;

  @Test
  public void abandonInstances_success() throws IOException {

    HttpTransport transport =
        new ComputeOperationMockHttpTransport(
            new MockLowLevelHttpResponse()
                .setStatusCode(200)
                .setContent(
                    ""
                        + "{"
                        + "  \"name\": \"xyzzy\","
                        + "  \"zone\": \"http://compute/zones/us-central1-f\""
                        + "}"));

    ZoneGoogleServerGroupManagers managers = createManagers(transport);

    managers
        .abandonInstances(ImmutableList.of("myServerGroup"))
        .executeAndWait(new DefaultTask("task"), "phase");
  }

  @Test
  public void abandonInstances_failure() {

    HttpTransport transport =
        new MockHttpTransport.Builder()
            .setLowLevelHttpResponse(
                new MockLowLevelHttpResponse().setStatusCode(404).setContent("{}"))
            .build();

    ZoneGoogleServerGroupManagers managers = createManagers(transport);

    assertThatIOException()
        .isThrownBy(() -> managers.abandonInstances(ImmutableList.of("myServerGroup")).execute());
  }

  @Test
  public void delete_success() throws IOException {

    HttpTransport transport =
        new ComputeOperationMockHttpTransport(
            new MockLowLevelHttpResponse()
                .setStatusCode(200)
                .setContent(
                    ""
                        + "{"
                        + "  \"name\": \"xyzzy\","
                        + "  \"zone\": \"http://compute/zones/us-central1-f\""
                        + "}"));

    ZoneGoogleServerGroupManagers managers = createManagers(transport);

    managers.delete().executeAndWait(new DefaultTask("task"), "phase");
  }

  @Test
  public void delete_failure() {

    HttpTransport transport =
        new MockHttpTransport.Builder()
            .setLowLevelHttpResponse(
                new MockLowLevelHttpResponse().setStatusCode(404).setContent("{}"))
            .build();

    ZoneGoogleServerGroupManagers managers = createManagers(transport);

    assertThatIOException().isThrownBy(() -> managers.delete().execute());
  }

  @Test
  public void get_success() throws IOException {

    MockHttpTransport transport =
        new MockHttpTransport.Builder()
            .setLowLevelHttpResponse(
                new MockLowLevelHttpResponse()
                    .setStatusCode(200)
                    .setContent("{\"name\": \"myServerGroup\"}"))
            .build();
    ZoneGoogleServerGroupManagers managers = createManagers(transport);

    InstanceGroupManager manager = managers.get().execute();

    assertThat(manager.getName()).isEqualTo("myServerGroup");
  }

  @Test
  public void get_error() {

    MockHttpTransport transport =
        new MockHttpTransport.Builder()
            .setLowLevelHttpResponse(
                new MockLowLevelHttpResponse().setStatusCode(404).setContent("{}"))
            .build();
    ZoneGoogleServerGroupManagers managers = createManagers(transport);

    assertThatIOException().isThrownBy(() -> managers.get().execute());
  }

  @Test
  public void get_successMetrics() throws IOException {

    MeterRegistry registry =
        new SimpleMeterRegistry(SimpleConfig.DEFAULT, new SteppingClock(CLOCK_STEP_TIME_MS));
    MockHttpTransport transport =
        new MockHttpTransport.Builder()
            .setLowLevelHttpResponse(
                new MockLowLevelHttpResponse()
                    .setStatusCode(200)
                    .setContent("{\"name\": \"myServerGroup\"}"))
            .build();
    ZoneGoogleServerGroupManagers managers = createManagers(transport, registry);

    managers.get().execute();

    assertThat(registry.getMeters()).hasSize(1);
    Timer timer = (Timer) registry.getMeters().get(0);
    assertThat(timer.getId().getName()).isEqualTo("google.api");
    // TODO(plumpy): Come up with something better than AccountForClient (which uses a bunch of
    //               global state) so that we can test for the account tags
    assertThat(timer.getId().getTags())
        .contains(
            tag("api", "compute.instanceGroupManagers.get"),
            tag("scope", "zonal"),
            tag("zone", ZONE),
            tag("status", "2xx"),
            tag("success", "true"));
    assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo((double) CLOCK_STEP_TIME_NS);
  }

  @Test
  public void get_errorMetrics() {

    MeterRegistry registry =
        new SimpleMeterRegistry(SimpleConfig.DEFAULT, new SteppingClock(CLOCK_STEP_TIME_MS));
    MockHttpTransport transport =
        new MockHttpTransport.Builder()
            .setLowLevelHttpResponse(
                new MockLowLevelHttpResponse().setStatusCode(404).setContent("{}"))
            .build();
    ZoneGoogleServerGroupManagers managers = createManagers(transport, registry);

    try {
      managers.get().execute();
    } catch (IOException expected) {
    }

    assertThat(registry.getMeters()).hasSize(1);
    Timer timer = (Timer) registry.getMeters().get(0);
    assertThat(timer.getId().getName()).isEqualTo("google.api");
    assertThat(timer.getId().getTags())
        .contains(
            tag("api", "compute.instanceGroupManagers.get"),
            tag("scope", "zonal"),
            tag("zone", ZONE),
            tag("status", "4xx"),
            tag("success", "false"));
    assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo((double) CLOCK_STEP_TIME_NS);
  }

  @Test
  public void update_success() throws IOException {

    HttpTransport transport =
        new ComputeOperationMockHttpTransport(
            new MockLowLevelHttpResponse()
                .setStatusCode(200)
                .setContent(
                    ""
                        + "{"
                        + "  \"name\": \"xyzzy\","
                        + "  \"zone\": \"http://compute/zones/us-central1-f\""
                        + "}"));

    ZoneGoogleServerGroupManagers managers = createManagers(transport);

    managers.update(new InstanceGroupManager()).executeAndWait(new DefaultTask("task"), "phase");
  }

  @Test
  public void update_failure() {

    HttpTransport transport =
        new MockHttpTransport.Builder()
            .setLowLevelHttpResponse(
                new MockLowLevelHttpResponse().setStatusCode(404).setContent("{}"))
            .build();

    ZoneGoogleServerGroupManagers managers = createManagers(transport);

    assertThatIOException().isThrownBy(() -> managers.update(new InstanceGroupManager()).execute());
  }

  private static ZoneGoogleServerGroupManagers createManagers(HttpTransport transport) {
    return createManagers(transport, new SimpleMeterRegistry());
  }

  private static ZoneGoogleServerGroupManagers createManagers(
      HttpTransport transport, MeterRegistry registry) {
    Compute compute =
        new Compute(
            transport, GsonFactory.getDefaultInstance(), /* httpRequestInitializer= */ null);
    GoogleNamedAccountCredentials credentials =
        new GoogleNamedAccountCredentials.Builder()
            .name("spin-user")
            .project("myproject")
            .credentials(new FakeGoogleCredentials())
            .compute(compute)
            .build();
    GoogleOperationPoller poller = new GoogleOperationPoller();
    poller.setGoogleConfigurationProperties(new GoogleConfigurationProperties());
    poller.setRegistry(registry);
    SafeRetry safeRetry = SafeRetry.withoutDelay();
    poller.setSafeRetry(safeRetry);
    return new ZoneGoogleServerGroupManagers(
        credentials, poller, registry, "myInstanceGroup", ZONE);
  }

  private static Tag tag(String key, String value) {
    return Tag.of(key, value);
  }
}
