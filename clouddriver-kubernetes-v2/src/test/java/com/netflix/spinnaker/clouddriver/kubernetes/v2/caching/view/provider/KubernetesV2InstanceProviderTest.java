/*
 * Copyright 2020 Discovery, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.model.ContainerLog;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2Instance;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class KubernetesV2InstanceProviderTest {

  private KubernetesV2InstanceProvider provider;
  private KubernetesV2Credentials credentials;
  private KubernetesAccountResolver accountResolver;
  private KubernetesCacheUtils cacheUtils;

  private static final JSON json = new JSON();
  private static final KubernetesKind KIND = KubernetesKind.POD;
  private static final String ACCOUNT = "account";
  private static final String NAMESPACE = "namespace";
  private static final String POD_NAME = "mypod";
  private static final String POD_FULL_NAME = KIND + " " + POD_NAME;
  private static final String CONTAINER = "container";
  private static final String INIT_CONTAINER = "initContainer";
  private static final String LOG_OUTPUT = "logs";
  private static final String CACHE_KEY =
      Keys.InfrastructureCacheKey.createKey(KIND, ACCOUNT, NAMESPACE, POD_NAME);

  @BeforeEach
  public void setup() {
    accountResolver = mock(KubernetesAccountResolver.class);
    cacheUtils = mock(KubernetesCacheUtils.class);
    credentials = mock(KubernetesV2Credentials.class);
    provider = new KubernetesV2InstanceProvider(cacheUtils, accountResolver);
    when(accountResolver.getCredentials(ACCOUNT)).thenReturn(Optional.of(credentials));
  }

  @Test
  void getCloudProvider() {
    assertThat(provider.getCloudProvider()).isEqualTo(KubernetesCloudProvider.ID);
  }

  @Test
  void getInstanceSuccess() {
    CacheData cacheData = mock(CacheData.class);
    Map<String, Object> attributes = new HashMap<>();
    KubernetesManifest manifest = getKubernetesManifest();
    attributes.put("manifest", manifest);
    when(cacheData.getAttributes()).thenReturn(attributes);
    when(cacheUtils.getSingleEntry(KIND.toString(), CACHE_KEY)).thenReturn(Optional.of(cacheData));
    when(cacheData.getId()).thenReturn(CACHE_KEY);

    KubernetesV2Instance instance = provider.getInstance(ACCOUNT, NAMESPACE, POD_FULL_NAME);

    assertThat(instance.getManifest()).isEqualTo(manifest);
  }

  @Test
  void getInstanceBadPodNameShouldReturnNull() {
    KubernetesV2Instance instance = provider.getInstance(ACCOUNT, NAMESPACE, "badname");

    assertThat(instance).isNull();
  }

  @Test
  void getInstancePodNotFoundShouldReturnNull() {
    when(cacheUtils.getSingleEntry(KIND.toString(), CACHE_KEY)).thenReturn(Optional.empty());

    KubernetesV2Instance instance = provider.getInstance(ACCOUNT, NAMESPACE, POD_FULL_NAME);

    assertThat(instance).isNull();
  }

  @Test
  void getConsoleOutputSuccess() {
    KubernetesManifest manifest = getKubernetesManifest();
    when(credentials.get(KubernetesKind.POD, NAMESPACE, POD_NAME)).thenReturn(manifest);
    when(credentials.logs(anyString(), anyString(), anyString())).thenReturn(LOG_OUTPUT);

    List<ContainerLog> logs = provider.getConsoleOutput(ACCOUNT, NAMESPACE, POD_FULL_NAME);

    assertThat(logs).isNotEmpty();
    assertThat(logs).hasSize(2);
    assertThat(logs.get(0).getName()).isEqualTo(INIT_CONTAINER);
    assertThat(logs.get(0).getOutput()).isEqualTo(LOG_OUTPUT);
    assertThat(logs.get(1).getName()).isEqualTo(CONTAINER);
    assertThat(logs.get(1).getOutput()).isEqualTo(LOG_OUTPUT);
  }

  @Test
  void getConsoleOutputNoInitContainer() {
    V1Pod pod = getPod();
    pod.getSpec().setInitContainers(null);
    KubernetesManifest manifest = json.deserialize(json.serialize(pod), KubernetesManifest.class);

    when(credentials.get(KubernetesKind.POD, NAMESPACE, POD_NAME)).thenReturn(manifest);
    when(credentials.logs(anyString(), anyString(), anyString())).thenReturn(LOG_OUTPUT);

    List<ContainerLog> logs = provider.getConsoleOutput(ACCOUNT, NAMESPACE, POD_FULL_NAME);

    assertThat(logs).isNotEmpty();
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getName()).isEqualTo(CONTAINER);
    assertThat(logs.get(0).getOutput()).isEqualTo(LOG_OUTPUT);
  }

  @Test
  void getConsoleOutputKubectlException() {
    KubernetesManifest manifest = getKubernetesManifest();
    when(credentials.get(KubernetesKind.POD, NAMESPACE, POD_NAME)).thenReturn(manifest);
    when(credentials.logs(anyString(), anyString(), anyString()))
        .thenThrow(new KubectlJobExecutor.KubectlException(LOG_OUTPUT, null));

    List<ContainerLog> logs = provider.getConsoleOutput(ACCOUNT, NAMESPACE, POD_FULL_NAME);

    assertThat(logs).isNotEmpty();
    assertThat(logs).hasSize(2);
    assertThat(logs.get(0).getName()).isEqualTo(INIT_CONTAINER);
    assertThat(logs.get(0).getOutput()).isEqualTo(LOG_OUTPUT);
    assertThat(logs.get(1).getName()).isEqualTo(CONTAINER);
    assertThat(logs.get(1).getOutput()).isEqualTo(LOG_OUTPUT);
  }

  private V1Pod getPod() {
    V1Pod pod = new V1Pod();
    V1PodSpec podSpec = new V1PodSpec();
    V1ObjectMeta metadata = new V1ObjectMeta();
    V1Container container = new V1Container();
    V1Container initContainer = new V1Container();

    metadata.setName(POD_NAME);
    metadata.setNamespace(NAMESPACE);
    container.setName(CONTAINER);
    initContainer.setName(INIT_CONTAINER);
    pod.setMetadata(metadata);
    pod.setSpec(podSpec);
    podSpec.setContainers(Lists.newArrayList(container));
    podSpec.setInitContainers(Lists.newArrayList(initContainer));

    return pod;
  }

  private KubernetesManifest getKubernetesManifest() {
    V1Pod pod = getPod();
    return json.deserialize(json.serialize(pod), KubernetesManifest.class);
  }

  @Test
  void getConsoleOutputAccountNotFoundShouldReturnNull() {
    when(accountResolver.getCredentials(ACCOUNT)).thenReturn(Optional.empty());

    List<ContainerLog> logs = provider.getConsoleOutput(ACCOUNT, NAMESPACE, POD_FULL_NAME);

    assertThat(logs).isNull();
  }

  @Test
  void getConsoleOutputBadPodNameShouldReturnNull() {
    List<ContainerLog> logs = provider.getConsoleOutput(ACCOUNT, NAMESPACE, "badname");

    assertThat(logs).isNull();
  }

  @Test
  void getConsoleOutputPodNotFoundShouldReturnErrorContainerLog() {
    when(credentials.get(KubernetesKind.POD, NAMESPACE, POD_NAME)).thenReturn(null);

    List<ContainerLog> logs = provider.getConsoleOutput(ACCOUNT, NAMESPACE, POD_FULL_NAME);

    assertThat(logs).isNotEmpty();
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getName()).isEqualTo("Error");
    assertThat(logs.get(0).getOutput())
        .isEqualTo("Failed to retrieve pod data; pod may have been deleted.");
  }
}
