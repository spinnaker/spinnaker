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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class KubernetesSpinnakerKindMap {
  public enum SpinnakerKind {
    INSTANCES("instances"),
    CONFIGS("configs"),
    SERVER_GROUPS("serverGroups"),
    LOAD_BALANCERS("loadBalancers"),
    SECURITY_GROUPS("securityGroups"),
    SERVER_GROUP_MANAGERS("serverGroupManagers"),
    UNCLASSIFIED("unclassified");

    private final String id;

    SpinnakerKind(String id) {
      this.id = id;
    }

    @Override
    public String toString() {
      return id;
    }

    @JsonCreator
    public static SpinnakerKind fromString(String name) {
      return Arrays.stream(values())
          .filter(k -> k.toString().equalsIgnoreCase(name))
          .findFirst()
          .orElse(UNCLASSIFIED);
    }
  }

  private Map<SpinnakerKind, Set<KubernetesKind>> spinnakerToKubernetes = new HashMap<>();
  private Map<KubernetesKind, SpinnakerKind> kubernetesToSpinnaker = new HashMap<>();

  void addRelationship(SpinnakerKind spinnakerKind, KubernetesKind kubernetesKind) {
    Set<KubernetesKind> kinds = spinnakerToKubernetes.get(spinnakerKind);
    if (kinds == null) {
      kinds = new HashSet<>();
    }

    kinds.add(kubernetesKind);
    spinnakerToKubernetes.put(spinnakerKind, kinds);
    kubernetesToSpinnaker.put(kubernetesKind, spinnakerKind);
  }

  public SpinnakerKind translateKubernetesKind(KubernetesKind kubernetesKind) {
    return kubernetesToSpinnaker.getOrDefault(kubernetesKind, SpinnakerKind.UNCLASSIFIED);
  }

  public Set<KubernetesKind> translateSpinnakerKind(SpinnakerKind spinnakerKind) {
    return spinnakerToKubernetes.get(spinnakerKind);
  }

  public Set<KubernetesKind> allKubernetesKinds() {
    return kubernetesToSpinnaker.keySet();
  }

  public Map<String, String> kubernetesToSpinnakerKindStringMap() {
    return kubernetesToSpinnaker.entrySet().stream()
        .filter(x -> x.getKey() != KubernetesKind.NONE)
        .collect(Collectors.toMap(x -> x.getKey().toString(), x -> x.getValue().toString()));
  }
}
