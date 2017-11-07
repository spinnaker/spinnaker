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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.ServerGroupManager;
import com.netflix.spinnaker.clouddriver.model.ServerGroupSummary;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class KubernetesV2ServerGroupManager extends ManifestBasedModel implements ServerGroupManager {
  KubernetesManifest manifest;
  Keys.InfrastructureCacheKey key;
  Set<ServerGroupSummary> serverGroups;

  KubernetesV2ServerGroupManager(KubernetesManifest manifest, String key, Set<ServerGroupSummary> serverGroups) {
    this.manifest = manifest;
    this.key = (Keys.InfrastructureCacheKey) Keys.parseKey(key).get();
    this.serverGroups = serverGroups;
  }

  public static KubernetesV2ServerGroupManager fromCacheData(CacheData cd, List<CacheData> serverGroupData) {
    if (cd == null) {
      return null;
    }

    if (serverGroupData == null) {
      serverGroupData = new ArrayList<>();
    }

    KubernetesManifest manifest = KubernetesCacheDataConverter.getManifest(cd);

    if (manifest == null) {
      log.warn("Cache data {} inserted without a manifest", cd.getId());
      return null;
    }

    Set<ServerGroupSummary> serverGroups = serverGroupData.stream()
        .map(KubernetesV2ServerGroup::fromCacheData)
        .filter(Objects::nonNull)
        .map(KubernetesV2ServerGroup::toServerGroupSummary)
        .collect(Collectors.toSet());

    return new KubernetesV2ServerGroupManager(manifest, cd.getId(), serverGroups);
  }
}
