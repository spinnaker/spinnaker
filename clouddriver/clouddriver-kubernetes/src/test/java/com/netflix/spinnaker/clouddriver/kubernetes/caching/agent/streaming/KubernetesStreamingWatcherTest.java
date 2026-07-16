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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.gson.JsonParser;
import com.netflix.spinnaker.cats.agent.NoOpStartupConcurrencyControl;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesListObject;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class KubernetesStreamingWatcherTest {

  private ExecutorService executor;
  private State state;
  private BlockingQueue<KubernetesStreamingEvent> eventQueue;
  private K8SListWatchAdapter adapter;
  private Set<Keys.InfrastructureCacheKey> knownKeys;
  private Supplier<Boolean> isRunning;

  @BeforeEach
  void setUp() {
    knownKeys = new HashSet<>();
    executor = Executors.newSingleThreadExecutor();
    ApiClient k8sClient = mock(ApiClient.class);
    adapter = mock(K8SListWatchAdapter.class);
    Mockito.when(k8sClient.getReadTimeout()).thenReturn(0);
    state =
        new State(
            "test-account",
            executor,
            new KubernetesStreamingWatcherFactory(
                k8sClient, "account", executor, new NoOpStartupConcurrencyControl()));
    eventQueue = new ArrayBlockingQueue<>(100);
    isRunning = mock(Supplier.class);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void testProcessErrorObject() throws ApiException, IOException {
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1");
    when(isRunning.get())
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(false);

    DynamicKubernetesListObject emptyList = getList(getJsonFixture("kubeapi/pods.default.json"));
    when(adapter.list(any())).thenReturn(emptyList);

    Watchable watchable = mock(Watchable.class);
    when(watchable.hasNext()).thenReturn(true);
    V1Status status = new V1Status();
    status.setCode(HttpURLConnection.HTTP_GONE);
    Watch.Response response = new Watch.Response<>("ERROR", status);
    when(watchable.next()).thenReturn(response);
    when(adapter.watch(any(), any())).thenReturn(watchable);

    watcher.run();
    assertThat(eventQueue.size()).isEqualTo(0);

    verify(adapter).list("0");
    verify(adapter).list("");
  }

  @Test
  void testProcessNullMeta() throws ApiException, IOException {
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1");
    when(isRunning.get()).thenReturn(true).thenReturn(true).thenReturn(false);

    DynamicKubernetesListObject nullMetaList = getList(getJsonFixture("initialList/nullMeta.json"));
    when(adapter.list(any())).thenReturn(nullMetaList);

    Watchable watchable = mock(Watchable.class);
    when(watchable.hasNext()).thenReturn(false);
    when(adapter.watch(any(), any())).thenReturn(watchable);

    watcher.run();
    assertThat(eventQueue.size()).isEqualTo(0);
    assertThat(state.getLastReceivedEventTime()).isEqualTo(0L);
    assertThat(state.getLastProcessedEventBatchTime()).isEqualTo(0L);
  }

  @Test
  void testProcessesListAndEvents() throws ApiException, IOException {
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1");
    when(isRunning.get()).thenReturn(false);

    DynamicKubernetesListObject nullMetaList = getList(getJsonFixture("initialList/pods.json"));
    when(adapter.list(any())).thenReturn(nullMetaList);

    Watchable watchable = mock(Watchable.class);
    when(watchable.hasNext()).thenReturn(true);
    DynamicKubernetesObject obj = getObject(getJsonFixture("delete/pod.json"));
    Watch.Response response = new Watch.Response<>("DELETED", obj);
    when(watchable.next()).thenReturn(response);
    when(adapter.watch(any(), any())).thenReturn(watchable);

    watcher.run();
    assertThat(eventQueue.size()).isEqualTo(0);
    assertThat(state.getLastReceivedEventTime()).isEqualTo(0L);
    assertThat(state.getLastProcessedEventBatchTime()).isEqualTo(0L);
  }

  private String getJsonFixture(String path) throws IOException {
    InputStream is =
        getClass()
            .getResourceAsStream(
                String.format(
                    "/com/netflix/spinnaker/clouddriver/kubernetes/caching/agent/streaming/__files/%s",
                    path));
    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
  }

  private DynamicKubernetesListObject getList(String payload) {
    return new DynamicKubernetesListObject(JsonParser.parseString(payload).getAsJsonObject());
  }

  private DynamicKubernetesObject getObject(String payload) {
    return new DynamicKubernetesObject(JsonParser.parseString(payload).getAsJsonObject());
  }

  private KubernetesStreamingWatcher createWatcher(String kind, String group, String version) {
    return new KubernetesStreamingWatcher(
        adapter,
        state,
        kind,
        group,
        version,
        "account",
        eventQueue,
        knownKeys,
        1000,
        60 * 5,
        new NoOpStartupConcurrencyControl(),
        isRunning);
  }
}
