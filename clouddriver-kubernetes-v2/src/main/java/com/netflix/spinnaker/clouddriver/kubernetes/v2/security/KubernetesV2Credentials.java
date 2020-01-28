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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static lombok.EqualsAndHashCode.Include;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.config.CustomKubernetesResource;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesCachingPolicy;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubeconfigFileHasher;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentialFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.AccountResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.JsonPatch;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPatchOptions;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor.KubectlException;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.kork.configserver.ConfigFileService;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Slf4j
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class KubernetesV2Credentials implements KubernetesCredentials {
  private static final int CRD_EXPIRY_SECONDS = 30;
  private static final int NAMESPACE_EXPIRY_SECONDS = 30;
  private static final Path SERVICE_ACCOUNT_NAMESPACE_PATH =
      Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/namespace");
  private static final String DEFAULT_NAMESPACE = "default";

  private final Registry registry;
  private final Clock clock;
  private final KubectlJobExecutor jobExecutor;

  @Include @Getter private final String accountName;

  @Include @Getter private final ImmutableList<String> namespaces;

  @Include @Getter private final ImmutableList<String> omitNamespaces;

  @Include private final ImmutableSet<KubernetesKind> kinds;

  @Include private final ImmutableSet<KubernetesKind> omitKinds;

  @Include @Getter private final List<CustomKubernetesResource> customResources;

  @Include @Getter private final String kubectlExecutable;

  @Include @Getter private final Integer kubectlRequestTimeoutSeconds;

  @Getter private final String kubeconfigFile;

  @Include private final String kubeconfigFileHash;

  @Include @Getter private final boolean serviceAccount;

  @Include @Getter private final String context;

  @Include @Getter private final boolean onlySpinnakerManaged;

  @Include @Getter private final boolean liveManifestCalls;

  @Include private final boolean checkPermissionsOnStartup;

  @Include @Getter private final List<KubernetesCachingPolicy> cachingPolicies;

  @Include @JsonIgnore @Getter private final String oAuthServiceAccount;

  @Include @JsonIgnore @Getter private final List<String> oAuthScopes;

  @Include private boolean metrics;

  @Include @Getter private final boolean debug;

  private String cachedDefaultNamespace;
  @Getter private final ResourcePropertyRegistry resourcePropertyRegistry;
  @Getter private final KubernetesKindRegistry kindRegistry;
  private final KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap;
  private final PermissionValidator permissionValidator;
  private final Supplier<ImmutableMap<KubernetesKind, KubernetesKindProperties>> crdSupplier =
      Suppliers.memoizeWithExpiration(this::crdSupplier, CRD_EXPIRY_SECONDS, TimeUnit.SECONDS);
  private final Supplier<ImmutableList<String>> liveNamespaceSupplier =
      Memoizer.memoizeWithExpiration(
          this::namespaceSupplier, NAMESPACE_EXPIRY_SECONDS, TimeUnit.SECONDS);

  private KubernetesV2Credentials(
      Registry registry,
      KubectlJobExecutor jobExecutor,
      KubernetesConfigurationProperties.ManagedAccount managedAccount,
      AccountResourcePropertyRegistry.Factory resourcePropertyRegistryFactory,
      KubernetesKindRegistry.Factory kindRegistryFactory,
      KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap,
      String kubeconfigFile) {
    this.registry = registry;
    this.clock = registry.clock();
    this.jobExecutor = jobExecutor;
    this.kindRegistry =
        kindRegistryFactory.create(
            this::getCrdProperties,
            managedAccount.getCustomResources().stream()
                .map(
                    cr ->
                        KubernetesKindProperties.create(
                            KubernetesKind.fromString(cr.getKubernetesKind()), cr.isNamespaced()))
                .collect(toImmutableList()));

    this.accountName = managedAccount.getName();
    this.namespaces = ImmutableList.copyOf(managedAccount.getNamespaces());
    this.omitNamespaces = ImmutableList.copyOf(managedAccount.getOmitNamespaces());
    this.kinds =
        managedAccount.getKinds().stream()
            .map(KubernetesKind::fromString)
            .collect(toImmutableSet());
    this.omitKinds =
        managedAccount.getOmitKinds().stream()
            .map(KubernetesKind::fromString)
            .collect(toImmutableSet());
    this.permissionValidator = new PermissionValidator();

    this.customResources = managedAccount.getCustomResources();
    this.resourcePropertyRegistry =
        resourcePropertyRegistryFactory.create(
            managedAccount.getCustomResources().stream()
                .map(KubernetesResourceProperties::fromCustomResource)
                .collect(toImmutableList()));
    this.kubernetesSpinnakerKindMap = kubernetesSpinnakerKindMap;

    this.kubectlExecutable = managedAccount.getKubectlExecutable();
    this.kubectlRequestTimeoutSeconds = managedAccount.getKubectlRequestTimeoutSeconds();
    this.kubeconfigFile = kubeconfigFile;
    this.kubeconfigFileHash = KubeconfigFileHasher.hashKubeconfigFile(kubeconfigFile);
    this.serviceAccount = managedAccount.isServiceAccount();
    this.context = managedAccount.getContext();

    this.onlySpinnakerManaged = managedAccount.isOnlySpinnakerManaged();
    this.liveManifestCalls = managedAccount.isLiveManifestCalls();
    this.checkPermissionsOnStartup = managedAccount.isCheckPermissionsOnStartup();
    this.cachingPolicies = managedAccount.getCachingPolicies();

    this.oAuthServiceAccount = managedAccount.getOAuthServiceAccount();
    this.oAuthScopes = managedAccount.getOAuthScopes();

    this.metrics = managedAccount.isMetrics();

    this.debug = managedAccount.isDebug();
  }

  /**
   * Thin wrapper around a Caffeine cache that handles memoizing a supplier function with expiration
   */
  private static class Memoizer<T> implements Supplier<T> {
    private static final String CACHE_KEY = "key";
    private final LoadingCache<String, T> cache;

    private Memoizer(Supplier<T> supplier, long expirySeconds, TimeUnit timeUnit) {
      this.cache =
          Caffeine.newBuilder()
              .refreshAfterWrite(expirySeconds, timeUnit)
              .build(key -> supplier.get());
    }

    public T get() {
      return cache.get(CACHE_KEY);
    }

    public static <U> Memoizer<U> memoizeWithExpiration(
        Supplier<U> supplier, long expirySeconds, TimeUnit timeUnit) {
      return new Memoizer<>(supplier, expirySeconds, timeUnit);
    }
  }

  public enum KubernetesKindStatus {
    VALID("Kind [%s] is a valid kind"),
    KIND_NONE("Kind [%s] is invalid"),
    EXPLICITLY_OMITTED_BY_CONFIGURATION(
        "Kind [%s] included in 'omitKinds' of kubernetes account configuration"),
    MISSING_FROM_ALLOWED_KINDS("Kind [%s] missing in 'kinds' of kubernetes account configuration"),
    READ_ERROR(
        "Error reading kind [%s]. Please check connectivity and access permissions to the cluster");

    private final String errorMessage;

    KubernetesKindStatus(String errorMessage) {
      this.errorMessage = errorMessage;
    }

    public String getErrorMessage(KubernetesKind kind) {
      return String.format(this.errorMessage, kind);
    }
  }

  public boolean isValidKind(@Nonnull KubernetesKind kind) {
    return getKindStatus(kind) == KubernetesKindStatus.VALID;
  }

  /**
   * Returns the status of a given kubernetes kind with respect to the current account. Checks of
   * whether a kind is readable are cached for the lifetime of the process (and are only performed
   * when a kind is otherwise considered valid for the account).
   */
  @Nonnull
  public KubernetesKindStatus getKindStatus(@Nonnull KubernetesKind kind) {
    if (kind.equals(KubernetesKind.NONE)) {
      return KubernetesKindStatus.KIND_NONE;
    }

    if (!kinds.isEmpty()) {
      return kinds.contains(kind)
          ? KubernetesKindStatus.VALID
          : KubernetesKindStatus.MISSING_FROM_ALLOWED_KINDS;
    }

    if (omitKinds.contains(kind)) {
      return KubernetesKindStatus.EXPLICITLY_OMITTED_BY_CONFIGURATION;
    }

    return permissionValidator.isKindReadable(kind)
        ? KubernetesKindStatus.VALID
        : KubernetesKindStatus.READ_ERROR;
  }

  private Optional<KubernetesKindProperties> getCrdProperties(
      @Nonnull KubernetesKind kubernetesKind) {
    return Optional.ofNullable(getCrds().get(kubernetesKind));
  }

  public String getDefaultNamespace() {
    if (StringUtils.isEmpty(cachedDefaultNamespace)) {
      cachedDefaultNamespace = lookupDefaultNamespace();
    }

    return cachedDefaultNamespace;
  }

  private Optional<String> serviceAccountNamespace() {
    try {
      return Files.lines(SERVICE_ACCOUNT_NAMESPACE_PATH, StandardCharsets.UTF_8).findFirst();
    } catch (IOException e) {
      log.debug("Failure looking up desired namespace", e);
      return Optional.empty();
    }
  }

  private Optional<String> kubectlNamespace() {
    try {
      return Optional.of(jobExecutor.defaultNamespace(this));
    } catch (KubectlException e) {
      log.debug("Failure looking up desired namespace", e);
      return Optional.empty();
    }
  }

  private String lookupDefaultNamespace() {
    try {
      if (serviceAccount) {
        return serviceAccountNamespace().orElse(DEFAULT_NAMESPACE);
      } else {
        return kubectlNamespace().orElse(DEFAULT_NAMESPACE);
      }
    } catch (Exception e) {
      log.debug(
          "Error encountered looking up default namespace in account '{}', defaulting to {}",
          accountName,
          DEFAULT_NAMESPACE,
          e);
      return DEFAULT_NAMESPACE;
    }
  }

  @Nonnull
  public ImmutableMap<KubernetesKind, KubernetesKindProperties> getCrds() {
    return crdSupplier.get();
  }

  @Nonnull
  private ImmutableMap<KubernetesKind, KubernetesKindProperties> crdSupplier() {
    // Short-circuit if the account is not configured (or does not have permission) to read CRDs
    if (!isValidKind(KubernetesKind.CUSTOM_RESOURCE_DEFINITION)) {
      return ImmutableMap.of();
    }
    try {
      return list(KubernetesKind.CUSTOM_RESOURCE_DEFINITION, "").stream()
          .map(
              manifest ->
                  KubernetesCacheDataConverter.getResource(
                      manifest, V1beta1CustomResourceDefinition.class))
          .map(KubernetesKindProperties::fromCustomResourceDefinition)
          .collect(
              toImmutableMap(KubernetesKindProperties::getKubernetesKind, Function.identity()));
    } catch (KubectlException e) {
      // not logging here -- it will generate a lot of noise in cases where crds aren't
      // available/registered in the first place
      return ImmutableMap.of();
    }
  }

  @Nonnull
  private ImmutableList<String> namespaceSupplier() {
    try {
      return jobExecutor
          .list(
              this,
              Collections.singletonList(KubernetesKind.NAMESPACE),
              "",
              new KubernetesSelectorList())
          .stream()
          .map(KubernetesManifest::getName)
          .collect(toImmutableList());
    } catch (KubectlException e) {
      log.error("Could not list namespaces for account {}: {}", accountName, e.getMessage());
      return ImmutableList.of();
    }
  }

  @Override
  public List<String> getDeclaredNamespaces() {
    List<String> result;
    if (!namespaces.isEmpty()) {
      result = namespaces;
    } else {
      result = liveNamespaceSupplier.get();
    }

    if (!omitNamespaces.isEmpty()) {
      result =
          result.stream().filter(n -> !omitNamespaces.contains(n)).collect(Collectors.toList());
    }

    return result;
  }

  public boolean isMetricsEnabled() {
    return metrics && permissionValidator.isMetricsReadable();
  }

  @Override
  public Map<String, String> getSpinnakerKindMap() {
    Map<String, String> kindMap =
        new HashMap<>(kubernetesSpinnakerKindMap.kubernetesToSpinnakerKindStringMap());
    getCustomResources()
        .forEach(
            customResource ->
                kindMap.put(customResource.getKubernetesKind(), customResource.getSpinnakerKind()));
    return kindMap;
  }

  @Override
  public List<LinkedDockerRegistryConfiguration> getDockerRegistries() {
    return Collections.emptyList();
  }

  public KubernetesManifest get(KubernetesKind kind, String namespace, String name) {
    return runAndRecordMetrics(
        "get", kind, namespace, () -> jobExecutor.get(this, kind, namespace, name));
  }

  @Nonnull
  public ImmutableList<KubernetesManifest> list(KubernetesKind kind, String namespace) {
    return runAndRecordMetrics(
        "list",
        kind,
        namespace,
        () ->
            jobExecutor.list(
                this, Collections.singletonList(kind), namespace, new KubernetesSelectorList()));
  }

  @Nonnull
  public ImmutableList<KubernetesManifest> list(
      KubernetesKind kind, String namespace, KubernetesSelectorList selectors) {
    return runAndRecordMetrics(
        "list",
        kind,
        namespace,
        () -> jobExecutor.list(this, Collections.singletonList(kind), namespace, selectors));
  }

  @Nonnull
  public ImmutableList<KubernetesManifest> list(List<KubernetesKind> kinds, String namespace) {
    if (kinds.isEmpty()) {
      return ImmutableList.of();
    } else {
      return runAndRecordMetrics(
          "list",
          kinds,
          namespace,
          () -> jobExecutor.list(this, kinds, namespace, new KubernetesSelectorList()));
    }
  }

  @Nonnull
  public ImmutableList<KubernetesManifest> eventsFor(
      KubernetesKind kind, String namespace, String name) {
    return runAndRecordMetrics(
        "list",
        KubernetesKind.EVENT,
        namespace,
        () -> jobExecutor.eventsFor(this, kind, namespace, name));
  }

  public String logs(String namespace, String podName, String containerName) {
    return runAndRecordMetrics(
        "logs",
        KubernetesKind.POD,
        namespace,
        () -> jobExecutor.logs(this, namespace, podName, containerName));
  }

  public String jobLogs(String namespace, String jobName, String containerName) {
    return runAndRecordMetrics(
        "logs",
        KubernetesKind.JOB,
        namespace,
        () -> jobExecutor.jobLogs(this, namespace, jobName, containerName));
  }

  public void scale(KubernetesKind kind, String namespace, String name, int replicas) {
    runAndRecordMetrics(
        "scale", kind, namespace, () -> jobExecutor.scale(this, kind, namespace, name, replicas));
  }

  public List<String> delete(
      KubernetesKind kind,
      String namespace,
      String name,
      KubernetesSelectorList labelSelectors,
      V1DeleteOptions options) {
    return runAndRecordMetrics(
        "delete",
        kind,
        namespace,
        () -> jobExecutor.delete(this, kind, namespace, name, labelSelectors, options));
  }

  public Collection<KubernetesPodMetric> topPod(String namespace, String pod) {
    return runAndRecordMetrics(
        "top", KubernetesKind.POD, namespace, () -> jobExecutor.topPod(this, namespace, pod));
  }

  public void deploy(KubernetesManifest manifest) {
    runAndRecordMetrics(
        "deploy",
        manifest.getKind(),
        manifest.getNamespace(),
        () -> jobExecutor.deploy(this, manifest));
  }

  public void replace(KubernetesManifest manifest) {
    runAndRecordMetrics(
        "replace",
        manifest.getKind(),
        manifest.getNamespace(),
        () -> jobExecutor.replace(this, manifest));
  }

  public List<Integer> historyRollout(KubernetesKind kind, String namespace, String name) {
    return runAndRecordMetrics(
        "historyRollout",
        kind,
        namespace,
        () -> jobExecutor.historyRollout(this, kind, namespace, name));
  }

  public void undoRollout(KubernetesKind kind, String namespace, String name, int revision) {
    runAndRecordMetrics(
        "undoRollout",
        kind,
        namespace,
        () -> jobExecutor.undoRollout(this, kind, namespace, name, revision));
  }

  public void pauseRollout(KubernetesKind kind, String namespace, String name) {
    runAndRecordMetrics(
        "pauseRollout",
        kind,
        namespace,
        () -> jobExecutor.pauseRollout(this, kind, namespace, name));
  }

  public void resumeRollout(KubernetesKind kind, String namespace, String name) {
    runAndRecordMetrics(
        "resumeRollout",
        kind,
        namespace,
        () -> jobExecutor.resumeRollout(this, kind, namespace, name));
  }

  public void rollingRestart(KubernetesKind kind, String namespace, String name) {
    runAndRecordMetrics(
        "rollingRestart",
        kind,
        namespace,
        () -> jobExecutor.rollingRestart(this, kind, namespace, name));
  }

  public void patch(
      KubernetesKind kind,
      String namespace,
      String name,
      KubernetesPatchOptions options,
      KubernetesManifest manifest) {
    runAndRecordMetrics(
        "patch",
        kind,
        namespace,
        () -> jobExecutor.patch(this, kind, namespace, name, options, manifest));
  }

  public void patch(
      KubernetesKind kind,
      String namespace,
      String name,
      KubernetesPatchOptions options,
      List<JsonPatch> patches) {
    runAndRecordMetrics(
        "patch",
        kind,
        namespace,
        () -> jobExecutor.patch(this, kind, namespace, name, options, patches));
  }

  private <T> T runAndRecordMetrics(
      String action, KubernetesKind kind, String namespace, Supplier<T> op) {
    return runAndRecordMetrics(action, Collections.singletonList(kind), namespace, op);
  }

  private <T> T runAndRecordMetrics(
      String action, List<KubernetesKind> kinds, String namespace, Supplier<T> op) {
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
        tags.put(
            "kinds", kinds.stream().map(KubernetesKind::toString).collect(Collectors.joining(",")));
      }
      tags.put("account", accountName);
      tags.put("namespace", StringUtils.isEmpty(namespace) ? "none" : namespace);
      if (failure == null) {
        tags.put("success", "true");
      } else {
        tags.put("success", "false");
        tags.put("reason", failure.getClass().getSimpleName() + ": " + failure.getMessage());
      }

      registry
          .timer(registry.createId("kubernetes.api", tags))
          .record(clock.monotonicTime() - startTime, TimeUnit.NANOSECONDS);

      if (failure != null) {
        throw new KubectlJobExecutor.KubectlException(
            "Failure running " + action + " on " + kinds + ": " + failure.getMessage(), failure);
      } else if (apiException != null) {
        throw apiException;
      } else {
        return result;
      }
    }
  }

  /**
   * Handles validating which kubernetes kinds the current account has permission to read, as well
   * as whether the current account has permission to read pod metrics.
   */
  private class PermissionValidator {
    private final Supplier<String> checkNamespace = Suppliers.memoize(this::computeCheckNamespace);
    private final Map<KubernetesKind, Boolean> readableKinds = new ConcurrentHashMap<>();
    private final Supplier<Boolean> metricsReadable = Suppliers.memoize(this::checkMetricsReadable);

    private String getCheckNamespace() {
      return checkNamespace.get();
    }

    private String computeCheckNamespace() {
      List<String> namespaces = getDeclaredNamespaces();

      if (namespaces.isEmpty()) {
        log.warn(
            "There are no namespaces configured (or loadable) -- please check that the list of"
                + " 'omitNamespaces' for account '{}' doesn't prevent access from all namespaces"
                + " in this cluster, or that the cluster is reachable.",
            accountName);
        return null;
      }

      // we are making the assumption that the roles granted to spinnaker for this account in all
      // namespaces are identical.
      // otherwise, checking all namespaces for all kinds is too expensive in large clusters
      // (imagine a cluster with 100s of namespaces).
      return namespaces.get(0);
    }

    private boolean skipPermissionChecks() {
      // checkPermissionsOnStartup exists from when permission checks were done at startup (and took
      // a long time); this flag was added to skip the checks and assume all kinds were readable.
      // Now that permissions are checked on-the-fly, this flag is probably not necessary, but for
      // now we'll continue to support the prior behavior, which is to short-circuit and assume all
      // kinds are readable before checking.
      // Before removing this flag, we'll need to check that nobody is depending on Spinnaker
      // skipping permission checks for reasons other than performance.  (For example, users may
      // be relying on the skipped permission checks because of differences in permissions between
      // namespaces.)
      return !checkPermissionsOnStartup;
    }

    private boolean canReadKind(KubernetesKind kind) {
      if (skipPermissionChecks()) {
        return true;
      }
      log.info("Checking if {} is readable in account '{}'...", kind, accountName);
      try {
        if (kindRegistry.getKindProperties(kind).isNamespaced()) {
          list(kind, checkNamespace.get());
        } else {
          list(kind, null);
        }
        return true;
      } catch (Exception e) {
        log.info(
            "Kind {} will not be cached in account '{}' because it cannot be listed.",
            kind,
            accountName);
        return false;
      }
    }

    private boolean checkMetricsReadable() {
      if (skipPermissionChecks()) {
        return true;
      }
      try {
        log.info("Checking if pod metrics are readable for account {}...", accountName);
        topPod(getCheckNamespace(), null);
        return true;
      } catch (Exception e) {
        log.warn(
            "Could not read pod metrics in account '{}' for reason: {}",
            accountName,
            e.getMessage());
        log.debug("Reading logs for account '{}' failed with exception: ", accountName, e);
        return false;
      }
    }

    /**
     * Returns whether the given kind is readable for the current kubernetes account. This check is
     * cached for each kind for the lifetime of the process, and subsequent calls return the cached
     * value.
     */
    boolean isKindReadable(@Nonnull KubernetesKind kind) {
      return readableKinds.computeIfAbsent(kind, this::canReadKind);
    }

    /**
     * Returns whether metrics are readable for the current kubernetes account. This check is cached
     * for the lifetime of the process, and subsequent calls return the cached value.
     */
    boolean isMetricsReadable() {
      return metricsReadable.get();
    }
  }

  @Component
  @RequiredArgsConstructor
  public static class Factory implements KubernetesCredentialFactory<KubernetesV2Credentials> {
    private final Registry spectatorRegistry;
    private final NamerRegistry namerRegistry;
    private final KubectlJobExecutor jobExecutor;
    private final ConfigFileService configFileService;
    private final AccountResourcePropertyRegistry.Factory resourcePropertyRegistryFactory;
    private final KubernetesKindRegistry.Factory kindRegistryFactory;
    private final KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap;

    public KubernetesV2Credentials build(
        KubernetesConfigurationProperties.ManagedAccount managedAccount) {
      validateAccount(managedAccount);
      NamerRegistry.lookup()
          .withProvider(KubernetesCloudProvider.ID)
          .withAccount(managedAccount.getName())
          .setNamer(
              KubernetesManifest.class,
              namerRegistry.getNamingStrategy(managedAccount.getNamingStrategy()));
      return new KubernetesV2Credentials(
          spectatorRegistry,
          jobExecutor,
          managedAccount,
          resourcePropertyRegistryFactory,
          kindRegistryFactory,
          kubernetesSpinnakerKindMap,
          getKubeconfigFile(configFileService, managedAccount));
    }
  }
}
