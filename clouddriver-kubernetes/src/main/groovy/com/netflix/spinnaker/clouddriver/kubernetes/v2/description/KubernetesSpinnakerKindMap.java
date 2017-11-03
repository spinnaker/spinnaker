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

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class KubernetesSpinnakerKindMap {
  public enum SpinnakerKind {
    INSTANCE,
    SERVER_GROUP,
    LOAD_BALANCER,
    SECURITY_GROUP,
    SUBCLUSTER,
    UNCLASSIFIED
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
    return kubernetesToSpinnaker.get(kubernetesKind);
  }

  public Set<KubernetesKind> translateSpinnakerKind(SpinnakerKind spinnakerKind) {
    return spinnakerToKubernetes.get(spinnakerKind);
  }
}
