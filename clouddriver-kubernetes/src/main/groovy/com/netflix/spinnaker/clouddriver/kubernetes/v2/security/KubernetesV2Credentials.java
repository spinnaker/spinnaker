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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Service;
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
  private final KubectlJobExecutor jobExecutor;
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

  // remove when kubectl is no longer a dependency
  @Getter
  private final String kubeconfigFile;

  // remove when kubectl is no longer a dependency
  @Getter
  private final String context;

  @Getter
  private final String defaultNamespace = "default";

  @Getter
  private final boolean debug;

  public static class Builder {
    String accountName;
    String kubeconfigFile;
    String context;
    String userAgent;
    List<String> namespaces = new ArrayList<>();
    List<String> omitNamespaces = new ArrayList<>();
    Registry registry;
    KubectlJobExecutor jobExecutor;
    boolean debug;

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

    public Builder jobExecutor(KubectlJobExecutor jobExecutor) {
      this.jobExecutor = jobExecutor;
      return this;
    }

    public Builder debug(boolean debug) {
      this.debug = debug;
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

      namespaces = namespaces == null ? new ArrayList<>() : namespaces;
      omitNamespaces = omitNamespaces == null ? new ArrayList<>() : omitNamespaces;
      
      return new KubernetesV2Credentials(
          accountName,
          jobExecutor,
          namespaces,
          omitNamespaces,
          registry,
          kubeconfigFile,
          context,
          debug
      );
    }
  }

  private KubernetesV2Credentials(@NotNull String accountName,
      @NotNull KubectlJobExecutor jobExecutor,
      @NotNull List<String> namespaces,
      @NotNull List<String> omitNamespaces,
      @NotNull Registry registry,
      String kubeconfigFile,
      String context,
      boolean debug) {
    this.registry = registry;
    this.clock = registry.clock();
    this.accountName = accountName;
    this.namespaces = namespaces;
    this.omitNamespaces = omitNamespaces;
    this.jobExecutor = jobExecutor;
    this.debug = debug;

    this.kubeconfigFile = kubeconfigFile;
    this.context = context;
  }

  @Override
  public List<String> getDeclaredNamespaces() {
    List<String> result;
    if (!namespaces.isEmpty()) {
      result = namespaces;
    } else {
      try {
        List<KubernetesManifest> namespaceManifests = jobExecutor.getAll(this, KubernetesKind.NAMESPACE, "");
        result = namespaceManifests.stream()
            .map(KubernetesManifest::getName)
            .collect(Collectors.toList());

      } catch (KubectlJobExecutor.KubectlException e) {
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

  public KubernetesSelectorList labelSelectorList(V1Service service) {
    KubernetesSelectorList list = new KubernetesSelectorList();
    for (Map.Entry<String, String> e : service.getSpec().getSelector().entrySet()) {
      list.addSelector(KubernetesSelector.equals(e.getKey(), e.getValue()));
    }

    return list;
  }

  private <T> T runAndRecordMetrics(String methodName, String namespace, Supplier<T> op) {
    T result = null;
    Throwable failure = null;
    KubernetesApiException apiException = null;
    long startTime = clock.monotonicTime();
    try {
      result = op.get();
    } catch (KubernetesApiException e) {
      apiException = e;
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
      } else if (apiException != null) {
        throw apiException;
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
