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
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.ImageList;
import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Timer;
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

public class ImagesTest {

  private static final int CLOCK_STEP_TIME_MS = 1234;
  private static final int CLOCK_STEP_TIME_NS = 1234 * 1000000;

  @Test
  public void list_success() throws IOException {

    HttpTransport transport =
        new ComputeOperationMockHttpTransport(
            new MockLowLevelHttpResponse()
                .setStatusCode(200)
                .setContent(
                    ""
                        + "{"
                        + "  \"items\": ["
                        + "    { \"name\": \"centos\" },"
                        + "    { \"name\": \"ubuntu\" }"
                        + "  ]"
                        + "}"));

    Images imagesApi = createImages(transport);

    ImageList imageList = imagesApi.list("my-project").execute();

    List<Image> images = imageList.getItems();
    assertThat(images).hasSize(2);
    assertThat(images.get(0).getName()).isEqualTo("centos");
    assertThat(images.get(1).getName()).isEqualTo("ubuntu");
  }

  @Test
  public void list_error() {

    HttpTransport transport =
        new ComputeOperationMockHttpTransport(
            new MockLowLevelHttpResponse().setStatusCode(404).setContent("{}"));

    Images imagesApi = createImages(transport);

    assertThatIOException()
        .isThrownBy(() -> imagesApi.list("my-project").execute())
        .withMessageContaining("404");
  }

  @Test
  public void list_successMetrics() throws IOException {

    Registry registry = new DefaultRegistry(new SteppingClock(CLOCK_STEP_TIME_MS));
    HttpTransport transport =
        new ComputeOperationMockHttpTransport(
            new MockLowLevelHttpResponse().setStatusCode(200).setContent("{\"items\": []}"));

    Images imagesApi = createImages(transport, registry);

    imagesApi.list("my-project").execute();

    assertThat(registry.timers().count()).isEqualTo(1);
    Timer timer = registry.timers().findFirst().orElseThrow(AssertionError::new);
    assertThat(timer.id().name()).isEqualTo("google.api");
    // TODO(plumpy): Come up with something better than AccountForClient (which uses a bunch of
    //               global state) so that we can test for the account tags
    assertThat(timer.id().tags())
        .contains(
            tag("api", "compute.images.list"),
            tag("scope", "global"),
            tag("status", "2xx"),
            tag("success", "true"));
    assertThat(timer.totalTime()).isEqualTo(CLOCK_STEP_TIME_NS);
  }

  @Test
  public void list_errorMetrics() {

    Registry registry = new DefaultRegistry(new SteppingClock(CLOCK_STEP_TIME_MS));
    HttpTransport transport =
        new ComputeOperationMockHttpTransport(
            new MockLowLevelHttpResponse().setStatusCode(404).setContent("{}"));

    Images imagesApi = createImages(transport, registry);

    assertThatIOException().isThrownBy(() -> imagesApi.list("my-project").execute());

    assertThat(registry.timers().count()).isEqualTo(1);
    Timer timer = registry.timers().findFirst().orElseThrow(AssertionError::new);
    assertThat(timer.id().name()).isEqualTo("google.api");
    // TODO(plumpy): Come up with something better than AccountForClient (which uses a bunch of
    //               global state) so that we can test for the account tags
    assertThat(timer.id().tags())
        .contains(
            tag("api", "compute.images.list"),
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
            transport, JacksonFactory.getDefaultInstance(), /* httpRequestInitializer= */ null);
    GoogleNamedAccountCredentials credentials =
        new GoogleNamedAccountCredentials.Builder()
            .name("plumpy")
            .project("myproject")
            .credentials(new FakeGoogleCredentials())
            .compute(compute)
            .build();
    return new Images(credentials, registry);
  }

  private static Tag tag(String key, String value) {
    return new BasicTag(key, value);
  }
}
