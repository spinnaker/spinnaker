/*
 * Copyright 2015 Google, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.security;

import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.List;

public class KubernetesCredentials {
  private final KubernetesClient client;
  private final List<String> namespaces;

  public KubernetesCredentials(List<String> namespaces, KubernetesClient client) {
    this.client = client;
    this.namespaces = namespaces;
  }

  public KubernetesClient getClient() {
    return client;
  }

  public List<String> getNamespaces() {
    return namespaces;
  }

  public Boolean isRegisteredNamespace(String namespace) {
    return namespaces != null && namespaces.contains(namespace);
  }
}
