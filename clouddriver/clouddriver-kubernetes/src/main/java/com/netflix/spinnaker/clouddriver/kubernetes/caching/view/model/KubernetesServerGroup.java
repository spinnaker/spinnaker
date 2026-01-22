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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.ArtifactReplacer;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.Replacer;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.InfrastructureCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.data.KubernetesServerGroupCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestTraffic;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.model.ServerGroupManager.ServerGroupManagerSummary;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import jakarta.validation.constraints.Null;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Value
public final class KubernetesServerGroup implements KubernetesResource, ServerGroup {
  private static final Logger log = LoggerFactory.getLogger(KubernetesServerGroup.class);
  private final boolean disabled;
  private final Set<KubernetesInstance> instances;
  private final Set<String> loadBalancers;
  private final List<ServerGroupManagerSummary> serverGroupManagers;
  private final Capacity capacity;
  private final String account;
  private final String name;
  private final String namespace;
  private final String displayName;
  private final KubernetesApiVersion apiVersion;
  private final KubernetesKind kind;
  private final Map<String, String> labels;
  private final Moniker moniker;
  private final Long createdTime;
  private final ImmutableMap<String, ImmutableList<String>> buildInfo;

  private final Set<String> zones = ImmutableSet.of();
  private final Set<String> securityGroups = ImmutableSet.of();
  private final Map<String, Object> launchConfig = ImmutableMap.of();

  @JsonIgnore
  private static final ArtifactReplacer dockerImageReplacer =
      new ArtifactReplacer(ImmutableList.of(Replacer.dockerImage()));

  @Override
  public ServerGroup.InstanceCounts getInstanceCounts() {
    return ServerGroup.InstanceCounts.builder()
        .total(Ints.checkedCast(instances.size()))
        .up(
            Ints.checkedCast(
                instances.stream().filter(i -> i.getHealthState().equals(HealthState.Up)).count()))
        .down(
            Ints.checkedCast(
                instances.stream()
                    .filter(i -> i.getHealthState().equals(HealthState.Down))
                    .count()))
        .unknown(
            Ints.checkedCast(
                instances.stream()
                    .filter(i -> i.getHealthState().equals(HealthState.Unknown))
                    .count()))
        .outOfService(
            Ints.checkedCast(
                instances.stream()
                    .filter(i -> i.getHealthState().equals(HealthState.OutOfService))
                    .count()))
        .starting(
            Ints.checkedCast(
                instances.stream()
                    .filter(i -> i.getHealthState().equals(HealthState.Starting))
                    .count()))
        .build();
  }

  @Override
  public Boolean isDisabled() {
    return disabled;
  }

  private KubernetesServerGroup(
      KubernetesManifest manifest,
      String key,
      Moniker moniker,
      List<KubernetesInstance> instances,
      Set<String> loadBalancers,
      List<ServerGroupManagerSummary> serverGroupManagers,
      Boolean disabled) {
    this.account = ((Keys.InfrastructureCacheKey) Keys.parseKey(key).get()).getAccount();
    this.kind = manifest.getKind();
    this.apiVersion = manifest.getApiVersion();
    this.namespace = manifest.getNamespace();
    this.name = manifest.getFullResourceName();
    this.displayName = manifest.getName();
    this.labels = ImmutableMap.copyOf(manifest.getLabels());
    this.moniker = moniker;
    this.createdTime = manifest.getCreationTimestampEpochMillis();
    this.buildInfo =
        ImmutableMap.of(
            "images",
            dockerImageReplacer.findAll(manifest).stream()
                .map(Artifact::getReference)
                .distinct()
                .collect(toImmutableList()));
    this.instances = new HashSet<>(instances);
    this.loadBalancers = loadBalancers;
    this.serverGroupManagers = serverGroupManagers;
    this.disabled = disabled;

    Object odesired =
        ((Map<String, Object>) manifest.getOrDefault("spec", new HashMap<String, Object>()))
            .getOrDefault("replicas", 0);
    int desired = 0;

    if (odesired instanceof Number) {
      desired = ((Number) odesired).intValue();
    } else {
      log.warn("Unable to cast replica count from unexpected type: {}", odesired.getClass());
    }

    this.capacity = Capacity.builder().desired(desired).build();
  }

  public static KubernetesServerGroup fromCacheData(KubernetesServerGroupCacheData cacheData) {
    List<ServerGroupManagerSummary> serverGroupManagers =
        cacheData.getServerGroupManagerKeys().stream()
            .map(Keys::parseKey)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(k -> k instanceof InfrastructureCacheKey)
            .map(k -> (InfrastructureCacheKey) k)
            .map(
                k ->
                    ServerGroupManagerSummary.builder()
                        .account(k.getAccount())
                        .location(k.getNamespace())
                        .name(k.getName())
                        .build())
            .collect(Collectors.toList());

    KubernetesManifest manifest =
        KubernetesCacheDataConverter.getManifest(cacheData.getServerGroupData());

    if (manifest == null) {
      log.warn("Cache data {} inserted without a manifest", cacheData.getServerGroupData().getId());
      return null;
    }

    List<KubernetesInstance> instances =
        cacheData.getInstanceData().stream()
            .map(KubernetesInstance::fromCacheData)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    KubernetesManifestTraffic traffic = KubernetesManifestAnnotater.getTraffic(manifest);
    Set<String> explicitLoadBalancers =
        traffic.getLoadBalancers().stream()
            // TODO(ezimanyi): Leaving this logic and the comment below, but I'm not sure that we
            // still need the logic to parse then re-serialize the name.
            // this ensures the names are serialized correctly when the get merged below
            .map(lb -> KubernetesCoordinates.builder().fullResourceName(lb).build())
            .map(c -> KubernetesManifest.getFullResourceName(c.getKind(), c.getName()))
            .collect(Collectors.toSet());

    Set<String> loadBalancers =
        cacheData.getLoadBalancerKeys().stream()
            .map(Keys::parseKey)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(k -> (InfrastructureCacheKey) k)
            .map(k -> KubernetesManifest.getFullResourceName(k.getKubernetesKind(), k.getName()))
            .collect(Collectors.toSet());

    boolean disabled = loadBalancers.isEmpty() && !explicitLoadBalancers.isEmpty();
    loadBalancers.addAll(explicitLoadBalancers);

    Moniker moniker = KubernetesCacheDataConverter.getMoniker(cacheData.getServerGroupData());
    return new KubernetesServerGroup(
        manifest,
        cacheData.getServerGroupData().getId(),
        moniker,
        instances,
        loadBalancers,
        serverGroupManagers,
        disabled);
  }

  public KubernetesServerGroupSummary toServerGroupSummary() {
    return KubernetesServerGroupSummary.builder()
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
        .instances(
            instances.stream()
                .map(KubernetesInstance::toLoadBalancerInstance)
                .collect(Collectors.toSet()))
        .name(getName())
        .region(getRegion())
        .isDisabled(isDisabled())
        .cloudProvider(KubernetesCloudProvider.ID)
        .build();
  }

  @Deprecated
  @Null
  @Override
  public ImageSummary getImageSummary() {
    return null;
  }

  @Override
  public ImagesSummary getImagesSummary() {
    return () ->
        ImmutableList.of(
            KubernetesImageSummary.builder()
                .serverGroupName(displayName)
                .buildInfo(buildInfo)
                .build());
  }

  @Override
  public String getRegion() {
    return namespace;
  }

  @Override
  public String getCloudProvider() {
    return KubernetesCloudProvider.ID;
  }
}
