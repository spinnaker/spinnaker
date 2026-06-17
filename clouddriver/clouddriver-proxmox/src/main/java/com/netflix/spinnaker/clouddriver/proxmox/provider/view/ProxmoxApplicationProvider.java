/*
 * Copyright 2026 McIntosh.farm
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
package com.netflix.spinnaker.clouddriver.proxmox.provider.view;

import com.netflix.spinnaker.clouddriver.model.ApplicationProvider;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProxmoxApplicationProvider implements ApplicationProvider {

  private final ProxmoxServerClusterProvider clusterProvider;

  @Override
  public Set<ProxmoxApplication> getApplications(boolean expand) {
    return clusterProvider.getClusters().entrySet().stream()
        .map(e -> buildApplication(e.getKey(), e.getValue(), expand))
        .collect(Collectors.toSet());
  }

  @Override
  public ProxmoxApplication getApplication(String name) {
    Set<ProxmoxServerCluster> clusters = clusterProvider.getClusterDetails(name).get(name);
    if (clusters == null || clusters.isEmpty()) return null;
    return buildApplication(name, clusters, true);
  }

  private ProxmoxApplication buildApplication(
      String name, Set<ProxmoxServerCluster> clusters, boolean expand) {
    Map<String, Set<String>> clusterNames = new HashMap<>();
    if (expand) {
      for (ProxmoxServerCluster cluster : clusters) {
        clusterNames
            .computeIfAbsent(cluster.getAccountName(), k -> new HashSet<>())
            .add(cluster.getName());
      }
    }
    return new ProxmoxApplication(name, Collections.singletonMap("name", name), clusterNames);
  }
}
