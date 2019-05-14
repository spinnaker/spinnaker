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

import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.model.Cluster;
import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;

@Data
public class KubernetesV2Cluster implements Cluster {
  String name;
  Moniker moniker;
  String type = KubernetesCloudProvider.getID();
  String accountName;
  Set<ServerGroup> serverGroups = new HashSet<>();
  Set<LoadBalancer> loadBalancers = new HashSet<>();
  String application;

  public KubernetesV2Cluster(String rawKey) {
    Keys.ClusterCacheKey key = (Keys.ClusterCacheKey) Keys.parseKey(rawKey).get();
    this.name = key.getName();
    this.accountName = key.getAccount();
    this.application = key.getApplication();
    this.moniker = Moniker.builder().cluster(name).app(application).build();
  }

  public KubernetesV2Cluster(
      String rawKey,
      List<KubernetesV2ServerGroup> serverGroups,
      List<KubernetesV2LoadBalancer> loadBalancers) {
    this(rawKey);
    this.serverGroups =
        serverGroups.stream().map(sg -> (ServerGroup) sg).collect(Collectors.toSet());

    this.loadBalancers =
        loadBalancers.stream().map(sg -> (LoadBalancer) sg).collect(Collectors.toSet());
  }
}
