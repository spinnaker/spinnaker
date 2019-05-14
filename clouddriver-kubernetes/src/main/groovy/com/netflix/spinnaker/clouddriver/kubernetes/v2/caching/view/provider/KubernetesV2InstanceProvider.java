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
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2Instance;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.ContainerLog;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1Pod;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KubernetesV2InstanceProvider
    implements InstanceProvider<KubernetesV2Instance, List<ContainerLog>> {
  private final KubernetesCacheUtils cacheUtils;
  private final KubernetesSpinnakerKindMap kindMap;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final KubectlJobExecutor jobExecutor;

  @Autowired
  KubernetesV2InstanceProvider(
      KubernetesCacheUtils cacheUtils,
      KubernetesSpinnakerKindMap kindMap,
      AccountCredentialsRepository accountCredentialsRepository,
      KubectlJobExecutor jobExecutor) {
    this.cacheUtils = cacheUtils;
    this.kindMap = kindMap;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.jobExecutor = jobExecutor;
  }

  @Override
  public String getCloudProvider() {
    return KubernetesCloudProvider.getID();
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
    String key = Keys.infrastructure(kind, account, location, name);

    Optional<CacheData> optionalInstanceData = cacheUtils.getSingleEntry(kind.toString(), key);
    if (!optionalInstanceData.isPresent()) {
      return null;
    }

    CacheData instanceData = optionalInstanceData.get();

    return KubernetesV2Instance.fromCacheData(instanceData);
  }

  @Override
  public List<ContainerLog> getConsoleOutput(String account, String location, String fullName) {
    KubernetesNamedAccountCredentials<KubernetesV2Credentials> credentials;
    try {
      credentials =
          (KubernetesNamedAccountCredentials) accountCredentialsRepository.getOne(account);
    } catch (Exception e) {
      log.warn("Failure getting account {}", account);
      return null;
    }

    if (credentials == null || credentials.getProviderVersion() != ProviderVersion.v2) {
      return null;
    }

    Pair<KubernetesKind, String> parsedName;
    try {
      parsedName = KubernetesManifest.fromFullResourceName(fullName);
    } catch (Exception e) {
      return null;
    }

    String name = parsedName.getRight();

    V1Pod pod =
        KubernetesCacheDataConverter.getResource(
            credentials.getCredentials().get(KubernetesKind.POD, location, name), V1Pod.class);

    List<ContainerLog> result = new ArrayList();

    // Make live calls rather than abuse the cache for storing all logs
    for (V1Container container : pod.getSpec().getContainers()) {
      ContainerLog log = new ContainerLog();
      log.setName(container.getName());
      try {
        log.setOutput(credentials.getCredentials().logs(location, name, container.getName()));
      } catch (KubectlJobExecutor.KubectlException e) {
        // Typically happens if the container/pod isn't running yet
        log.setOutput(e.getMessage());
      }
      result.add(log);
    }

    return result;
  }
}
