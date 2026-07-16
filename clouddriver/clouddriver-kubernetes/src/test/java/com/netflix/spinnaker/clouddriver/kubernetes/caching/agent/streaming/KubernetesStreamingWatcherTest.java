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

import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class KubernetesStreamingWatcherTest {

  private ExecutorService executor;
  private State state;
  private BlockingQueue<KubernetesStreamingEvent> eventQueue;

  @BeforeEach
  void setUp() {
    executor = Executors.newSingleThreadExecutor();
    ApiClient k8sClient = Mockito.mock(ApiClient.class);
    Mockito.when(k8sClient.getReadTimeout()).thenReturn(0);
    state = new State(executor, new KubernetesInformerFactory(k8sClient, executor));
    eventQueue = new ArrayBlockingQueue<>(100);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @ParameterizedTest
  @MethodSource("handlersList")
  void testNamespacedEventWithAllRequiredFields(
      Handler handler, KubernetesStreamingEvent.Type eventType) {
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1");

    DynamicKubernetesObject obj = getPodEvent(true);
    handler.accept(watcher, obj);

    KubernetesStreamingEvent expected = new KubernetesStreamingEvent(eventType, getPodManifest());

    assertThat(eventQueue.size()).isEqualTo(1);
    assertThat(eventQueue.poll()).isEqualTo(expected);
    assertThat(state.getLastReceivedEventTime()).isGreaterThan(0L);
    assertThat(state.getLastProcessedEventBatchTime()).isEqualTo(0L);
  }

  @ParameterizedTest
  @MethodSource("handlersList")
  void testNamespacedEventWithoutRequiredFields(
      Handler handler, KubernetesStreamingEvent.Type eventType) {
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1");

    // object without kind and apiVersion. watcher should fill them
    DynamicKubernetesObject obj = getPodEvent(false);
    handler.accept(watcher, obj);

    KubernetesStreamingEvent expected = new KubernetesStreamingEvent(eventType, getPodManifest());

    assertThat(eventQueue.size()).isEqualTo(1);
    assertThat(eventQueue.poll()).isEqualTo(expected);
    assertThat(state.getLastReceivedEventTime()).isGreaterThan(0L);
    assertThat(state.getLastProcessedEventBatchTime()).isEqualTo(0L);
  }

  @Test
  void testProcessNullObject() {
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1");

    watcher.onAdd(null);
    assertThat(eventQueue.size()).isEqualTo(0);
    assertThat(state.getLastReceivedEventTime()).isEqualTo(0L);
    assertThat(state.getLastProcessedEventBatchTime()).isEqualTo(0L);
  }

  @Test
  void testProcessNullMeta() {
    KubernetesStreamingWatcher watcher = createWatcher("Pod", "", "v1");

    DynamicKubernetesObject obj = new DynamicKubernetesObject();
    obj.setMetadata(null);

    watcher.onAdd(obj);
    assertThat(eventQueue.size()).isEqualTo(0);
    assertThat(state.getLastReceivedEventTime()).isEqualTo(0L);
    assertThat(state.getLastProcessedEventBatchTime()).isEqualTo(0L);
  }

  @MethodSource
  static Stream<Arguments> handlersList() {
    Handler onAdd = KubernetesStreamingWatcher::onAdd;
    Handler onUpdate = (instance, obj) -> instance.onUpdate(obj, obj);
    Handler onDelete = (instance, obj) -> instance.onDelete(obj, false);

    return Stream.of(
        Arguments.of(onAdd, KubernetesStreamingEvent.Type.UPSERT),
        Arguments.of(onUpdate, KubernetesStreamingEvent.Type.UPSERT),
        Arguments.of(onDelete, KubernetesStreamingEvent.Type.DELETE));
  }

  private static DynamicKubernetesObject getPodEvent(boolean withKind) {
    DynamicKubernetesObject obj = new DynamicKubernetesObject();
    if (withKind) {
      obj.setKind("Pod");
      obj.setApiVersion("v1");
    }
    V1ObjectMeta objectMeta = new V1ObjectMeta();
    objectMeta.setName("test-pod");
    objectMeta.setNamespace("test-namespace");
    objectMeta.setLabels(Map.of("key1", "value"));
    objectMeta.setAnnotations(Map.of("key2", "value"));
    obj.setMetadata(objectMeta);
    return obj;
  }

  private static KubernetesManifest getPodManifest() {
    KubernetesManifest expected = new KubernetesManifest();
    expected.put("kind", "Pod");
    expected.put("apiVersion", "v1");
    expected.put(
        "metadata",
        Map.of(
            "name", "test-pod",
            "namespace", "test-namespace",
            "labels", Map.of("key1", "value"),
            "annotations", Map.of("key2", "value"),
            "finalizers", List.of(),
            "managedFields", List.of(),
            "ownerReferences", List.of()));
    return expected;
  }

  private KubernetesStreamingWatcher createWatcher(String kind, String group, String version) {
    return new KubernetesStreamingWatcher(state, kind, group, version, eventQueue);
  }

  private interface Handler
      extends BiConsumer<KubernetesStreamingWatcher, DynamicKubernetesObject> {}
}
