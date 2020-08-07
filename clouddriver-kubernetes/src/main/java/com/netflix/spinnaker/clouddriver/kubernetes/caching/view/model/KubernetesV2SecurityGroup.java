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

import static com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion.EXTENSIONS_V1BETA1;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion.NETWORKING_K8S_IO_V1;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion.NETWORKING_K8S_IO_V1BETA1;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.SecurityGroup;
import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary;
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule;
import com.netflix.spinnaker.moniker.Moniker;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.models.V1NetworkPolicy;
import io.kubernetes.client.openapi.models.V1NetworkPolicyEgressRule;
import io.kubernetes.client.openapi.models.V1NetworkPolicyIngressRule;
import io.kubernetes.client.openapi.models.V1NetworkPolicyPort;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
public final class KubernetesV2SecurityGroup implements KubernetesResource, SecurityGroup {
  private static final ImmutableSet<KubernetesApiVersion> SUPPORTED_API_VERSIONS =
      ImmutableSet.of(EXTENSIONS_V1BETA1, NETWORKING_K8S_IO_V1BETA1, NETWORKING_K8S_IO_V1);

  private final String account;
  private final String id;
  private final String namespace;
  private final String displayName;
  private final KubernetesApiVersion apiVersion;
  private final KubernetesKind kind;
  private final Map<String, String> labels;
  private final Moniker moniker;

  private final Set<Rule> inboundRules;
  private final Set<Rule> outboundRules;

  @Override
  public String getAccountName() {
    return account;
  }

  @Override
  public String getApplication() {
    return getMoniker().getApp();
  }

  @Override
  public SecurityGroupSummary getSummary() {
    return KubernetesV2SecurityGroupSummary.builder().id(id).name(id).build();
  }

  private KubernetesV2SecurityGroup(
      KubernetesManifest manifest,
      String key,
      Moniker moniker,
      Set<Rule> inboundRules,
      Set<Rule> outboundRules) {
    this.id = manifest.getFullResourceName();
    this.account = ((Keys.InfrastructureCacheKey) Keys.parseKey(key).get()).getAccount();
    this.kind = manifest.getKind();
    this.apiVersion = manifest.getApiVersion();
    this.displayName = manifest.getName();
    this.namespace = manifest.getNamespace();
    this.labels = ImmutableMap.copyOf(manifest.getLabels());
    this.moniker = moniker;

    this.inboundRules = inboundRules;
    this.outboundRules = outboundRules;
  }

  public static KubernetesV2SecurityGroup fromCacheData(CacheData cd) {
    if (cd == null) {
      return null;
    }

    KubernetesManifest manifest = KubernetesCacheDataConverter.getManifest(cd);

    if (manifest == null) {
      log.warn("Cache data {} inserted without a manifest", cd.getId());
      return null;
    }

    Set<Rule> inboundRules = new HashSet<>();
    Set<Rule> outboundRules = new HashSet<>();

    if (!manifest.getKind().equals(KubernetesKind.NETWORK_POLICY)) {
      log.warn("Unknown security group kind " + manifest.getKind());
    } else {
      if (SUPPORTED_API_VERSIONS.contains(manifest.getApiVersion())) {
        V1NetworkPolicy v1beta1NetworkPolicy =
            KubernetesCacheDataConverter.getResource(manifest, V1NetworkPolicy.class);
        inboundRules = inboundRules(v1beta1NetworkPolicy);
        outboundRules = outboundRules(v1beta1NetworkPolicy);
      } else {
        log.warn(
            "Could not determine (in)/(out)bound rules for "
                + manifest.getName()
                + " at version "
                + manifest.getApiVersion());
      }
    }

    Moniker moniker = KubernetesCacheDataConverter.getMoniker(cd);
    return new KubernetesV2SecurityGroup(
        manifest, cd.getId(), moniker, inboundRules, outboundRules);
  }

  private static Set<Rule> inboundRules(V1NetworkPolicy policy) {
    if (policy.getSpec().getIngress() == null) {
      return ImmutableSet.of();
    }
    return policy.getSpec().getIngress().stream()
        .map(V1NetworkPolicyIngressRule::getPorts)
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .map(KubernetesV2SecurityGroup::fromPolicyPort)
        .collect(Collectors.toSet());
  }

  private static Set<Rule> outboundRules(V1NetworkPolicy policy) {
    if (policy.getSpec().getEgress() == null) {
      return ImmutableSet.of();
    }
    return policy.getSpec().getEgress().stream()
        .map(V1NetworkPolicyEgressRule::getPorts)
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .map(KubernetesV2SecurityGroup::fromPolicyPort)
        .collect(Collectors.toSet());
  }

  private static Rule fromPolicyPort(V1NetworkPolicyPort policyPort) {
    IntOrString port = policyPort.getPort();
    return new PortRule()
        .setProtocol(policyPort.getProtocol())
        .setPortRanges(
            port == null
                ? null
                : new TreeSet<>(ImmutableList.of(new StringPortRange(port.toString()))));
  }

  @Override
  public String getType() {
    return KubernetesCloudProvider.ID;
  }

  @Override
  public String getName() {
    return id;
  }

  @Override
  public String getRegion() {
    return namespace;
  }

  @Override
  public String getCloudProvider() {
    return KubernetesCloudProvider.ID;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  private static class KubernetesV2SecurityGroupSummary implements SecurityGroupSummary {
    private String name;
    private String id;
  }

  @Data
  private static class PortRule implements Rule {
    @Nullable private SortedSet<PortRange> portRanges;
    @Nullable private String protocol;
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  public static class StringPortRange extends Rule.PortRange {
    protected String startPortName;
    protected String endPortName;

    StringPortRange(String port) {
      Integer numPort;
      try {
        numPort = Integer.parseInt(port);
        this.startPort = numPort;
        this.endPort = numPort;
      } catch (Exception e) {
        this.startPortName = port;
        this.endPortName = port;
      }
    }
  }
}
