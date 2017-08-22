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

import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.util.Config;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class KubernetesV2Credentials implements KubernetesCredentials {
  private final ApiClient client;
  private final CoreV1Api coreV1Api;

  public KubernetesV2Credentials() {
    try {
      // TODO(lwander) initialize client based on provided config
      client = Config.defaultClient();
      coreV1Api = new CoreV1Api(client);
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
}
