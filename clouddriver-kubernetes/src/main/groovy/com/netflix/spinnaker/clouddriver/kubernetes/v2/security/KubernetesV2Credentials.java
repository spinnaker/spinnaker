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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.security;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.models.V1beta1ReplicaSet;
import io.kubernetes.client.util.Config;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class KubernetesV2Credentials implements KubernetesCredentials {
  private final ApiClient client;
  private final CoreV1Api coreV1Api;
  private final ExtensionsV1beta1Api extensionsV1beta1Api;
  private final Registry registry;

  @Getter
  private final String defaultNamespace = "default";

  public KubernetesV2Credentials(Registry registry) {
    // TODO(lwander) wire this in
    this.registry = registry;
    try {
      // TODO(lwander) initialize client based on provided config
      client = Config.defaultClient();
      client.setDebugging(true);
      coreV1Api = new CoreV1Api(client);
      extensionsV1beta1Api = new ExtensionsV1beta1Api(client);
    } catch (IOException e) {
      throw new RuntimeException("Failed to instantiate Kubernetes credentials", e);
    }
  }

  @Override
  public List<String> getNamespaces() {
    try {
      return coreV1Api.listNamespace(null, null, null, null, 10, null)
          .getItems()
          .stream()
          .map(n -> n.getMetadata().getName())
          .collect(Collectors.toList());
    } catch (ApiException e) {
      throw new RuntimeException(e);
    }
  }

  public void deployReplicaSet(V1beta1ReplicaSet replicaSet) {
    final String operation = "replicaSets.create";
    try {
      extensionsV1beta1Api.createNamespacedReplicaSet(replicaSet.getMetadata().getNamespace(), replicaSet, null);
    } catch (ApiException e) {
      throw new KubernetesApiException(operation, e);
    }
  }
}
