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

package com.netflix.spinnaker.clouddriver.kubernetes.description;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class KubernetesSpinnakerKindMap {

  private final ImmutableMap<KubernetesKind, SpinnakerKind> kubernetesToSpinnaker;
  private final ImmutableSetMultimap<SpinnakerKind, KubernetesKind> spinnakerToKubernetes;

  public KubernetesSpinnakerKindMap(List<KubernetesHandler> handlers) {
    ImmutableMap.Builder<KubernetesKind, SpinnakerKind> kubernetesToSpinnakerBuilder =
        new ImmutableMap.Builder<>();
    ImmutableSetMultimap.Builder<SpinnakerKind, KubernetesKind> spinnakerToKubernetesBuilder =
        new ImmutableSetMultimap.Builder<>();
    for (KubernetesHandler handler : handlers) {
      SpinnakerKind spinnakerKind = handler.spinnakerKind();
      KubernetesKind kubernetesKind = handler.kind();
      kubernetesToSpinnakerBuilder.put(kubernetesKind, spinnakerKind);
      spinnakerToKubernetesBuilder.put(spinnakerKind, kubernetesKind);
    }
    this.kubernetesToSpinnaker = kubernetesToSpinnakerBuilder.build();
    this.spinnakerToKubernetes = spinnakerToKubernetesBuilder.build();
  }

  public SpinnakerKind translateKubernetesKind(KubernetesKind kubernetesKind) {
    return kubernetesToSpinnaker.getOrDefault(kubernetesKind, SpinnakerKind.UNCLASSIFIED);
  }

  public ImmutableSet<KubernetesKind> translateSpinnakerKind(SpinnakerKind spinnakerKind) {
    return spinnakerToKubernetes.get(spinnakerKind);
  }

  public ImmutableSet<KubernetesKind> allKubernetesKinds() {
    return kubernetesToSpinnaker.keySet();
  }

  public Map<String, String> kubernetesToSpinnakerKindStringMap() {
    return kubernetesToSpinnaker.entrySet().stream()
        .filter(x -> !x.getKey().equals(KubernetesKind.NONE))
        .collect(Collectors.toMap(x -> x.getKey().toString(), x -> x.getValue().toString()));
  }
}
