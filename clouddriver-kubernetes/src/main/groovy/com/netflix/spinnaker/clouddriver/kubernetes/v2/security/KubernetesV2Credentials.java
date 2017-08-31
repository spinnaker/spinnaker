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

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1ReplicaSet;
import io.kubernetes.client.util.Config;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class KubernetesV2Credentials implements KubernetesCredentials {
  private final ApiClient client;
  private final CoreV1Api coreV1Api;
  private final ExtensionsV1beta1Api extensionsV1beta1Api;
  private final Registry registry;
  private final Clock clock;
  private final String accountName;

  @Getter
  private final String defaultNamespace = "default";

  public KubernetesV2Credentials(String accountName, Registry registry) {
    this.registry = registry;
    this.clock = registry.clock();
    this.accountName = accountName;
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
    final String methodName = "replicaSets.create";
    final String namespace = replicaSet.getMetadata().getNamespace();
    runAndRecordMetrics(methodName, namespace, () -> {
      try {
        return extensionsV1beta1Api.createNamespacedReplicaSet(namespace, replicaSet, null);
      } catch (ApiException e) {
        throw new KubernetesApiException(methodName, e);
      }
    });
  }

  public void deployService(V1Service service) {
    final String methodName = "services.create";
    final String namespace = service.getMetadata().getNamespace();
    runAndRecordMetrics(methodName, namespace, () -> {
      try {
        return coreV1Api.createNamespacedService(namespace, service, null);
      } catch (ApiException e) {
        throw new KubernetesApiException(methodName, e);
      }
    });
  }

  public void deployIngress(V1beta1Ingress ingress) {
    final String methodName = "ingresses.create";
    final String namespace = ingress.getMetadata().getNamespace();
    runAndRecordMetrics(methodName, namespace, () -> {
      try {
        return extensionsV1beta1Api.createNamespacedIngress(namespace, ingress, null);
      } catch (ApiException e) {
        throw new KubernetesApiException(methodName, e);
      }
    });
  }

  private <T> T runAndRecordMetrics(String methodName, String namespace, Supplier<T> op) {
    T result = null;
    Throwable failure = null;
    long startTime = clock.monotonicTime();
    try {
      result = op.get();
    } catch (KubernetesApiException e) {
      failure = e.getCause();
    } catch (Exception e) {
      failure = e;
    } finally {
      Map<String, String> tags = new HashMap<>();
      tags.put("method", methodName);
      tags.put("account", accountName);
      tags.put("namespace", StringUtils.isEmpty(namespace) ? "none" : namespace);
      tags.put("success", failure == null ? "true" : "false");
      tags.put("reason", failure == null ? null : failure.getClass().getSimpleName() + ": " + failure.getMessage());

      registry.timer(registry.createId("kubernetes.api", tags))
          .record(clock.monotonicTime() - startTime, TimeUnit.NANOSECONDS);

      if (failure != null) {
        throw new KubernetesApiException(methodName, failure);
      } else {
        return result;
      }
    }
  }
}
