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

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.data.KubernetesServerGroupCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.data.KubernetesServerGroupManagerCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.ServerGroupManager;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Value
public final class KubernetesServerGroupManager implements KubernetesResource, ServerGroupManager {
  private static final Logger log = LoggerFactory.getLogger(KubernetesServerGroupManager.class);
  // private final KubernetesManifest manifest;
  private final String account;
  private final Set<KubernetesServerGroupSummary> serverGroups;
  private final String name;
  private final String namespace;
  private final String displayName;
  private final KubernetesApiVersion apiVersion;
  private final KubernetesKind kind;
  private final Map<String, String> labels;
  private final Moniker moniker;
  private final Long createdTime;

  private KubernetesServerGroupManager(
      KubernetesManifest manifest,
      String key,
      Moniker moniker,
      Set<KubernetesServerGroupSummary> serverGroups) {
    this.account = ((Keys.InfrastructureCacheKey) Keys.parseKey(key).get()).getAccount();
    this.kind = manifest.getKind();
    this.apiVersion = manifest.getApiVersion();
    this.namespace = manifest.getNamespace();
    this.name = manifest.getFullResourceName();
    this.displayName = manifest.getName();
    this.labels = ImmutableMap.copyOf(manifest.getLabels());
    this.moniker = moniker;
    this.serverGroups = serverGroups;
    this.createdTime = manifest.getCreationTimestampEpochMillis();
  }

  public static KubernetesServerGroupManager fromCacheData(
      KubernetesServerGroupManagerCacheData data) {
    KubernetesManifest manifest =
        KubernetesCacheDataConverter.getManifest(data.getServerGroupManagerData());
    if (manifest == null) {
      log.warn(
          "Cache data {} inserted without a manifest", data.getServerGroupManagerData().getId());
      return null;
    }

    Set<KubernetesServerGroupSummary> serverGroups =
        data.getServerGroupData().stream()
            .map(
                sg ->
                    KubernetesServerGroup.fromCacheData(
                        KubernetesServerGroupCacheData.builder().serverGroupData(sg).build()))
            .filter(Objects::nonNull)
            .map(KubernetesServerGroup::toServerGroupSummary)
            .collect(Collectors.toSet());

    Moniker moniker = KubernetesCacheDataConverter.getMoniker(data.getServerGroupManagerData());
    return new KubernetesServerGroupManager(
        manifest, data.getServerGroupManagerData().getId(), moniker, serverGroups);
  }

  @Override
  public String getRegion() {
    return namespace;
  }

  public String getCloudProvider() {
    return KubernetesCloudProvider.ID;
  }
}
