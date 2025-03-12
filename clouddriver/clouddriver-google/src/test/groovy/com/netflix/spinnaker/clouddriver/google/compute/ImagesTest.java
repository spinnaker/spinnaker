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
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Image;
import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Timer;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import org.junit.jupiter.api.Test;

final class ImagesTest {

  private static final int CLOCK_STEP_TIME_MS = 1234;
  private static final int CLOCK_STEP_TIME_NS = 1234 * 1000000;

  @Test
  void get_success() throws IOException {

    HttpTransport transport =
        new ComputeOperationMockHttpTransport(
            new MockLowLevelHttpResponse()
                .setStatusCode(200)
                .setContent("{ \"name\": \"my-wacky-image-name\" }"));

    Images imagesApi = createImages(transport);

    Image image = imagesApi.get("my-project", "my-image").execute();

    assertThat(image.getName()).isEqualTo("my-wacky-image-name");
  }

  @Test
  public void get_error() {

    HttpTransport transport =
        new ComputeOperationMockHttpTransport(
            new MockLowLevelHttpResponse().setStatusCode(404).setContent("{}"));

    Images imagesApi = createImages(transport);

    assertThatIOException()
        .isThrownBy(() -> imagesApi.get("my-project", "my-image").execute())
        .withMessageContaining("404");
  }

  @Test
  public void get_successMetrics() throws IOException {

    Registry registry = new DefaultRegistry(new SteppingClock(CLOCK_STEP_TIME_MS));
    HttpTransport transport =
        new ComputeOperationMockHttpTransport(
            new MockLowLevelHttpResponse().setStatusCode(200).setContent("{\"items\": []}"));

    Images imagesApi = createImages(transport, registry);

    imagesApi.get("my-project", "my-image").execute();

    assertThat(registry.timers().count()).isEqualTo(1);
    Timer timer = registry.timers().findFirst().orElseThrow(AssertionError::new);
    assertThat(timer.id().name()).isEqualTo("google.api");
    // TODO(plumpy): Come up with something better than AccountForClient (which uses a bunch of
    //               global state) so that we can test for the account tags
    assertThat(timer.id().tags())
        .contains(
            tag("api", "compute.images.get"),
            tag("scope", "global"),
            tag("status", "2xx"),
            tag("success", "true"));
    assertThat(timer.totalTime()).isEqualTo(CLOCK_STEP_TIME_NS);
  }

  @Test
  public void get_errorMetrics() {

    Registry registry = new DefaultRegistry(new SteppingClock(CLOCK_STEP_TIME_MS));
    HttpTransport transport =
        new ComputeOperationMockHttpTransport(
            new MockLowLevelHttpResponse().setStatusCode(404).setContent("{}"));

    Images imagesApi = createImages(transport, registry);

    assertThatIOException().isThrownBy(() -> imagesApi.get("my-project", "my-image").execute());

    assertThat(registry.timers().count()).isEqualTo(1);
    Timer timer = registry.timers().findFirst().orElseThrow(AssertionError::new);
    assertThat(timer.id().name()).isEqualTo("google.api");
    // TODO(plumpy): Come up with something better than AccountForClient (which uses a bunch of
    //               global state) so that we can test for the account tags
    assertThat(timer.id().tags())
        .contains(
            tag("api", "compute.images.get"),
            tag("scope", "global"),
            tag("status", "4xx"),
            tag("success", "false"));
    assertThat(timer.totalTime()).isEqualTo(CLOCK_STEP_TIME_NS);
  }

  private static Images createImages(HttpTransport transport) {
    return createImages(transport, new NoopRegistry());
  }

  private static Images createImages(HttpTransport transport, Registry registry) {
    Compute compute =
        new Compute(
            transport, GsonFactory.getDefaultInstance(), /* httpRequestInitializer= */ null);
    GoogleNamedAccountCredentials credentials =
        new GoogleNamedAccountCredentials.Builder()
            .name("plumpy")
            .project("myproject")
            .credentials(new FakeGoogleCredentials())
            .compute(compute)
            .build();
    return new Images(credentials, new GoogleOperationPoller(), registry);
  }

  private static Tag tag(String key, String value) {
    return new BasicTag(key, value);
  }
}
