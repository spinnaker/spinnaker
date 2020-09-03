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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.CustomKubernetesCachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.Manifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.Manifest.Status;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import javax.annotation.Nonnull;

public class CustomKubernetesHandlerFactory {
  public static KubernetesHandler create(
      KubernetesKind kubernetesKind,
      SpinnakerKind spinnakerKind,
      boolean versioned,
      int deployPriority) {
    return new Handler(kubernetesKind, spinnakerKind, versioned, deployPriority);
  }

  private static class Handler extends KubernetesHandler {
    private final KubernetesKind kubernetesKind;
    private final SpinnakerKind spinnakerKind;
    private final boolean versioned;
    private final int deployPriority;

    Handler(
        KubernetesKind kubernetesKind,
        SpinnakerKind spinnakerKind,
        boolean versioned,
        int deployPriority) {
      this.kubernetesKind = kubernetesKind;
      this.spinnakerKind = spinnakerKind;
      this.versioned = versioned;
      this.deployPriority = deployPriority;
    }

    @Override
    public int deployPriority() {
      return deployPriority;
    }

    @Nonnull
    @Override
    public KubernetesKind kind() {
      return kubernetesKind;
    }

    @Override
    public boolean versioned() {
      return versioned;
    }

    @Nonnull
    @Override
    public SpinnakerKind spinnakerKind() {
      return spinnakerKind;
    }

    @Override
    public Manifest.Status status(KubernetesManifest manifest) {
      return Status.defaultStatus();
    }

    @Override
    protected KubernetesCachingAgentFactory cachingAgentFactory() {
      return this::buildCustomCachingAgent;
    }

    private KubernetesCachingAgent buildCustomCachingAgent(
        KubernetesNamedAccountCredentials namedAccountCredentials,
        ObjectMapper objectMapper,
        Registry registry,
        int agentIndex,
        int agentCount,
        Long agentInterval) {
      return CustomKubernetesCachingAgentFactory.create(
          kubernetesKind,
          namedAccountCredentials,
          objectMapper,
          registry,
          agentIndex,
          agentCount,
          agentInterval);
    }
  }
}
