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
import com.google.common.base.Suppliers;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.config.CustomKubernetesResource;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesCachingPolicy;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPatchOptions;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor.KubectlException;
import io.kubernetes.client.models.V1DeleteOptions;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
public class KubernetesV2Credentials implements KubernetesCredentials {
  private final KubectlJobExecutor jobExecutor;
  private final Registry registry;
  private final Clock clock;
  private final String accountName;
  @Getter
  private final List<String> namespaces;
  @Getter
  private final List<String> omitNamespaces;
  private final List<KubernetesKind> kinds;
  private final List<KubernetesKind> omitKinds;
  @Getter private final boolean serviceAccount;
  @Getter private final List<KubernetesCachingPolicy> cachingPolicies;

  // TODO(lwander) make configurable
  private final static int namespaceExpirySeconds = 30;

  private final com.google.common.base.Supplier<List<String>> liveNamespaceSupplier;

  @Getter
  private final List<CustomKubernetesResource> customResources;

  // remove when kubectl is no longer a dependency
  @Getter
  private final String kubectlExecutable;

  @Getter
  private final Integer kubectlRequestTimeoutSeconds;

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
  private String cachedDefaultNamespace;

  private final Path serviceAccountNamespacePath = Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/namespace");

  public boolean isValidKind(KubernetesKind kind) {
    if (kind == KubernetesKind.NONE) {
      return false;
    } else if (!this.kinds.isEmpty()) {
      return kinds.contains(kind);
    } else if (!this.omitKinds.isEmpty()) {
      return !omitKinds.contains(kind);
    } else {
      return true;
    }
  }

  public String getDefaultNamespace() {
    if (StringUtils.isEmpty(cachedDefaultNamespace)) {
      cachedDefaultNamespace = lookupDefaultNamespace();
    }

    return cachedDefaultNamespace;
  }

  public String lookupDefaultNamespace() {
    String namespace = defaultNamespace;
    try {
      Optional<String> serviceAccountNamespace = Files.lines(serviceAccountNamespacePath, StandardCharsets.UTF_8).findFirst();
      namespace = serviceAccountNamespace.orElse("");
    } catch (IOException e) {
      try {
        namespace = jobExecutor.defaultNamespace(this);
      } catch (KubectlException ke) {
        log.debug("Failure looking up desired namespace, defaulting to {}", defaultNamespace, ke);
      }
    } catch (Exception e) {
      log.debug("Error encountered looking up default namespace, defaulting to {}", defaultNamespace, e);
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
    String kubectlExecutable;
    Integer kubectlRequestTimeoutSeconds;
    String oAuthServiceAccount;
    List<String> oAuthScopes;
    String userAgent;
    List<String> namespaces = new ArrayList<>();
    List<String> omitNamespaces = new ArrayList<>();
    Registry registry;
    KubectlJobExecutor jobExecutor;
    List<CustomKubernetesResource> customResources;
    List<KubernetesCachingPolicy> cachingPolicies;
    List<String> kinds;
    List<String> omitKinds;
    boolean debug;
    boolean serviceAccount;

    public Builder accountName(String accountName) {
      this.accountName = accountName;
      return this;
    }

    public Builder kubeconfigFile(String kubeconfigFile) {
      this.kubeconfigFile = kubeconfigFile;
      return this;
    }

    public Builder kubectlExecutable(String kubectlExecutable) {
      this.kubectlExecutable = kubectlExecutable;
      return this;
    }

    public Builder kubectlRequestTimeoutSeconds(Integer kubectlRequestTimeoutSeconds) {
      this.kubectlRequestTimeoutSeconds = kubectlRequestTimeoutSeconds;
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

    public Builder cachingPolicies(List<KubernetesCachingPolicy> cachingPolicies) {
      this.cachingPolicies = cachingPolicies;
      return this;
    }

    public Builder customResources(List<CustomKubernetesResource> customResources) {
      this.customResources = customResources;
      return this;
    }

    public Builder debug(boolean debug) {
      this.debug = debug;
      return this;
    }

    public Builder serviceAccount(boolean serviceAccount) {
      this.serviceAccount = serviceAccount;
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

    public Builder kinds(List<String> kinds) {
      this.kinds = kinds;
      return this;
    }

    public Builder omitKinds(List<String> omitKinds) {
      this.omitKinds = omitKinds;
      return this;
    }

    public KubernetesV2Credentials build() {
      namespaces = namespaces == null ? new ArrayList<>() : namespaces;
      omitNamespaces = omitNamespaces == null ? new ArrayList<>() : omitNamespaces;
      customResources = customResources == null ? new ArrayList<>() : customResources;
      kinds = kinds == null ? new ArrayList<>() : kinds;
      omitKinds = omitKinds == null ? new ArrayList<>() : omitKinds;
      cachingPolicies = cachingPolicies == null ? new ArrayList<>() : cachingPolicies;

      return new KubernetesV2Credentials(
          accountName,
          jobExecutor,
          namespaces,
          omitNamespaces,
          registry,
          kubeconfigFile,
          kubectlExecutable,
          kubectlRequestTimeoutSeconds,
          context,
          oAuthServiceAccount,
          oAuthScopes,
          serviceAccount,
          customResources,
          cachingPolicies,
          KubernetesKind.registeredStringList(kinds),
          KubernetesKind.registeredStringList(omitKinds),
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
      String kubectlExecutable,
      Integer kubectlRequestTimeoutSeconds,
      String context,
      String oAuthServiceAccount,
      List<String> oAuthScopes,
      boolean serviceAccount,
      @NotNull List<CustomKubernetesResource> customResources,
      @NotNull List<KubernetesCachingPolicy> cachingPolicies,
      @NotNull List<KubernetesKind> kinds,
      @NotNull List<KubernetesKind> omitKinds,
      boolean debug) {
    this.registry = registry;
    this.clock = registry.clock();
    this.accountName = accountName;
    this.namespaces = namespaces;
    this.omitNamespaces = omitNamespaces;
    this.jobExecutor = jobExecutor;
    this.debug = debug;
    this.kubectlExecutable = kubectlExecutable;
    this.kubectlRequestTimeoutSeconds = kubectlRequestTimeoutSeconds;
    this.kubeconfigFile = kubeconfigFile;
    this.context = context;
    this.oAuthServiceAccount = oAuthServiceAccount;
    this.oAuthScopes = oAuthScopes;
    this.serviceAccount = serviceAccount;
    this.customResources = customResources;
    this.cachingPolicies = cachingPolicies;
    this.kinds = kinds;
    this.omitKinds = omitKinds;

    this.liveNamespaceSupplier = Suppliers.memoizeWithExpiration(() -> jobExecutor.list(this, Collections.singletonList(KubernetesKind.NAMESPACE), "")
        .stream()
        .map(KubernetesManifest::getName)
        .collect(Collectors.toList()), namespaceExpirySeconds, TimeUnit.SECONDS);

    determineOmitKinds();
  }

  @Override
  public List<String> getDeclaredNamespaces() {
    List<String> result;
    if (!namespaces.isEmpty()) {
      result = namespaces;
    } else {
      try {
        result = liveNamespaceSupplier.get();

      } catch (KubectlException e) {
        log.warn("Could not list namespaces for account {}: {}", accountName, e.getMessage());
        return new ArrayList<>();
      }
    }

    if (!omitNamespaces.isEmpty()) {
      result = result.stream()
          .filter(n -> !omitNamespaces.contains(n))
          .collect(Collectors.toList());
    }

    return result;
  }

  private void determineOmitKinds() {
    List<String> namespaces = getDeclaredNamespaces();

    if (namespaces.isEmpty()) {
      log.warn("There are no namespaces configured (or loadable) -- please check that the list of 'omitNamespaces' for account '"
          + accountName +"' doesn't prevent access from all namespaces in this cluster, or that the cluster is reachable.");
      return;
    }

    // we are making the assumption that the roles granted to spinnaker for this account in all namespaces are identical.
    // otherwise, checking all namespaces for all kinds is too expensive in large clusters (imagine a cluster with 100s of namespaces).
    String checkNamespace = namespaces.get(0);
    List<KubernetesKind> allKinds = KubernetesKind.getValues();

    log.info("Checking permissions on configured kinds for account {}... {}", accountName, allKinds);
    for (KubernetesKind kind : allKinds) {
      if (kind == KubernetesKind.NONE || omitKinds.contains(kind)) {
        continue;
      }

      try {
        log.info("Checking if {} is readable...", kind);
        if (kind.isNamespaced()) {
          list(kind, checkNamespace);
        } else {
          list(kind, null);
        }
      } catch (Exception e) {
        log.info("Kind '{}' will not be cached in account '{}' for reason: '{}'", kind, accountName, e.getMessage());
        log.debug("Reading kind '{}' failed with exception: ", kind, e);
        omitKinds.add(kind);
      }
    }
  }

  public KubernetesManifest get(KubernetesKind kind, String namespace, String name) {
    return runAndRecordMetrics("get", kind, namespace, () -> jobExecutor.get(this, kind, namespace, name));
  }

  public List<KubernetesManifest> list(KubernetesKind kind, String namespace) {
    return runAndRecordMetrics("list", kind, namespace, () -> jobExecutor.list(this, Collections.singletonList(kind), namespace));
  }

  public List<KubernetesManifest> list(List<KubernetesKind> kinds, String namespace) {
    if (kinds.isEmpty()) {
      return new ArrayList<>();
    } else {
      return runAndRecordMetrics("list", kinds, namespace, () -> jobExecutor.list(this, kinds, namespace));
    }
  }

  public String logs(String namespace, String podName, String containerName) {
    return runAndRecordMetrics("logs", KubernetesKind.POD, namespace, () -> jobExecutor.logs(this, namespace, podName, containerName));
  }

  public void scale(KubernetesKind kind, String namespace, String name, int replicas) {
    runAndRecordMetrics("scale", kind, namespace, () -> jobExecutor.scale(this, kind, namespace, name, replicas));
  }

  public List<String> delete(KubernetesKind kind, String namespace, String name, KubernetesSelectorList labelSelectors, V1DeleteOptions options) {
    return runAndRecordMetrics("delete", kind, namespace, () -> jobExecutor.delete(this, kind, namespace, name, labelSelectors, options));
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

  public void patch(KubernetesKind kind, String namespace, String name, KubernetesPatchOptions options,
    KubernetesManifest manifest) {
    runAndRecordMetrics("patch", kind, namespace, () -> jobExecutor.patch(this, kind, namespace, name, options, manifest));
  }

  private <T> T runAndRecordMetrics(String action, KubernetesKind kind, String namespace, Supplier<T> op) {
    return runAndRecordMetrics(action, Collections.singletonList(kind), namespace, op);
  }

  private <T> T runAndRecordMetrics(String action, List<KubernetesKind> kinds, String namespace, Supplier<T> op) {
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
      if (kinds.size() == 1) {
        tags.put("kind", kinds.get(0).toString());
      } else {
        tags.put("kinds", String.join(",", kinds.stream().map(KubernetesKind::toString).collect(Collectors.toList())));
      }
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
        throw new KubectlJobExecutor.KubectlException("Failure running " + action + " on " + kinds + ": " + failure.getMessage(), failure);
      } else if (apiException != null) {
        throw apiException;
      } else {
        return result;
      }
    }
  }
}
