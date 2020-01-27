/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider;

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.model.ContainerLog;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2Instance;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KubernetesV2InstanceProvider
    implements InstanceProvider<KubernetesV2Instance, List<ContainerLog>> {
  private final KubernetesCacheUtils cacheUtils;
  private final KubernetesAccountResolver accountResolver;

  @Autowired
  KubernetesV2InstanceProvider(
      KubernetesCacheUtils cacheUtils, KubernetesAccountResolver accountResolver) {
    this.cacheUtils = cacheUtils;
    this.accountResolver = accountResolver;
  }

  @Override
  public String getCloudProvider() {
    return KubernetesCloudProvider.ID;
  }

  @Override
  public KubernetesV2Instance getInstance(String account, String location, String fullName) {
    Pair<KubernetesKind, String> parsedName;
    try {
      parsedName = KubernetesManifest.fromFullResourceName(fullName);
    } catch (Exception e) {
      return null;
    }

    KubernetesKind kind = parsedName.getLeft();
    String name = parsedName.getRight();
    String key = Keys.InfrastructureCacheKey.createKey(kind, account, location, name);

    Optional<CacheData> optionalInstanceData = cacheUtils.getSingleEntry(kind.toString(), key);
    if (!optionalInstanceData.isPresent()) {
      return null;
    }

    CacheData instanceData = optionalInstanceData.get();

    return KubernetesV2Instance.fromCacheData(instanceData);
  }

  @Override
  public List<ContainerLog> getConsoleOutput(String account, String namespace, String fullName) {
    Optional<KubernetesV2Credentials> optionalCredentials = accountResolver.getCredentials(account);
    if (!optionalCredentials.isPresent()) {
      log.warn("Failure getting account {}", account);
      return null;
    }

    KubernetesV2Credentials credentials = optionalCredentials.get();
    Pair<KubernetesKind, String> parsedName;

    try {
      parsedName = KubernetesManifest.fromFullResourceName(fullName);
    } catch (Exception e) {
      return null;
    }

    String podName = parsedName.getRight();
    V1Pod pod =
        KubernetesCacheDataConverter.getResource(
            credentials.get(KubernetesKind.POD, namespace, podName), V1Pod.class);

    // Short-circuit if pod cannot be found
    if (pod == null) {
      return Collections.singletonList(
          new ContainerLog("Error", "Failed to retrieve pod data; pod may have been deleted."));
    }

    return getPodLogs(credentials, pod);
  }

  @Nonnull
  private List<ContainerLog> getPodLogs(
      @Nonnull KubernetesV2Credentials credentials, @Nonnull V1Pod pod) {
    List<V1Container> initContainers =
        Optional.ofNullable(pod.getSpec().getInitContainers()).orElse(Collections.emptyList());
    List<V1Container> containers = pod.getSpec().getContainers();

    return Stream.concat(initContainers.stream(), containers.stream())
        .map(container -> getContainerLog(credentials, pod, container))
        .collect(Collectors.toList());
  }

  @Nonnull
  private ContainerLog getContainerLog(
      @Nonnull KubernetesV2Credentials credentials,
      @Nonnull V1Pod pod,
      @Nonnull V1Container container) {
    String containerName = container.getName();
    V1ObjectMeta metadata = pod.getMetadata();

    try {
      // Make live calls rather than abuse the cache for storing all logs
      String containerLogs =
          credentials.logs(metadata.getNamespace(), metadata.getName(), containerName);
      return new ContainerLog(containerName, containerLogs);
    } catch (KubectlJobExecutor.KubectlException e) {
      // Typically happens if the container/pod isn't running yet
      return new ContainerLog(containerName, e.getMessage());
    }
  }
}
