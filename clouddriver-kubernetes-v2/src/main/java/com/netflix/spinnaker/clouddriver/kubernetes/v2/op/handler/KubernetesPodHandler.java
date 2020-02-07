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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler.DeployPriority.WORKLOAD_PRIORITY;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.Replacer;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.InfrastructureCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.model.Manifest.Status;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import java.util.Map;
import javax.annotation.Nonnull;
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
    V1Pod pod = KubernetesCacheDataConverter.getResource(manifest, V1Pod.class);
    V1PodStatus status = pod.getStatus();

    if (status == null) {
      return Status.defaultStatus()
          .unstable("No status reported yet")
          .unavailable("No availability reported");
    }

    // https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/
    String phase = status.getPhase();

    // This code appears to be broken, as Kubernetes always reports pod status in initial caps
    // and we're comparing to lower-case strings. We'll thus never enter any of the else-if cases.
    // TODO(ezimanyi): Fix this when cleaning up status reporting code
    if (phase == null) {
      return Status.defaultStatus()
          .unstable("No phase reported yet")
          .unavailable("No availability reported");
    } else if (phase.equals("pending")) {
      return Status.defaultStatus()
          .unstable("Pod is 'pending'")
          .unavailable("Pod has not been scheduled yet");
    } else if (phase.equals("unknown")) {
      return Status.defaultStatus()
          .unstable("Pod has 'unknown' phase")
          .unavailable("No availability reported");
    } else if (phase.equals("failed")) {
      return Status.defaultStatus().failed("Pod has 'failed'").unavailable("Pod is not running");
    }

    return Status.defaultStatus();
  }

  @Override
  public Map<String, Object> hydrateSearchResult(InfrastructureCacheKey key) {
    Map<String, Object> result = super.hydrateSearchResult(key);
    result.put("instanceId", result.get("name"));

    return result;
  }

  @Override
  protected KubernetesV2CachingAgentFactory cachingAgentFactory() {
    return KubernetesCoreCachingAgent::new;
  }
}
