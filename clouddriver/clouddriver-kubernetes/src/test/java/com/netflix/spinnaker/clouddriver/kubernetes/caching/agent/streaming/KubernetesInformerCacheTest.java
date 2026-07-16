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

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class KubernetesInformerCacheTest {

  private KubernetesInformerCache<DynamicKubernetesObject> cache;

  @BeforeEach
  void setUp() {
    cache = new KubernetesInformerCache<>();
  }

  @Test
  void testAddAndGet() {
    DynamicKubernetesObject obj = getNamespacedObject();
    cache.add(obj);

    assertThat(cache.get(obj)).isEqualTo(obj);
  }

  // the cache doesn't store anything except the key
  @Test
  void testSaveOnlyId() {
    // create an object with extra fields
    DynamicKubernetesObject obj = getNamespacedObject();
    V1ObjectMeta objMeta = new V1ObjectMeta();
    objMeta.setName(obj.getMetadata().getName());
    objMeta.setNamespace(obj.getMetadata().getNamespace());
    objMeta.setLabels(Map.of("key", "value"));
    obj.setMetadata(objMeta);
    cache.add(obj);

    // but the saved object contains only the key
    assertThat(cache.get(obj)).isEqualTo(getNamespacedObject());
    assertThat(cache.get(obj).getMetadata().getLabels()).isNullOrEmpty();
  }

  @Test
  void testUpdate() {
    DynamicKubernetesObject obj = getNamespacedObject();
    cache.add(obj);

    // "update" doesn't change the object because the cache doesn't store anything except the key
    DynamicKubernetesObject updatedObj = getNamespacedObject();
    V1ObjectMeta updatedMeta = new V1ObjectMeta();
    updatedMeta.setName(obj.getMetadata().getName());
    updatedMeta.setNamespace(obj.getMetadata().getNamespace());
    updatedMeta.setLabels(Map.of("key", "value"));
    updatedObj.setMetadata(updatedMeta);
    cache.update(updatedObj);

    assertThat(cache.get(updatedObj)).isEqualTo(obj);
  }

  @Test
  void testDelete() {
    DynamicKubernetesObject obj = getNamespacedObject();
    cache.add(obj);

    cache.delete(obj);
    assertThat(cache.get(obj)).isNull();
  }

  @ParameterizedTest
  @MethodSource("testGetByKeyData")
  void testAddAndGetByKey(
      List<DynamicKubernetesObject> input, String key, DynamicKubernetesObject expected) {
    input.forEach(cache::add);

    assertThat(cache.getByKey(key)).isEqualTo(expected);
  }

  static Stream<Arguments> testGetByKeyData() {
    List<DynamicKubernetesObject> namespacedObjects = List.of(getNamespacedObject());
    List<DynamicKubernetesObject> clusterObjects = List.of(getClusterObject());
    return Stream.of(
        Arguments.of(namespacedObjects, "test-namespace/test-name", getNamespacedObject()),
        Arguments.of(namespacedObjects, "unknown-namespace/unknown-name", null),
        Arguments.of(clusterObjects, "test-cluster-name", getClusterObject()),
        Arguments.of(clusterObjects, "unknown-name", null));
  }

  @ParameterizedTest
  @MethodSource("testAddAndGetData")
  void testAddAndGet(
      List<DynamicKubernetesObject> input,
      DynamicKubernetesObject key,
      DynamicKubernetesObject expected) {
    input.forEach(cache::add);
    assertThat(cache.get(key)).isEqualTo(expected);
  }

  static Stream<Arguments> testAddAndGetData() {
    List<DynamicKubernetesObject> namespacedObjects = List.of(getNamespacedObject());
    List<DynamicKubernetesObject> clusterObjects = List.of(getClusterObject());

    DynamicKubernetesObject unknownNamespacedObject = new DynamicKubernetesObject();
    V1ObjectMeta unknownMeta = new V1ObjectMeta();
    unknownMeta.setName("unknown-name");
    unknownMeta.setNamespace("unknown-namespace");
    unknownNamespacedObject.setMetadata(unknownMeta);

    DynamicKubernetesObject unknownClusterObject = new DynamicKubernetesObject();
    V1ObjectMeta unknownClusterMeta = new V1ObjectMeta();
    unknownClusterMeta.setName("unknown-cluster-name");
    unknownClusterObject.setMetadata(unknownClusterMeta);

    return Stream.of(
        Arguments.of(namespacedObjects, getNamespacedObject(), getNamespacedObject()),
        Arguments.of(namespacedObjects, unknownNamespacedObject, null),
        Arguments.of(clusterObjects, getClusterObject(), getClusterObject()),
        Arguments.of(clusterObjects, unknownClusterObject, null));
  }

  @ParameterizedTest
  @MethodSource("testListKeysData")
  void testListKeys(List<DynamicKubernetesObject> objects, List<String> expectedKeys) {
    objects.forEach(cache::add);
    assertThat(cache.listKeys()).containsExactlyInAnyOrderElementsOf(expectedKeys);
  }

  static Stream<Arguments> testListKeysData() {
    DynamicKubernetesObject obj1 = getNamespacedObject("test-name-1");
    DynamicKubernetesObject obj2 = getNamespacedObject("test-name-2");

    List<DynamicKubernetesObject> namespacedObjects = List.of(obj1, obj2);
    List<DynamicKubernetesObject> clusterObjects = List.of(getClusterObject());

    return Stream.of(
        Arguments.of(
            namespacedObjects, List.of("test-namespace/test-name-1", "test-namespace/test-name-2")),
        Arguments.of(clusterObjects, List.of("test-cluster-name")),
        Arguments.of(List.of(), List.of()));
  }

  private static DynamicKubernetesObject getNamespacedObject() {
    return getNamespacedObject("test-name");
  }

  private static DynamicKubernetesObject getNamespacedObject(String name) {
    DynamicKubernetesObject obj = new DynamicKubernetesObject();
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(name);
    meta.setNamespace("test-namespace");
    obj.setMetadata(meta);

    return obj;
  }

  private static DynamicKubernetesObject getClusterObject() {
    DynamicKubernetesObject obj = new DynamicKubernetesObject();
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName("test-cluster-name");
    obj.setMetadata(meta);

    return obj;
  }
}
