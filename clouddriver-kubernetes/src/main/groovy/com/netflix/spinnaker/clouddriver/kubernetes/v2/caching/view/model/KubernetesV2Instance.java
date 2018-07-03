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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model;

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.provider.KubernetesModelUtil;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Slf4j
public class KubernetesV2Instance extends ManifestBasedModel implements Instance {
  Long launchTime;
  List<Map<String, Object>> health = new ArrayList<>();
  KubernetesManifest manifest;
  Keys.InfrastructureCacheKey key;

  private KubernetesV2Instance(KubernetesManifest manifest, String key) {
    this.manifest = manifest;
    this.key = (Keys.InfrastructureCacheKey) Keys.parseKey(key).get();

    V1Pod pod = KubernetesCacheDataConverter.getResource(this.manifest, V1Pod.class);
    V1PodStatus status = pod.getStatus();
    if (status != null) {
      health.add(new KubernetesV2Health(status).toMap());
      if (status.getContainerStatuses() != null) {
        health.addAll(status
            .getContainerStatuses()
            .stream()
            .map(KubernetesV2Health::new)
            .map(KubernetesV2Health::toMap)
            .collect(Collectors.toList()));
      }
    }
  }

  public static KubernetesV2Instance fromCacheData(CacheData cd) {
    if (cd == null) {
      return null;
    }

    KubernetesManifest manifest = KubernetesCacheDataConverter.getManifest(cd);

    if (manifest == null) {
      log.warn("Cache data {} inserted without a manifest", cd.getId());
      return null;
    }

    return new KubernetesV2Instance(manifest, cd.getId());
  }

  public LoadBalancerInstance toLoadBalancerInstance() {
    return LoadBalancerInstance.builder()
        .health(health.stream().reduce(new HashMap<>(), (a, b) -> {
          Map<String, Object> result = new HashMap<>();
          result.putAll(a);
          result.putAll(b);
          return result;
        }))
        .id(getName())
        .uid(getUid())
        .zone(getZone())
        .build();
  }

  public HealthState getHealthState() {
    return KubernetesModelUtil.getHealthState(health);
  }
}
