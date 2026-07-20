/*
 * Copyright 2025 Wise, PLC.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.cats.agent.NoOpStartupConcurrencyControl;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KubernetesStreamingWatcherIntTest extends BaseKubernetesStreamingCachingAgentIntTest {

  private BlockingQueue<KubernetesStreamingEvent> eventQueue;
  private State state;

  @BeforeEach
  void setUp() throws IOException {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    eventQueue = new ArrayBlockingQueue<>(100);

    ApiClient apiClient = Config.fromConfig(kubeconfigFile);
    apiClient.setReadTimeout(100_000);

    KubernetesStreamingWatcherFactory factory =
        new KubernetesStreamingWatcherFactory(
            apiClient, "account", 0, executor, new NoOpStartupConcurrencyControl());

    state = new State("test-account", executor, factory, apiClient.getHttpClient());
  }

  @Test
  void testInterruptDuringSlowListRequest() throws Exception {
    // Mock a very slow list request (60+ seconds delay)
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v1/pods"))
            .withQueryParam("watch", or(equalTo("false"), absent()))
            .willReturn(
                aResponse()
                    .withFixedDelay(60_000)
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("kubeapi/pods.default.json")));

    CountDownLatch requestReceivedLatch = new CountDownLatch(1);

    // Add a request listener to know when the request is received
    wireMockServer.addMockServiceRequestListener(
        (request, response) -> {
          if (request.getUrl().contains("/api/v1/pods")
              && !request.getUrl().contains("watch=true")) {
            requestReceivedLatch.countDown();
          }
        });

    state
        .getFactory()
        .watcherFor(
            DynamicKubernetesObject.class,
            "pod",
            "",
            "v1",
            "pods",
            state,
            eventQueue,
            new HashSet<>(),
            100,
            100,
            100);

    state.getFactory().startAllWatchers();
    state.start();

    // Wait for the request to be received by WireMock
    boolean requestReceived = requestReceivedLatch.await(5, TimeUnit.SECONDS);
    assertThat(requestReceived).isTrue();

    // Interrupt the threads while it's waiting for the slow response
    boolean stopped = state.stopAndWait(5_000);
    assertThat(stopped).isTrue();

    // Verify no events were queued
    assertThat(eventQueue.size()).isEqualTo(0);
  }
}
