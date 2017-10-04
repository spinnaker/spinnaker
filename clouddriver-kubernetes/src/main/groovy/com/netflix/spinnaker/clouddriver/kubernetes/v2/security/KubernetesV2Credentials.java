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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.diff.JsonDiff;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesKind;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.AppsV1beta1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.models.AppsV1beta1Deployment;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1NetworkPolicy;
import io.kubernetes.client.models.V1beta1NetworkPolicyList;
import io.kubernetes.client.models.V1beta1ReplicaSet;
import io.kubernetes.client.models.V1beta1ReplicaSetList;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import javax.validation.constraints.NotNull;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
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
  private final AppsV1beta1Api appsV1beta1Api;
  private final Registry registry;
  private final Clock clock;
  private final String accountName;
  private final ObjectMapper mapper = new ObjectMapper();
  private final List<String> namespaces;
  private final List<String> omitNamespaces;
  private final String PRETTY = "";
  private final String CONTINUE = null;
  private final boolean EXACT = true;
  private final boolean EXPORT = false;
  private final boolean INCLUDE_UNINITIALIZED = false;
  private final Integer LIMIT = null; // TODO(lwander): include paginination
  private final boolean WATCH = false;
  private final String DEFAULT_VERSION = "0";
  private final int TIMEOUT_SECONDS = 10; // TODO(lwander) make configurable

  @Getter
  private final String defaultNamespace = "default";

  public static class Builder {
    String accountName;
    String kubeconfigFile;
    String context;
    String userAgent;
    List<String> namespaces = new ArrayList<>();
    List<String> omitNamespaces = new ArrayList<>();
    Registry registry;

    public Builder accountName(String accountName) {
      this.accountName = accountName;
      return this;
    }

    public Builder kubeconfigFile(String kubeconfigFile) {
      this.kubeconfigFile = kubeconfigFile;
      return this;
    }

    public Builder context(String context) {
      this.context = context;
      return this;
    }

    public Builder userAgent(String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    public Builder namespaces(List<String> namespaces) {
      this.namespaces = namespaces;
      return this;
    }

    public Builder omitNamespaces(List<String> omitNamespaces) {
      this.omitNamespaces = omitNamespaces;
      return this;
    }

    public Builder registry(Registry registry) {
      this.registry = registry;
      return this;
    }

    public KubernetesV2Credentials build() {
      KubeConfig kubeconfig;
      try {
        if (StringUtils.isEmpty(kubeconfigFile)) {
          kubeconfig = KubeConfig.loadDefaultKubeConfig();
        } else {
          kubeconfig = KubeConfig.loadKubeConfig(new FileReader(kubeconfigFile));
        }
      } catch (FileNotFoundException e) {
        throw new RuntimeException("Unable to create credentials from kubeconfig file: " + e, e);
      }

      if (!StringUtils.isEmpty(context)) {
        kubeconfig.setContext(context);
      }

      ApiClient client = Config.fromConfig(kubeconfig);

      if (!StringUtils.isEmpty(userAgent)) {
        client.setUserAgent(userAgent);
      }

      namespaces = namespaces == null ? new ArrayList<>() : namespaces;
      omitNamespaces = omitNamespaces == null ? new ArrayList<>() : omitNamespaces;
      
      return new KubernetesV2Credentials(accountName, client, namespaces, omitNamespaces, registry);
    }

  }

  private KubernetesV2Credentials(@NotNull String accountName,
      @NotNull ApiClient client,
      @NotNull List<String> namespaces,
      @NotNull List<String> omitNamespaces,
      @NotNull Registry registry) {
    this.registry = registry;
    this.clock = registry.clock();
    this.accountName = accountName;
    this.namespaces = namespaces;
    this.omitNamespaces = omitNamespaces;
    this.client = client;
      // TODO(lwander) make debug mode configurable
    this.client.setDebugging(true);
    this.coreV1Api = new CoreV1Api(this.client);
    this.extensionsV1beta1Api = new ExtensionsV1beta1Api(this.client);
    this.appsV1beta1Api = new AppsV1beta1Api(this.client);
  }

  @Override
  public List<String> getDeclaredNamespaces() {
    List<String> result;
    String labelSelector = null;
    String fieldSelector = null;
    if (!namespaces.isEmpty()) {
      result = namespaces;
    } else {
      try {
        result = coreV1Api.listNamespace(PRETTY, CONTINUE, fieldSelector, INCLUDE_UNINITIALIZED, labelSelector, LIMIT, DEFAULT_VERSION, TIMEOUT_SECONDS, WATCH)
            .getItems()
            .stream()
            .map(n -> n.getMetadata().getName())
            .collect(Collectors.toList());
      } catch (ApiException e) {
        throw new RuntimeException(e);
      }
    }

    if (!omitNamespaces.isEmpty()) {
      result = result.stream()
          .filter(n -> !omitNamespaces.contains(n))
          .collect(Collectors.toList());
    }

    return result;
  }

  private boolean notFound(ApiException e) {
    return e.getCode() == 404;
  }

  private Map[] determineJsonPatch(Object current, Object desired) {
    JsonNode desiredNode = mapper.convertValue(desired, JsonNode.class);
    JsonNode currentNode = mapper.convertValue(current, JsonNode.class);

    return mapper.convertValue(JsonDiff.asJson(currentNode, desiredNode), Map[].class);
  }

  public void createDeployment(AppsV1beta1Deployment deployment) {
    final String methodName = "deployments.create";
    final String namespace = deployment.getMetadata().getNamespace();
    runAndRecordMetrics(methodName, namespace, () -> {
      try {
        return appsV1beta1Api.createNamespacedDeployment(namespace, deployment, null);
      } catch (ApiException e) {
        throw new KubernetesApiException(methodName, e);
      }
    });
  }

  public void patchDeployment(AppsV1beta1Deployment current, AppsV1beta1Deployment desired) {
    final String methodName = "deployments.patch";
    final String namespace = current.getMetadata().getNamespace();
    final String name = current.getMetadata().getName();
    final Map[] jsonPatch = determineJsonPatch(current, desired);
    runAndRecordMetrics(methodName, namespace, () -> {
      try {
        return appsV1beta1Api.patchNamespacedDeployment(name, namespace, jsonPatch, null);
      } catch (ApiException e) {
        throw new KubernetesApiException(methodName, e);
      }
    });
  }

  public AppsV1beta1Deployment readDeployment(String namespace, String name) {
    final String methodName = "deployments.read";
    final KubernetesApiVersion apiVersion = KubernetesApiVersion.APPS_V1BETA1;
    final KubernetesKind kind = KubernetesKind.DEPLOYMENT;
    return runAndRecordMetrics(methodName, namespace, () -> {
      try {
        AppsV1beta1Deployment result = appsV1beta1Api.readNamespacedDeployment(name, namespace, PRETTY, EXACT, EXPORT);
        return annotateMissingFields(result, AppsV1beta1Deployment.class, apiVersion, kind);
      } catch (ApiException e) {
        if (notFound(e)) {
          return null;
        }

        throw new KubernetesApiException(methodName, e);
      }
    });
  }

  public void createIngress(V1beta1Ingress ingress) {
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

  public List<V1beta1NetworkPolicy> listAllNetworkPolicies(String namespace) {
    return listNetworkPolicies(namespace, new KubernetesSelectorList(), new KubernetesSelectorList());
  }

  public List<V1beta1NetworkPolicy> listNetworkPolicies(String namespace, KubernetesSelectorList fieldSelectors, KubernetesSelectorList labelSelectors) {
    final String methodName = "networkPolicies.list";
    final String fieldSelectorString = fieldSelectors.toString();
    final String labelSelectorString = labelSelectors.toString();
    final KubernetesApiVersion apiVersion = KubernetesApiVersion.EXTENSIONS_V1BETA1;
    final KubernetesKind kind = KubernetesKind.NETWORK_POLICY;
    return runAndRecordMetrics(methodName, namespace, () -> {
      try {
        V1beta1NetworkPolicyList list = extensionsV1beta1Api.listNamespacedNetworkPolicy(namespace, PRETTY, CONTINUE, fieldSelectorString, INCLUDE_UNINITIALIZED, labelSelectorString, LIMIT, DEFAULT_VERSION, TIMEOUT_SECONDS, WATCH);
        return annotateMissingFields(list == null ? new ArrayList<>() : list.getItems(),
            V1beta1NetworkPolicy.class,
            apiVersion,
            kind);
      } catch (ApiException e) {
        throw new KubernetesApiException(methodName, e);
      }
    });
  }

  public V1beta1NetworkPolicy readNetworkPolicy(String namespace, String name) {
    final String methodName = "networkPolicies.read";
    final KubernetesApiVersion apiVersion = KubernetesApiVersion.EXTENSIONS_V1BETA1;
    final KubernetesKind kind = KubernetesKind.NETWORK_POLICY;
    return runAndRecordMetrics(methodName, namespace, () -> {
      try {
        V1beta1NetworkPolicy result = extensionsV1beta1Api.readNamespacedNetworkPolicy(name, namespace, PRETTY, EXACT, EXPORT);
        return annotateMissingFields(result, V1beta1NetworkPolicy.class, apiVersion, kind);
      } catch (ApiException e) {
        if (notFound(e)) {
          return null;
        }

        throw new KubernetesApiException(methodName, e);
      }
    });
  }

  public List<V1Pod> listAllPods(String namespace) {
    return listPods(namespace, new KubernetesSelectorList(), new KubernetesSelectorList());
  }

  public List<V1Pod> listPods(String namespace, KubernetesSelectorList fieldSelectors, KubernetesSelectorList labelSelectors) {
    final String methodName = "pods.list";
    final String fieldSelectorString = fieldSelectors.toString();
    final String labelSelectorString = labelSelectors.toString();
    final KubernetesApiVersion apiVersion = KubernetesApiVersion.V1;
    final KubernetesKind kind = KubernetesKind.POD;
    return runAndRecordMetrics(methodName, namespace, () -> {
      try {
        V1PodList list = coreV1Api.listNamespacedPod(namespace, PRETTY, CONTINUE, fieldSelectorString, INCLUDE_UNINITIALIZED, labelSelectorString, LIMIT, DEFAULT_VERSION, TIMEOUT_SECONDS, WATCH);
        return annotateMissingFields(list == null ? new ArrayList<>() : list.getItems(),
            V1Pod.class,
            apiVersion,
            kind);
      } catch (ApiException e) {
        throw new KubernetesApiException(methodName, e);
      }
    });
  }

  public void createReplicaSet(V1beta1ReplicaSet replicaSet) {
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

  public List<V1beta1ReplicaSet> listAllReplicaSets(String namespace) {
    return listReplicaSets(namespace, new KubernetesSelectorList(), new KubernetesSelectorList());
  }

  public List<V1beta1ReplicaSet> listReplicaSets(String namespace, KubernetesSelectorList fieldSelectors, KubernetesSelectorList labelSelectors) {
    final String methodName = "replicaSets.list";
    final String fieldSelectorString = fieldSelectors.toString();
    final String labelSelectorString = labelSelectors.toString();
    final KubernetesApiVersion apiVersion = KubernetesApiVersion.EXTENSIONS_V1BETA1;
    final KubernetesKind kind = KubernetesKind.REPLICA_SET;
    return runAndRecordMetrics(methodName, namespace, () -> {
      try {
        V1beta1ReplicaSetList list = extensionsV1beta1Api.listNamespacedReplicaSet(namespace, PRETTY, CONTINUE, fieldSelectorString, INCLUDE_UNINITIALIZED, labelSelectorString, LIMIT, DEFAULT_VERSION, TIMEOUT_SECONDS, WATCH);
        return annotateMissingFields(list == null ? new ArrayList<>() : list.getItems(),
            V1beta1ReplicaSet.class,
            apiVersion,
            kind);
      } catch (ApiException e) {
        throw new KubernetesApiException(methodName, e);
      }
    });
  }

  public V1beta1ReplicaSet readReplicaSet(String namespace, String name) {
    final String methodName = "replicaSets.read";
    final KubernetesApiVersion apiVersion = KubernetesApiVersion.EXTENSIONS_V1BETA1;
    final KubernetesKind kind = KubernetesKind.REPLICA_SET;
    return runAndRecordMetrics(methodName, namespace, () -> {
      try {
        V1beta1ReplicaSet result = extensionsV1beta1Api.readNamespacedReplicaSet(name, namespace, PRETTY, EXACT, EXPORT);
        return annotateMissingFields(result, V1beta1ReplicaSet.class, apiVersion, kind);
      } catch (ApiException e) {
        if (notFound(e)) {
          return null;
        }

        throw new KubernetesApiException(methodName, e);
      }
    });
  }

  public void createService(V1Service service) {
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
      if (failure == null) {
        tags.put("success", "true");
      } else {
        tags.put("success", "false");
        tags.put("reason", failure.getClass().getSimpleName() + ": " + failure.getMessage());
      }

      registry.timer(registry.createId("kubernetes.api", tags))
          .record(clock.monotonicTime() - startTime, TimeUnit.NANOSECONDS);

      if (failure != null) {
        throw new KubernetesApiException(methodName, failure);
      } else {
        return result;
      }
    }
  }

  private static <T> List<T> annotateMissingFields(List<T> objs, Class<T> clazz, KubernetesApiVersion apiVersion, KubernetesKind kind) {
    return objs.stream()
        .map(obj -> annotateMissingFields(obj, clazz, apiVersion, kind))
        .collect(Collectors.toList());
  }

  private static <T> T annotateMissingFields(T obj, Class<T> clazz, KubernetesApiVersion apiVersion, KubernetesKind kind) {
    try {
      clazz.getMethod("setApiVersion", String.class).invoke(obj, apiVersion == null ? null : apiVersion.toString());
      clazz.getMethod("setKind", String.class).invoke(obj, kind == null ? null : kind.toString());
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new RuntimeException("Unable to set missing fields on " + clazz.getSimpleName(), e);
    }
    return obj;
  }
}
