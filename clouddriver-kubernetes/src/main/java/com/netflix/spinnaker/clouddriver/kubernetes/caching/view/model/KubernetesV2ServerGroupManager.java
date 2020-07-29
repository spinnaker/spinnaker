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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model;

import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.data.KubernetesV2ServerGroupCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.data.KubernetesV2ServerGroupManagerCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.ServerGroupManager;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Value
public final class KubernetesV2ServerGroupManager extends ManifestBasedModel
    implements ServerGroupManager {
  private final KubernetesManifest manifest;
  private final Keys.InfrastructureCacheKey key;
  private final Set<KubernetesV2ServerGroupSummary> serverGroups;

  private KubernetesV2ServerGroupManager(
      KubernetesManifest manifest, String key, Set<KubernetesV2ServerGroupSummary> serverGroups) {
    this.manifest = manifest;
    this.key = (Keys.InfrastructureCacheKey) Keys.parseKey(key).get();
    this.serverGroups = serverGroups;
  }

  public static KubernetesV2ServerGroupManager fromCacheData(
      KubernetesV2ServerGroupManagerCacheData data) {
    KubernetesManifest manifest =
        KubernetesCacheDataConverter.getManifest(data.getServerGroupManagerData());
    if (manifest == null) {
      log.warn(
          "Cache data {} inserted without a manifest", data.getServerGroupManagerData().getId());
      return null;
    }

    Set<KubernetesV2ServerGroupSummary> serverGroups =
        data.getServerGroupData().stream()
            .map(
                sg ->
                    KubernetesV2ServerGroup.fromCacheData(
                        KubernetesV2ServerGroupCacheData.builder().serverGroupData(sg).build()))
            .filter(Objects::nonNull)
            .map(KubernetesV2ServerGroup::toServerGroupSummary)
            .collect(Collectors.toSet());

    return new KubernetesV2ServerGroupManager(
        manifest, data.getServerGroupManagerData().getId(), serverGroups);
  }
}
