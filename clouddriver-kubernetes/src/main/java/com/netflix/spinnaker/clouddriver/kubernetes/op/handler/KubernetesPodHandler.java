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

package com.netflix.spinnaker.clouddriver.kubernetes.op.handler;

import static com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler.DeployPriority.WORKLOAD_PRIORITY;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.Replacer;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.InfrastructureCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.Manifest.Status;
import io.kubernetes.client.openapi.models.V1PodStatus;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class KubernetesPodHandler extends KubernetesHandler {
  @Nonnull
  @Override
  protected ImmutableList<Replacer> artifactReplacers() {
    return ImmutableList.of(Replacer.podDockerImage());
  }

  @Override
  public int deployPriority() {
    return WORKLOAD_PRIORITY.getValue();
  }

  @Nonnull
  @Override
  public KubernetesKind kind() {
    return KubernetesKind.POD;
  }

  @Override
  public boolean versioned() {
    return true;
  }

  @Nonnull
  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.INSTANCES;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    V1PodStatus status =
        KubernetesCacheDataConverter.getResource(manifest.getStatus(), V1PodStatus.class);

    if (status == null) {
      return Status.noneReported();
    }

    PodPhase phase = PodPhase.fromString(status.getPhase());
    if (phase.isUnstable()) {
      return Status.defaultStatus().unstable(phase.getMessage()).unavailable(phase.getMessage());
    }

    return Status.defaultStatus();
  }

  private enum PodPhase {
    // https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/
    Pending(true, "Pod is pending"),
    Running(false, ""),
    Succeeded(false, ""),
    Failed(true, "Pod has failed"),
    Unknown(true, "Pod phase is unknown");

    @Getter private final String message;
    @Getter private final boolean unstable;

    PodPhase(boolean unstable, String message) {
      this.message = message;
      this.unstable = unstable;
    }

    static PodPhase fromString(@Nullable String phase) {
      if (phase == null) {
        return Unknown;
      }
      try {
        return valueOf(phase);
      } catch (IllegalArgumentException e) {
        return Unknown;
      }
    }
  }

  @Override
  public Map<String, Object> hydrateSearchResult(InfrastructureCacheKey key) {
    Map<String, Object> result = super.hydrateSearchResult(key);
    result.put("instanceId", result.get("name"));

    return result;
  }

  @Override
  protected KubernetesCachingAgentFactory cachingAgentFactory() {
    return KubernetesCoreCachingAgent::new;
  }
}
