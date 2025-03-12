/*
 * Copyright 2018 Google, Inc.
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

import static com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler.DeployPriority.WORKLOAD_ATTACHMENT_PRIORITY;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.Replacer;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.Manifest.Status;
import io.kubernetes.client.openapi.models.V1HorizontalPodAutoscaler;
import io.kubernetes.client.openapi.models.V1HorizontalPodAutoscalerStatus;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.stereotype.Component;

@Component
public class KubernetesHorizontalPodAutoscalerHandler extends KubernetesHandler {
  @Nonnull
  @Override
  protected ImmutableList<Replacer> artifactReplacers() {
    return ImmutableList.of(Replacer.hpaDeployment(), Replacer.hpaReplicaSet());
  }

  @Override
  public int deployPriority() {
    return WORKLOAD_ATTACHMENT_PRIORITY.getValue();
  }

  @Nonnull
  @Override
  public KubernetesKind kind() {
    return KubernetesKind.HORIZONTAL_POD_AUTOSCALER;
  }

  @Override
  public boolean versioned() {
    return false;
  }

  @Nonnull
  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.UNCLASSIFIED;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    V1HorizontalPodAutoscaler hpa =
        KubernetesCacheDataConverter.getResource(manifest, V1HorizontalPodAutoscaler.class);
    return status(hpa);
  }

  private Status status(V1HorizontalPodAutoscaler hpa) {
    V1HorizontalPodAutoscalerStatus status = hpa.getStatus();
    if (status == null) {
      return Status.noneReported();
    }

    int desiredReplicas = defaultToZero(status.getDesiredReplicas());
    int existing = defaultToZero(status.getCurrentReplicas());
    if (desiredReplicas > existing) {
      return Status.defaultStatus()
          .unstable(
              String.format(
                  "Waiting for HPA to complete a scale up, current: %d desired: %d",
                  existing, desiredReplicas));
    }

    if (desiredReplicas < existing) {
      return Status.defaultStatus()
          .unstable(
              String.format(
                  "Waiting for HPA to complete a scale down, current: %d desired: %d",
                  existing, desiredReplicas));
    }
    // desiredReplicas == existing, this is now stable
    return Status.defaultStatus();
  }

  // Unboxes an Integer, returning 0 if the input is null
  private static int defaultToZero(@Nullable Integer input) {
    return input == null ? 0 : input;
  }

  @Override
  protected KubernetesCachingAgentFactory cachingAgentFactory() {
    return KubernetesCoreCachingAgent::new;
  }
}
