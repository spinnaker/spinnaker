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
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Timer;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry;
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RegionGoogleServerGroupManagersTest {

  private static final String REGION = "us-central1";
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
                        + "  \"name\":   \"xyzzy\","
                        + "  \"region\": \"http://compute/regions/us-central1\""
                        + "}"));

    RegionGoogleServerGroupManagers managers = createManagers(transport);

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

    RegionGoogleServerGroupManagers managers = createManagers(transport);

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
                        + "  \"name\":   \"xyzzy\","
                        + "  \"region\": \"http://compute/regions/us-central1\""
                        + "}"));

    RegionGoogleServerGroupManagers managers = createManagers(transport);

    managers.delete().executeAndWait(new DefaultTask("task"), "phase");
  }

  @Test
  public void delete_failure() {

    HttpTransport transport =
        new MockHttpTransport.Builder()
            .setLowLevelHttpResponse(
                new MockLowLevelHttpResponse().setStatusCode(404).setContent("{}"))
            .build();

    RegionGoogleServerGroupManagers managers = createManagers(transport);

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
    RegionGoogleServerGroupManagers managers = createManagers(transport);

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
    RegionGoogleServerGroupManagers managers = createManagers(transport);

    assertThatIOException().isThrownBy(() -> managers.get().execute());
  }

  @Test
  public void get_successMetrics() throws IOException {

    Registry registry = new DefaultRegistry(new SteppingClock(CLOCK_STEP_TIME_MS));
    MockHttpTransport transport =
        new MockHttpTransport.Builder()
            .setLowLevelHttpResponse(
                new MockLowLevelHttpResponse()
                    .setStatusCode(200)
                    .setContent("{\"name\": \"myServerGroup\"}"))
            .build();
    RegionGoogleServerGroupManagers managers = createManagers(transport, registry);

    managers.get().execute();

    assertThat(registry.timers().count()).isEqualTo(1);
    Timer timer = registry.timers().findFirst().orElseThrow(AssertionError::new);
    assertThat(timer.id().name()).isEqualTo("google.api");
    // TODO(plumpy): Come up with something better than AccountForClient (which uses a bunch of
    //               global state) so that we can test for the account tags
    assertThat(timer.id().tags())
        .contains(
            tag("api", "compute.regionInstanceGroupManagers.get"),
            tag("scope", "regional"),
            tag("region", REGION),
            tag("status", "2xx"),
            tag("success", "true"));
    assertThat(timer.totalTime()).isEqualTo(CLOCK_STEP_TIME_NS);
  }

  @Test
  public void get_errorMetrics() {

    Registry registry = new DefaultRegistry(new SteppingClock(CLOCK_STEP_TIME_MS));
    MockHttpTransport transport =
        new MockHttpTransport.Builder()
            .setLowLevelHttpResponse(
                new MockLowLevelHttpResponse().setStatusCode(404).setContent("{}"))
            .build();
    RegionGoogleServerGroupManagers managers = createManagers(transport, registry);

    try {
      managers.get().execute();
    } catch (IOException expected) {
    }

    assertThat(registry.timers().count()).isEqualTo(1);
    Timer timer = registry.timers().findFirst().orElseThrow(AssertionError::new);
    assertThat(timer.id().name()).isEqualTo("google.api");
    assertThat(timer.id().tags())
        .contains(
            tag("api", "compute.regionInstanceGroupManagers.get"),
            tag("scope", "regional"),
            tag("region", REGION),
            tag("status", "4xx"),
            tag("success", "false"));
    assertThat(timer.totalTime()).isEqualTo(CLOCK_STEP_TIME_NS);
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
                        + "  \"name\":   \"xyzzy\","
                        + "  \"region\": \"http://compute/regions/us-central1\""
                        + "}"));

    RegionGoogleServerGroupManagers managers = createManagers(transport);

    managers.update(new InstanceGroupManager()).executeAndWait(new DefaultTask("task"), "phase");
  }

  @Test
  public void update_failure() {

    HttpTransport transport =
        new MockHttpTransport.Builder()
            .setLowLevelHttpResponse(
                new MockLowLevelHttpResponse().setStatusCode(404).setContent("{}"))
            .build();

    RegionGoogleServerGroupManagers managers = createManagers(transport);

    assertThatIOException().isThrownBy(() -> managers.update(new InstanceGroupManager()).execute());
  }

  private static RegionGoogleServerGroupManagers createManagers(HttpTransport transport) {
    return createManagers(transport, new NoopRegistry());
  }

  private static RegionGoogleServerGroupManagers createManagers(
      HttpTransport transport, Registry registry) {
    Compute compute =
        new Compute(
            transport, JacksonFactory.getDefaultInstance(), /* httpRequestInitializer= */ null);
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
    return new RegionGoogleServerGroupManagers(
        credentials, poller, registry, "myInstanceGroup", REGION);
  }

  private static Tag tag(String key, String value) {
    return new BasicTag(key, value);
  }
}
