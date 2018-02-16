/*
 * Copyright 2018 Google, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.CustomKubernetesCachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.Manifest;

public class CustomKubernetesHandlerFactory {
  public static KubernetesHandler create(KubernetesKind kubernetesKind, SpinnakerKind spinnakerKind, boolean versioned) {
    return new Handler(kubernetesKind, spinnakerKind, versioned);
  }

  private static class Handler extends KubernetesHandler {
    private final KubernetesKind kubernetesKind;
    private final SpinnakerKind spinnakerKind;
    private final boolean versioned;

    Handler(KubernetesKind kubernetesKind, SpinnakerKind spinnakerKind, boolean versioned) {
      this.kubernetesKind = kubernetesKind;
      this.spinnakerKind = spinnakerKind;
      this.versioned = versioned;
    }

    @Override
    public KubernetesKind kind() {
      return kubernetesKind;
    }

    @Override
    public boolean versioned() {
      return versioned;
    }

    @Override
    public SpinnakerKind spinnakerKind() {
      return spinnakerKind;
    }

    @Override
    public Manifest.Status status(KubernetesManifest manifest) {
      return new Manifest.Status();
    }

    @Override
    public KubernetesV2CachingAgent buildCachingAgent(
        KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
        ObjectMapper objectMapper,
        Registry registry,
        int agentIndex,
        int agentCount
    ) {
      return CustomKubernetesCachingAgentFactory.create(
          kubernetesKind,
          namedAccountCredentials,
          objectMapper,
          registry,
          agentIndex,
          agentCount
      );
    }
  }
}
