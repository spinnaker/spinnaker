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

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.model.Health;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import io.kubernetes.client.models.V1ContainerStatus;
import io.kubernetes.client.models.V1PodStatus;
import java.util.Map;
import lombok.Data;

@Data
// TODO(lwander): match spec described here
// https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/
public class KubernetesV2Health implements Health {
  private final HealthState state;
  private final String source;
  private final String type;
  private final String healthClass = "platform";

  public KubernetesV2Health(V1PodStatus status) {
    String phase = status.getPhase();
    this.source = "Pod";
    this.type = "kubernetes/pod";

    if (phase.equalsIgnoreCase("pending")) {
      state = HealthState.Down;
    } else if (phase.equalsIgnoreCase("running")) {
      state = HealthState.Up;
    } else {
      state = HealthState.Unknown;
    }
  }

  public KubernetesV2Health(V1ContainerStatus status) {
    this.source = "Container " + status.getName();
    this.type = "kuberentes/container";

    if (!status.isReady()) {
      state = HealthState.Down;
    } else {
      state = HealthState.Up;
    }
  }

  public Map<String, Object> toMap() {
    return new ImmutableMap.Builder<String, Object>()
        .put("state", state.toString())
        .put("source", source)
        .put("type", type)
        .put(healthClass, healthClass)
        .build();
  }
}
