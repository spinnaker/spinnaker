/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesInstance;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.model.ContainerLog;
import com.netflix.spinnaker.clouddriver.kubernetes.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KubernetesInstanceProvider
    implements InstanceProvider<KubernetesInstance, List<ContainerLog>> {
  private static final Logger log = LoggerFactory.getLogger(KubernetesInstanceProvider.class);
  private final KubernetesCacheUtils cacheUtils;
  private final KubernetesAccountResolver accountResolver;

  @Autowired
  KubernetesInstanceProvider(
      KubernetesCacheUtils cacheUtils, KubernetesAccountResolver accountResolver) {
    this.cacheUtils = cacheUtils;
    this.accountResolver = accountResolver;
  }

  @Override
  public String getCloudProvider() {
    return KubernetesCloudProvider.ID;
  }

  @Override
  public KubernetesInstance getInstance(String account, String namespace, String fullName) {
    return cacheUtils
        .getSingleEntry(account, namespace, fullName)
        .map(KubernetesInstance::fromCacheData)
        .orElse(null);
  }

  @Override
  public List<ContainerLog> getConsoleOutput(String account, String namespace, String fullName) {
    Optional<KubernetesCredentials> optionalCredentials = accountResolver.getCredentials(account);
    if (!optionalCredentials.isPresent()) {
      log.warn("Failure getting account {}", account);
      return null;
    }

    KubernetesCredentials credentials = optionalCredentials.get();
    KubernetesCoordinates coords;
    try {
      coords =
          KubernetesCoordinates.builder().namespace(namespace).fullResourceName(fullName).build();
    } catch (IllegalArgumentException e) {
      return null;
    }

    V1Pod pod = KubernetesCacheDataConverter.getResource(credentials.get(coords), V1Pod.class);

    // Short-circuit if pod cannot be found
    if (pod == null) {
      return ImmutableList.of(
          new ContainerLog("Error", "Failed to retrieve pod data; pod may have been deleted."));
    }

    return getPodLogs(credentials, pod);
  }

  @Nonnull
  private List<ContainerLog> getPodLogs(
      @Nonnull KubernetesCredentials credentials, @Nonnull V1Pod pod) {
    List<V1Container> initContainers =
        Optional.ofNullable(pod.getSpec().getInitContainers()).orElse(ImmutableList.of());
    List<V1Container> containers = pod.getSpec().getContainers();

    return Stream.concat(initContainers.stream(), containers.stream())
        .map(container -> getContainerLog(credentials, pod, container))
        .collect(Collectors.toList());
  }

  @Nonnull
  private ContainerLog getContainerLog(
      @Nonnull KubernetesCredentials credentials,
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
