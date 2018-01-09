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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor.KubectlException;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.util.KubeConfig;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import javax.validation.constraints.NotNull;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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

  // remove when kubectl is no longer a dependency
  @Getter
  private final String kubeconfigFile;

  // remove when kubectl is no longer a dependency
  @Getter
  private final String context;

  @JsonIgnore
  @Getter
  private final String oAuthServiceAccount;

  @JsonIgnore
  @Getter
  private final List<String> oAuthScopes;

  private final String defaultNamespace = "default";
  private final Path serviceAccountNamespacePath = Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/namespace");
  public String getDefaultNamespace() {
    String namespace = defaultNamespace;
    try {
      Optional<String> serviceAccountNamespace = Files.lines(serviceAccountNamespacePath, StandardCharsets.UTF_8).findFirst();
      namespace = serviceAccountNamespace.get();
    } catch (IOException | NoSuchElementException e) {
      try {
        namespace = jobExecutor.defaultNamespace(this);
      } catch (KubectlException e1) {
      }
    }
    if (StringUtils.isEmpty(namespace)) {
      namespace = defaultNamespace;
    }
    return namespace;
  }

  @Getter
  private final boolean debug;

  public static class Builder {
    String accountName;
    String kubeconfigFile;
    String context;
    String oAuthServiceAccount;
    List<String> oAuthScopes;
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

    public Builder oAuthServiceAccount(String oAuthServiceAccount) {
      this.oAuthServiceAccount = oAuthServiceAccount;
      return this;
    }

    public Builder oAuthScopes(List<String> oAuthScopes) {
      this.oAuthScopes = oAuthScopes;
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
          oAuthServiceAccount,
          oAuthScopes,
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
      String oAuthServiceAccount,
      List<String> oAuthScopes,
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
    this.oAuthServiceAccount = oAuthServiceAccount;
    this.oAuthScopes = oAuthScopes;
  }

  @Override
  public List<String> getDeclaredNamespaces() {
    List<String> result;
    if (!namespaces.isEmpty()) {
      result = namespaces;
    } else {
      try {
        List<KubernetesManifest> namespaceManifests = jobExecutor.list(this, KubernetesKind.NAMESPACE, "");
        result = namespaceManifests.stream()
            .map(KubernetesManifest::getName)
            .collect(Collectors.toList());

      } catch (KubectlException e) {
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

  public KubernetesManifest get(KubernetesKind kind, String namespace, String name) {
    return runAndRecordMetrics("get", kind, namespace, () -> jobExecutor.get(this, kind, namespace, name));
  }

  public List<KubernetesManifest> list(KubernetesKind kind, String namespace) {
    return runAndRecordMetrics("list", kind, namespace, () -> jobExecutor.list(this, kind, namespace));
  }

  public String logs(String namespace, String podName, String containerName) {
    return runAndRecordMetrics("logs", KubernetesKind.POD, namespace, () -> jobExecutor.logs(this, namespace, podName, containerName));
  }

  public void scale(KubernetesKind kind, String namespace, String name, int replicas) {
    runAndRecordMetrics("scale", kind, namespace, () -> jobExecutor.scale(this, kind, namespace, name, replicas));
  }

  public List<String> delete(KubernetesKind kind, String namespace, String name, KubernetesSelectorList labelSelectors, V1DeleteOptions options) {
    return runAndRecordMetrics("scale", kind, namespace, () -> jobExecutor.delete(this, kind, namespace, name, labelSelectors, options));
  }

  public void deploy(KubernetesManifest manifest) {
    runAndRecordMetrics("deploy", manifest.getKind(), manifest.getNamespace(), () -> jobExecutor.deploy(this, manifest));
  }

  public List<Integer> historyRollout(KubernetesKind kind, String namespace, String name) {
    return runAndRecordMetrics("historyRollout", kind, namespace, () -> jobExecutor.historyRollout(this, kind, namespace, name));
  }

  public void undoRollout(KubernetesKind kind, String namespace, String name, int revision) {
    runAndRecordMetrics("undoRollout", kind, namespace, () -> jobExecutor.undoRollout(this, kind, namespace, name, revision));
  }

  public void pauseRollout(KubernetesKind kind, String namespace, String name) {
    runAndRecordMetrics("pauseRollout", kind, namespace, () -> jobExecutor.pauseRollout(this, kind, namespace, name));
  }

  public void resumeRollout(KubernetesKind kind, String namespace, String name) {
    runAndRecordMetrics("resumeRollout", kind, namespace, () -> jobExecutor.resumeRollout(this, kind, namespace, name));
  }

  private <T> T runAndRecordMetrics(String action, KubernetesKind kind, String namespace, Supplier<T> op) {
    T result = null;
    Throwable failure = null;
    KubectlException apiException = null;
    long startTime = clock.monotonicTime();
    try {
      result = op.get();
    } catch (KubectlException e) {
      apiException = e;
    } catch (Exception e) {
      failure = e;
    } finally {
      Map<String, String> tags = new HashMap<>();
      tags.put("action", action);
      tags.put("kind", kind.toString());
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
        throw new KubectlJobExecutor.KubectlException("Failure running " + action + " on " + kind + ": " + failure.getMessage(), failure);
      } else if (apiException != null) {
        throw apiException;
      } else {
        return result;
      }
    }
  }
}
