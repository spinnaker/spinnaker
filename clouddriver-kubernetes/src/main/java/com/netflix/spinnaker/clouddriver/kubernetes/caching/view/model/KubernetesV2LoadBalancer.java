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
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
public final class KubernetesV2LoadBalancer
    implements KubernetesResource, LoadBalancer, LoadBalancerProvider.Details {
  private final Set<LoadBalancerServerGroup> serverGroups;
  private final String account;
  private final String name;
  private final String namespace;
  private final String displayName;
  private final KubernetesApiVersion apiVersion;
  private final KubernetesKind kind;
  private final Map<String, String> labels;
  private final Moniker moniker;

  private KubernetesV2LoadBalancer(
      KubernetesManifest manifest, String key, Set<LoadBalancerServerGroup> serverGroups) {
    this.account = ((Keys.InfrastructureCacheKey) Keys.parseKey(key).get()).getAccount();
    this.name = manifest.getFullResourceName();
    this.displayName = manifest.getName();
    this.apiVersion = manifest.getApiVersion();
    this.kind = manifest.getKind();
    this.namespace = manifest.getNamespace();
    this.labels = ImmutableMap.copyOf(manifest.getLabels());
    this.moniker =
        NamerRegistry.lookup()
            .withProvider(KubernetesCloudProvider.ID)
            .withAccount(account)
            .withResource(KubernetesManifest.class)
            .deriveMoniker(manifest);
    this.serverGroups = serverGroups;
  }

  @Nullable
  @ParametersAreNonnullByDefault
  public static KubernetesV2LoadBalancer fromCacheData(
      CacheData cd, Set<LoadBalancerServerGroup> loadBalancerServerGroups) {
    KubernetesManifest manifest = KubernetesCacheDataConverter.getManifest(cd);
    if (manifest == null) {
      log.warn("Cache data {} inserted without a manifest", cd.getId());
      return null;
    }
    return new KubernetesV2LoadBalancer(manifest, cd.getId(), loadBalancerServerGroups);
  }

  @Override
  public String getType() {
    return KubernetesCloudProvider.ID;
  }

  public String getRegion() {
    return namespace;
  }

  @Override
  public String getCloudProvider() {
    return KubernetesCloudProvider.ID;
  }
}
