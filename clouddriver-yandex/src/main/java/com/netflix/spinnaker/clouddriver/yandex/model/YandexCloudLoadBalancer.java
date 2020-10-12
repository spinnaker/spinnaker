/*
 * Copyright 2020 YANDEX LLC
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
 */

package com.netflix.spinnaker.clouddriver.yandex.model;

import static yandex.cloud.api.loadbalancer.v1.NetworkLoadBalancerOuterClass.NetworkLoadBalancer;

import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import com.netflix.spinnaker.clouddriver.yandex.model.health.YandexLoadBalancerHealth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class YandexCloudLoadBalancer implements LoadBalancer {
  String cloudProvider;
  String account;
  String id;
  String region;
  String name;
  String description;
  BalancerType balancerType;
  Long createdTime;
  SessionAffinity sessionAffinity;
  List<Listener> listeners;
  Map<String, String> labels;

  Set<LoadBalancerServerGroup> serverGroups;
  Map<String, List<YandexLoadBalancerHealth>> healths;

  @Override
  public String getType() {
    return getCloudProvider();
  }

  public static YandexCloudLoadBalancer createFromNetworkLoadBalancer(
      NetworkLoadBalancer nlb,
      String account,
      Map<String, List<YandexLoadBalancerHealth>> healths) {
    return YandexCloudLoadBalancer.builder()
        .cloudProvider(YandexCloudProvider.ID)
        .account(account)
        .id(nlb.getId())
        .region(nlb.getRegionId())
        .name(nlb.getName())
        .description(nlb.getDescription())
        .balancerType(BalancerType.valueOf(nlb.getType().name()))
        .createdTime(nlb.getCreatedAt().getSeconds() * 1000)
        .sessionAffinity(SessionAffinity.valueOf(nlb.getSessionAffinity().name()))
        .listeners(
            nlb.getListenersList().stream()
                .map(
                    listener ->
                        new Listener(
                            listener.getName(),
                            listener.getAddress(),
                            (int) listener.getPort(),
                            Protocol.valueOf(listener.getProtocol().name()),
                            (int) listener.getTargetPort(),
                            listener.getSubnetId(),
                            IpVersion.IPV4))
                .collect(Collectors.toList()))
        .labels(nlb.getLabelsMap())
        .healths(healths)
        .serverGroups(new HashSet<>())
        .build();
  }

  public enum BalancerType {
    EXTERNAL,
    INTERNAL;
  }

  public enum SessionAffinity {
    SESSION_AFFINITY_UNSPECIFIED,
    CLIENT_IP_PORT_PROTO;
  }

  public enum Protocol {
    TCP,
    UDP;
  }

  public enum IpVersion {
    IPV4,
    IPV6;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Listener {
    private String name;
    private String address;
    private Integer port;
    private Protocol protocol;
    private Integer targetPort;
    private String subnetId;
    private IpVersion ipVersion;
  }
}
