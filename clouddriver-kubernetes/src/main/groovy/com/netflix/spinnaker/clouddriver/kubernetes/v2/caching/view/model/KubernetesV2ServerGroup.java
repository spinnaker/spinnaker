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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacer;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacerFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.data.KubernetesV2ServerGroupCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestTraffic;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.model.ServerGroupManager.ServerGroupManagerSummary;
import com.netflix.spinnaker.clouddriver.model.ServerGroupSummary;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class KubernetesV2ServerGroup extends ManifestBasedModel implements ServerGroup {
  Boolean disabled;
  Set<String> zones = new HashSet<>();
  Set<Instance> instances = new HashSet<>();
  Set<String> loadBalancers = new HashSet<>();
  Set<String> securityGroups = new HashSet<>();
  List<ServerGroupManagerSummary> serverGroupManagers = new ArrayList<>();
  Map<String, Object> launchConfig = new HashMap<>();
  Capacity capacity = new Capacity();
  ImageSummary imageSummary;
  ImagesSummary imagesSummary;
  KubernetesManifest manifest;
  Keys.InfrastructureCacheKey key;

  @JsonIgnore
  private static final ArtifactReplacer dockerImageReplacer;

  static {
    dockerImageReplacer = new ArtifactReplacer();
    dockerImageReplacer.addReplacer(ArtifactReplacerFactory.dockerImageReplacer());
  }

  @Override
  public ServerGroup.InstanceCounts getInstanceCounts() {
    return ServerGroup.InstanceCounts.builder()
        .total(Ints.checkedCast(instances.size()))
        .up(Ints.checkedCast(instances.stream().filter(i -> i.getHealthState().equals(HealthState.Up)).count()))
        .down(Ints.checkedCast(instances.stream().filter(i -> i.getHealthState().equals(HealthState.Down)).count()))
        .unknown(Ints.checkedCast(instances.stream().filter(i -> i.getHealthState().equals(HealthState.Unknown)).count()))
        .outOfService(Ints.checkedCast(instances.stream().filter(i -> i.getHealthState().equals(HealthState.OutOfService)).count()))
        .starting(Ints.checkedCast(instances.stream().filter(i -> i.getHealthState().equals(HealthState.Starting)).count()))
        .build();
  }

  public Map<String, Object> getBuildInfo() {
    return new ImmutableMap.Builder<String, Object>()
        .put("images", dockerImageReplacer.findAll(getManifest())
            .stream()
            .map(Artifact::getReference)
            .collect(Collectors.toSet()))
        .build();
  }

  @Override
  public Boolean isDisabled() {
    return disabled;
  }

  protected KubernetesV2ServerGroup(KubernetesManifest manifest, String key, List<KubernetesV2Instance> instances, Set<String> loadBalancers, List<ServerGroupManagerSummary> serverGroupManagers, Boolean disabled) {
    this.manifest = manifest;
    this.key = (Keys.InfrastructureCacheKey) Keys.parseKey(key).get();
    this.instances = new HashSet<>(instances);
    this.loadBalancers = loadBalancers;
    this.serverGroupManagers = serverGroupManagers;
    this.disabled = disabled;

    Object odesired = ((Map<String, Object>) manifest
        .getOrDefault("spec", new HashMap<String, Object>()))
        .getOrDefault("replicas", 0);
    Integer desired = 0;

    if (odesired instanceof Number) {
      desired = ((Number) odesired).intValue();
    } else {
      log.warn("Unable to cast replica count from unexpected type: {}", odesired.getClass());
    }

    this.capacity = Capacity.builder()
        .desired(desired)
        .build();
  }

  private static KubernetesV2ServerGroup fromCacheData(CacheData cd, List<CacheData> instanceData, List<CacheData> loadBalancerData, List<Keys.InfrastructureCacheKey> serverGroupManagerKeys) {
    if (cd == null) {
      return null;
    }

    if (instanceData == null) {
      instanceData = new ArrayList<>();
    }

    if (serverGroupManagerKeys == null) {
      serverGroupManagerKeys = new ArrayList<>();
    }

    List<ServerGroupManagerSummary> serverGroupManagers = serverGroupManagerKeys.stream()
        .map(k -> ServerGroupManagerSummary.builder()
            .account(k.getAccount())
            .location(k.getNamespace())
            .name(k.getName())
            .build()
        ).collect(Collectors.toList());

    KubernetesManifest manifest = KubernetesCacheDataConverter.getManifest(cd);

    if (manifest == null) {
      log.warn("Cache data {} inserted without a manifest", cd.getId());
      return null;
    }

    List<KubernetesV2Instance> instances = instanceData.stream()
        .map(KubernetesV2Instance::fromCacheData)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());


    KubernetesManifestTraffic traffic = KubernetesManifestAnnotater.getTraffic(manifest);
    Set<String> explicitLoadBalancers = traffic.getLoadBalancers().stream()
        .map(KubernetesManifest::fromFullResourceName)
        .map(p -> KubernetesManifest.getFullResourceName(p.getLeft(), p.getRight())) // this ensures the names are serialized correctly when the get merged below
        .collect(Collectors.toSet());

    Set<String> loadBalancers = loadBalancerData.stream()
        .map(CacheData::getId)
        .map(Keys::parseKey)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(k -> (Keys.InfrastructureCacheKey) k)
        .map(k -> KubernetesManifest.getFullResourceName(k.getKubernetesKind(), k.getName()))
        .collect(Collectors.toSet());

    Boolean disabled = loadBalancers.isEmpty() && !explicitLoadBalancers.isEmpty();
    loadBalancers.addAll(explicitLoadBalancers);

    return new KubernetesV2ServerGroup(manifest, cd.getId(), instances, loadBalancers, serverGroupManagers, disabled);
  }

  public static KubernetesV2ServerGroup fromCacheData(KubernetesV2ServerGroupCacheData cacheData) {
    return fromCacheData(cacheData.getServerGroupData(), cacheData.getInstanceData(), cacheData.getLoadBalancerData(), cacheData.getServerGroupManagerKeys());
  }

  public ServerGroupSummary toServerGroupSummary() {
    return KubernetesV2ServerGroupSummary.builder()
        .name(getName())
        .account(getAccount())
        .namespace(getRegion())
        .moniker(getMoniker())
        .build();
  }

  public LoadBalancerServerGroup toLoadBalancerServerGroup() {
    return LoadBalancerServerGroup.builder()
      .account(getAccount())
      .detachedInstances(new HashSet<>())
      .instances(instances.stream()
          .map(i -> ((KubernetesV2Instance) i).toLoadBalancerInstance())
          .collect(Collectors.toSet()))
      .name(getName())
      .region(getRegion())
      .isDisabled(isDisabled())
      .build();
  }
}
