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

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion.NETWORKING_K8S_IO_V1;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion.NETWORKING_K8S_IO_V1BETA1;

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.SecurityGroup;
import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary;
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule;
import io.kubernetes.client.models.V1NetworkPolicy;
import io.kubernetes.client.models.V1NetworkPolicyPort;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class KubernetesV2SecurityGroup extends ManifestBasedModel implements SecurityGroup {
  private KubernetesManifest manifest;
  private Keys.InfrastructureCacheKey key;
  private String id;

  private Set<Rule> inboundRules;
  private Set<Rule> outboundRules;

  @Override
  public String getApplication() {
    return getMoniker().getApp();
  }

  @Override
  public SecurityGroupSummary getSummary() {
    return KubernetesV2SecurityGroupSummary.builder().id(id).name(id).build();
  }

  KubernetesV2SecurityGroup(
      KubernetesManifest manifest, String key, Set<Rule> inboundRules, Set<Rule> outboundRules) {
    this.manifest = manifest;
    this.id = manifest.getFullResourceName();
    this.key = (Keys.InfrastructureCacheKey) Keys.parseKey(key).get();
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

    if (manifest.getKind() != KubernetesKind.NETWORK_POLICY) {
      log.warn("Unknown security group kind " + manifest.getKind());
    } else {
      if (manifest.getApiVersion().equals(NETWORKING_K8S_IO_V1)
          || (manifest.getApiVersion().equals(NETWORKING_K8S_IO_V1BETA1))) {
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

    return new KubernetesV2SecurityGroup(manifest, cd.getId(), inboundRules, outboundRules);
  }

  private static Set<Rule> inboundRules(V1NetworkPolicy policy) {
    return policy.getSpec().getIngress().stream()
        .map(i -> i.getPorts().stream().map(KubernetesV2SecurityGroup::fromPolicyPort))
        .flatMap(s -> s)
        .collect(Collectors.toSet());
  }

  private static Set<Rule> outboundRules(V1NetworkPolicy policy) {
    return policy.getSpec().getEgress().stream()
        .map(i -> i.getPorts().stream().map(KubernetesV2SecurityGroup::fromPolicyPort))
        .flatMap(s -> s)
        .collect(Collectors.toSet());
  }

  private static Rule fromPolicyPort(V1NetworkPolicyPort policyPort) {
    String port = policyPort.getPort();
    return new PortRule()
        .setProtocol(policyPort.getProtocol())
        .setPortRanges(new TreeSet<>(Collections.singletonList(new StringPortRange(port))));
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
    private SortedSet<PortRange> portRanges;
    private String protocol;
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
