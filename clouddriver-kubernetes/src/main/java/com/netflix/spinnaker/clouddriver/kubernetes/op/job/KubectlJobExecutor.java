/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.op.job;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import com.netflix.spinnaker.clouddriver.jobs.local.ReaderConsumer;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.JsonPatch;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPatchOptions;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesSelectorList;
import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import com.netflix.spinnaker.kork.resilience4j.Resilience4jHelper;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.WillClose;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KubectlJobExecutor {
  private static final Logger log = LoggerFactory.getLogger(KubectlJobExecutor.class);
  private static final String NOT_FOUND_STRING = "(NotFound)";
  private static final String NO_OBJECTS_PASSED_TO_STRING = "error: no objects passed to";
  private static final String NO_OBJECTS_PASSED_TO_APPLY_STRING =
      NO_OBJECTS_PASSED_TO_STRING + " apply";
  private static final String NO_OBJECTS_PASSED_TO_CREATE_STRING =
      NO_OBJECTS_PASSED_TO_STRING + " create";
  private static final String KUBECTL_COMMAND_OPTION_TOKEN = "--token=";
  private static final String KUBECTL_COMMAND_OPTION_KUBECONFIG = "--kubeconfig=";
  private static final String KUBECTL_COMMAND_OPTION_CONTEXT = "--context=";

  private final JobExecutor jobExecutor;

  private final Gson gson = new Gson();

  private final KubernetesConfigurationProperties kubernetesConfigurationProperties;

  // @Getter is required so that this can be used in tests
  @Getter private final Optional<RetryRegistry> retryRegistry;

  private final MeterRegistry meterRegistry;

  @Autowired
  public KubectlJobExecutor(
      JobExecutor jobExecutor,
      KubernetesConfigurationProperties kubernetesConfigurationProperties,
      MeterRegistry meterRegistry) {
    this.jobExecutor = jobExecutor;
    this.kubernetesConfigurationProperties = kubernetesConfigurationProperties;
    this.meterRegistry = meterRegistry;

    this.retryRegistry =
        initializeRetryRegistry(kubernetesConfigurationProperties.getJobExecutor().getRetries());
  }

  /**
   * This is used to initialize a RetryRegistry. RetryRegistry acts as a global store for all retry
   * instances. The retry instances are shared for various kubectl actions. A retry instance is
   * identified by the account name.
   *
   * @param retriesConfig - kubectl job retries configuration
   * @return - If retries are enabled, it returns an Optional that contains a RetryRegistry,
   *     otherwise it returns an empty Optional
   */
  private Optional<RetryRegistry> initializeRetryRegistry(
      KubernetesConfigurationProperties.KubernetesJobExecutorProperties.Retries retriesConfig) {
    if (retriesConfig.isEnabled()) {
      log.info("kubectl retries are enabled");

      // this config will be applied to all retry instances created from the registry
      RetryConfig.Builder<Object> retryConfig =
          RetryConfig.custom().maxAttempts(retriesConfig.getMaxAttempts());
      if (retriesConfig.isExponentialBackoffEnabled()) {
        retryConfig.intervalFunction(
            IntervalFunction.ofExponentialBackoff(
                Duration.ofMillis(retriesConfig.getExponentialBackOffIntervalMs()),
                retriesConfig.getExponentialBackoffMultiplier()));
      } else {
        retryConfig.waitDuration(Duration.ofMillis(retriesConfig.getBackOffInMs()));
      }

      // retry on all exceptions except NoRetryException
      retryConfig.ignoreExceptions(NoRetryException.class);

      // create the retry registry
      RetryRegistry retryRegistry = RetryRegistry.of(retryConfig.build());

      Resilience4jHelper.configureLogging(retryRegistry, "Kubectl command", log);

      if (this.kubernetesConfigurationProperties
          .getJobExecutor()
          .getRetries()
          .getMetrics()
          .isEnabled()) {
        TaggedRetryMetrics.ofRetryRegistry(retryRegistry).bindTo(meterRegistry);
      }

      return Optional.of(retryRegistry);
    } else {
      log.info("kubectl retries are disabled");
      return Optional.empty();
    }
  }

  public String logs(
      KubernetesCredentials credentials, String namespace, String podName, String containerName) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);
    command.add("logs");
    command.add(podName);
    command.add("-c=" + containerName);

    JobResult<String> status = executeKubectlCommand(credentials, command);

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to get logs from "
              + podName
              + "/"
              + containerName
              + " in "
              + namespace
              + ": "
              + status.getError());
    }

    return status.getOutput();
  }

  public String jobLogs(
      KubernetesCredentials credentials, String namespace, String jobName, String containerName) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);
    String resource = "job/" + jobName;
    command.add("logs");
    command.add(resource);
    command.add("-c=" + containerName);

    JobResult<String> status = executeKubectlCommand(credentials, command);

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to get logs from " + resource + " in " + namespace + ": " + status.getError());
    }

    return status.getOutput();
  }

  public List<String> delete(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      KubernetesSelectorList labelSelectors,
      V1DeleteOptions deleteOptions,
      Task task,
      String opName) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("delete");

    command = kubectlLookupInfo(command, kind, name, labelSelectors);

    // spinnaker generally accepts deletes of resources that don't exist
    command.add("--ignore-not-found=true");

    if (deleteOptions.getPropagationPolicy() != null) {
      command.add("--cascade=" + deleteOptions.getPropagationPolicy());
    }

    if (deleteOptions.getGracePeriodSeconds() != null) {
      command.add("--grace-period=" + deleteOptions.getGracePeriodSeconds());
    }

    String id;
    if (!Strings.isNullOrEmpty(name)) {
      id = kind + "/" + name;
    } else {
      id = labelSelectors.toString();
    }

    JobResult<String> status = executeKubectlCommand(credentials, command);

    persistKubectlJobOutput(credentials, status, id, task, opName);

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to delete " + id + " from " + namespace + ": " + status.getError());
    }

    if (Strings.isNullOrEmpty(status.getOutput())
        || status.getOutput().equals("No output from command.")
        || status.getOutput().startsWith("No resources found")) {
      return new ArrayList<>();
    }

    return Arrays.stream(status.getOutput().split("\n"))
        .map(m -> m.substring(m.indexOf("\"") + 1))
        .map(m -> m.substring(0, m.lastIndexOf("\"")))
        .collect(Collectors.toList());
  }

  public Void scale(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      int replicas,
      Task task,
      String opName) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("scale");
    command = kubectlLookupInfo(command, kind, name, null);
    command.add("--replicas=" + replicas);

    String resource = kind + "/" + name;
    JobResult<String> status = executeKubectlCommand(credentials, command);
    persistKubectlJobOutput(credentials, status, resource, task, opName);

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to scale " + resource + " from " + namespace + ": " + status.getError());
    }

    return null;
  }

  public List<Integer> historyRollout(
      KubernetesCredentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);
    String resource = kind + "/" + name;
    command.add("rollout");
    command.add("history");
    command.add(resource);

    JobResult<String> status = executeKubectlCommand(credentials, command);

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to get rollout history of "
              + resource
              + " from "
              + namespace
              + ": "
              + status.getError());
    }

    String stdout = status.getOutput();
    if (Strings.isNullOrEmpty(stdout)) {
      return new ArrayList<>();
    }

    // <resource> "name"
    // REVISION CHANGE-CAUSE
    // #        <change>
    // #        <change>
    // #        <change>
    // ...
    List<String> splitOutput = Arrays.stream(stdout.split("\n")).collect(Collectors.toList());

    if (splitOutput.size() <= 2) {
      return new ArrayList<>();
    }

    splitOutput = splitOutput.subList(2, splitOutput.size());

    return splitOutput.stream()
        .map(l -> l.split("[ \t]"))
        .filter(l -> l.length > 0)
        .map(l -> l[0])
        .map(Integer::valueOf)
        .collect(Collectors.toList());
  }

  public Void undoRollout(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      int revision) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    String resource = kind + "/" + name;
    command.add("rollout");
    command.add("undo");
    command.add(resource);
    command.add("--to-revision=" + revision);

    JobResult<String> status = executeKubectlCommand(credentials, command);

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to undo rollout " + resource + " from " + namespace + ": " + status.getError());
    }

    return null;
  }

  public Void pauseRollout(
      KubernetesCredentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    String resource = kind + "/" + name;
    command.add("rollout");
    command.add("pause");
    command.add(resource);

    JobResult<String> status = executeKubectlCommand(credentials, command);

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to pause rollout " + resource + " from " + namespace + ": " + status.getError());
    }

    return null;
  }

  public Void resumeRollout(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      Task task,
      String opName) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    String resource = kind + "/" + name;
    command.add("rollout");
    command.add("resume");
    command.add(resource);

    JobResult<String> status = executeKubectlCommand(credentials, command);

    persistKubectlJobOutput(credentials, status, resource, task, opName);

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to resume rollout " + resource + " from " + namespace + ": " + status.getError());
    }

    return null;
  }

  public Void rollingRestart(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      Task task,
      String opName) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    String resource = kind + "/" + name;
    command.add("rollout");
    command.add("restart");
    command.add(resource);

    JobResult<String> status = executeKubectlCommand(credentials, command);

    persistKubectlJobOutput(credentials, status, resource, task, opName);

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to complete rolling restart of "
              + resource
              + " from "
              + namespace
              + ": "
              + status.getError());
    }

    return null;
  }

  @Nullable
  public KubernetesManifest get(
      KubernetesCredentials credentials, KubernetesKind kind, String namespace, String name) {
    log.debug(
        "Getting information for {} of Kind {} in namespace {}", name, kind.toString(), namespace);
    List<String> command = kubectlNamespacedGet(credentials, ImmutableList.of(kind), namespace);
    command.add(name);

    JobResult<String> status = executeKubectlCommand(credentials, command);

    if (status.getResult() != JobResult.Result.SUCCESS) {
      if (status.getError().contains(NOT_FOUND_STRING)) {
        return null;
      }

      throw new KubectlException(
          "Failed to get: "
              + name
              + " of kind: "
              + kind
              + " from namespace: "
              + namespace
              + ": "
              + status.getError());
    }

    try {
      return gson.fromJson(status.getOutput(), KubernetesManifest.class);
    } catch (JsonSyntaxException e) {
      throw new KubectlException(
          "Failed to parse kubectl output for: "
              + name
              + " of kind: "
              + kind
              + " in namespace: "
              + namespace
              + ": "
              + e.getMessage(),
          e);
    }
  }

  @Nonnull
  public ImmutableList<KubernetesManifest> eventsFor(
      KubernetesCredentials credentials, KubernetesKind kind, String namespace, String name) {
    log.debug("Getting events for {} of Kind {} in namespace {}", name, kind.toString(), namespace);
    List<String> command =
        kubectlNamespacedGet(credentials, ImmutableList.of(KubernetesKind.EVENT), namespace);
    command.add("--field-selector");
    command.add(
        String.format(
            "involvedObject.name=%s,involvedObject.kind=%s",
            name, StringUtils.capitalize(kind.toString())));

    JobResult<ImmutableList<KubernetesManifest>> status =
        executeKubectlCommand(credentials, command, parseManifestList());

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to read events for: "
              + kind
              + "/"
              + name
              + " from "
              + namespace
              + ": "
              + status.getError());
    }

    if (status.getError().contains("No resources found")) {
      return ImmutableList.of();
    }

    return status.getOutput();
  }

  @Nonnull
  public ImmutableList<KubernetesManifest> list(
      KubernetesCredentials credentials,
      List<KubernetesKind> kinds,
      String namespace,
      KubernetesSelectorList selectors) {
    log.debug("Getting list of kinds {} in namespace {}", kinds, namespace);
    List<String> command = kubectlNamespacedGet(credentials, kinds, namespace);
    if (selectors.isNotEmpty()) {
      log.debug("with selectors: {}", selectors.toString());
      command.add("-l=" + selectors.toString());
    }

    JobResult<ImmutableList<KubernetesManifest>> status =
        executeKubectlCommand(credentials, command, parseManifestList());

    if (status.getResult() != JobResult.Result.SUCCESS) {
      boolean permissionError =
          org.apache.commons.lang3.StringUtils.containsIgnoreCase(status.getError(), "forbidden");
      if (permissionError) {
        log.warn(status.getError());
      } else {
        throw new KubectlException(
            "Failed to read " + kinds + " from " + namespace + ": " + status.getError());
      }
    }

    if (status.getError().contains("No resources found")) {
      return ImmutableList.of();
    }

    return status.getOutput();
  }

  /**
   * Invoke kubectl apply with the given manifest and (if present) label selectors.
   *
   * @param credentials k8s account credentials
   * @param manifest the manifest to apply
   * @param task the task performing this kubectl invocation
   * @param opName the name of the operation performing this kubectl invocation
   * @param labelSelectors label selectors
   * @return the manifest parsed from stdout of the kubectl invocation, or null if a label selector
   *     is present and kubectl returned "no objects passed to apply"
   */
  public KubernetesManifest deploy(
      KubernetesCredentials credentials,
      KubernetesManifest manifest,
      Task task,
      String opName,
      KubernetesSelectorList labelSelectors,
      String... cmdArgs) {
    log.info("Deploying manifest {}", manifest.getFullResourceName());
    List<String> command = kubectlAuthPrefix(credentials);

    // Read from stdin
    command.add("apply");
    command.addAll(List.of(cmdArgs));
    command.add("-o");
    command.add("json");
    command.add("-f");
    command.add("-");
    addLabelSelectors(command, labelSelectors);

    JobResult<String> status = executeKubectlCommand(credentials, command, Optional.of(manifest));

    persistKubectlJobOutput(credentials, status, manifest.getFullResourceName(), task, opName);

    if (status.getResult() != JobResult.Result.SUCCESS) {
      // If the caller provided a label selector, kubectl returns "no objects
      // passed to apply" if none of the given objects satisfy the selector.
      // Instead of throwing an exception, leave it to higher level logic to
      // decide how to behave.
      if (labelSelectors.isNotEmpty()
          && status.getError().contains(NO_OBJECTS_PASSED_TO_APPLY_STRING)) {
        return null;
      }

      throw new KubectlException(
          "Deploy failed for manifest: "
              + manifest.getFullResourceName()
              + ". Error: "
              + status.getError());
    }

    return getKubernetesManifestFromJobResult(status, manifest);
  }

  /**
   * Invoke kubectl replace with the given manifest. Note that kubectl replace doesn't support label
   * selectors.
   *
   * @param credentials k8s account credentials
   * @param manifest the manifest to replace
   * @param task the task performing this kubectl invocation
   * @param opName the name of the operation performing this kubectl invocation
   * @return the manifest parsed from stdout of the kubectl invocation
   */
  public KubernetesManifest replace(
      KubernetesCredentials credentials, KubernetesManifest manifest, Task task, String opName) {
    log.info("Replacing manifest {}", manifest.getFullResourceName());
    List<String> command = kubectlAuthPrefix(credentials);

    // Read from stdin
    command.add("replace");
    command.add("-o");
    command.add("json");
    command.add("-f");
    command.add("-");

    JobResult<String> status = executeKubectlCommand(credentials, command, Optional.of(manifest));

    persistKubectlJobOutput(credentials, status, manifest.getFullResourceName(), task, opName);

    if (status.getResult() != JobResult.Result.SUCCESS) {
      if (status.getError().contains(NOT_FOUND_STRING)) {
        throw new KubectlNotFoundException(
            "Replace failed for manifest: "
                + manifest.getFullResourceName()
                + ". Error: "
                + status.getError());
      }
      throw new KubectlException(
          "Replace failed for manifest: "
              + manifest.getFullResourceName()
              + ". Error: "
              + status.getError());
    }

    return getKubernetesManifestFromJobResult(status, manifest);
  }

  /**
   * Invoke kubectl create with the given manifest and (if present) label selectors.
   *
   * @param credentials k8s account credentials
   * @param manifest the manifest to create
   * @param task the task performing this kubectl invocation
   * @param opName the name of the operation performing this kubectl invocation
   * @param labelSelectors label selectors
   * @return the manifest parsed from stdout of the kubectl invocation, or null if a label selector
   *     is present and kubectl returned "no objects passed to create"
   */
  public KubernetesManifest create(
      KubernetesCredentials credentials,
      KubernetesManifest manifest,
      Task task,
      String opName,
      KubernetesSelectorList labelSelectors) {
    log.info("Creating manifest {}", manifest.getFullResourceName());
    List<String> command = kubectlAuthPrefix(credentials);

    // Read from stdin
    command.add("create");
    command.add("-o");
    command.add("json");
    command.add("-f");
    command.add("-");
    addLabelSelectors(command, labelSelectors);

    JobResult<String> status = executeKubectlCommand(credentials, command, Optional.of(manifest));

    persistKubectlJobOutput(credentials, status, manifest.getFullResourceName(), task, opName);

    if (status.getResult() != JobResult.Result.SUCCESS) {
      // If the caller provided a label selector, kubectl returns "no objects
      // passed to create" if none of the given objects satisfy the selector.
      // Instead of throwing an exception, leave it to higher level logic to
      // decide how to behave.
      if (labelSelectors.isNotEmpty()
          && status.getError().contains(NO_OBJECTS_PASSED_TO_CREATE_STRING)) {
        return null;
      }

      throw new KubectlException(
          "Create failed for manifest: "
              + manifest.getFullResourceName()
              + ". Error: "
              + status.getError());
    }

    return getKubernetesManifestFromJobResult(status, manifest);
  }

  private KubernetesManifest getKubernetesManifestFromJobResult(
      JobResult<String> status, KubernetesManifest inputManifest) {
    try {
      return gson.fromJson(status.getOutput(), KubernetesManifest.class);
    } catch (JsonSyntaxException e) {
      throw new KubectlException(
          "Failed to parse kubectl output for manifest: "
              + inputManifest.getName()
              + ". Error: "
              + e.getMessage(),
          e);
    }
  }

  private List<String> kubectlAuthPrefix(KubernetesCredentials credentials) {
    List<String> command = new ArrayList<>();
    if (!Strings.isNullOrEmpty(credentials.getKubectlExecutable())) {
      command.add(credentials.getKubectlExecutable());
    } else {
      command.add(this.kubernetesConfigurationProperties.getKubectl().getExecutable());
    }

    if (credentials.getKubectlRequestTimeoutSeconds() != null) {
      command.add("--request-timeout=" + credentials.getKubectlRequestTimeoutSeconds());
    }

    if (credentials.isDebug()) {
      command.add("-v");
      command.add("9");
    }

    if (!credentials.isServiceAccount()) {
      if (credentials.getOAuthServiceAccount() != null
          && !credentials.getOAuthServiceAccount().isEmpty()) {
        command.add(KUBECTL_COMMAND_OPTION_TOKEN + getOAuthToken(credentials));
      }

      String kubeconfigFile = credentials.getKubeconfigFile();
      if (!Strings.isNullOrEmpty(kubeconfigFile)) {
        command.add(KUBECTL_COMMAND_OPTION_KUBECONFIG + kubeconfigFile);
      }

      String context = credentials.getContext();
      if (!Strings.isNullOrEmpty(context)) {
        command.add(KUBECTL_COMMAND_OPTION_CONTEXT + context);
      }
    }

    return command;
  }

  private List<String> kubectlLookupInfo(
      List<String> command,
      KubernetesKind kind,
      String name,
      KubernetesSelectorList labelSelectors) {
    if (!Strings.isNullOrEmpty(name)) {
      command.add(kind + "/" + name);
    } else {
      command.add(kind.toString());
    }
    addLabelSelectors(command, labelSelectors);

    return command;
  }

  private List<String> kubectlNamespacedAuthPrefix(
      KubernetesCredentials credentials, String namespace) {
    List<String> command = kubectlAuthPrefix(credentials);

    if (!Strings.isNullOrEmpty(namespace)) {
      command.add("--namespace=" + namespace);
    }

    return command;
  }

  private List<String> kubectlNamespacedGet(
      KubernetesCredentials credentials, List<KubernetesKind> kind, String namespace) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);
    command.add("-o");
    command.add("json");

    command.add("get");
    command.add(kind.stream().map(KubernetesKind::toString).collect(Collectors.joining(",")));

    return command;
  }

  private String getOAuthToken(KubernetesCredentials credentials) {
    List<String> command = new ArrayList<>();
    command.add(this.kubernetesConfigurationProperties.getOAuth().getExecutable());
    command.add("fetch");
    command.add("--json");
    command.add(credentials.getOAuthServiceAccount());
    command.addAll(credentials.getOAuthScopes());

    JobResult<String> status = executeKubectlCommand(credentials, command);

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException("Could not fetch OAuth token: " + status.getError());
    }
    return status.getOutput();
  }

  public ImmutableList<KubernetesPodMetric> topPod(
      KubernetesCredentials credentials, String namespace, @Nonnull String pod) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);
    command.add("top");
    command.add("po");
    if (!pod.isEmpty()) {
      command.add(pod);
    }
    command.add("--containers");

    JobResult<String> status = executeKubectlCommand(credentials, command);
    if (status.getResult() != JobResult.Result.SUCCESS) {
      if (status.getError().toLowerCase().contains("not available")
          || status.getError().toLowerCase().contains("not found")) {
        log.warn(
            String.format(
                "Error fetching metrics for account %s: %s",
                credentials.getAccountName(), status.getError()));
        return ImmutableList.of();
      }
      throw new KubectlException("Could not read metrics: " + status.getError());
    }

    ImmutableSetMultimap<String, KubernetesPodMetric.ContainerMetric> metrics =
        MetricParser.parseMetrics(status.getOutput());
    return metrics.asMap().entrySet().stream()
        .map(
            podMetrics ->
                KubernetesPodMetric.builder()
                    .podName(podMetrics.getKey())
                    .namespace(namespace)
                    .containerMetrics(podMetrics.getValue())
                    .build())
        .collect(ImmutableList.toImmutableList());
  }

  public Void patch(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      KubernetesPatchOptions options,
      List<JsonPatch> patches,
      Task task,
      String opName) {
    return patch(credentials, kind, namespace, name, options, gson.toJson(patches), task, opName);
  }

  public Void patch(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      KubernetesPatchOptions options,
      KubernetesManifest manifest,
      Task task,
      String opName) {
    return patch(credentials, kind, namespace, name, options, gson.toJson(manifest), task, opName);
  }

  private Void patch(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      KubernetesPatchOptions options,
      String patchBody,
      Task task,
      String opName) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("patch");
    command.add(kind.toString());
    command.add(name);

    if (options.isRecord()) {
      command.add("--record");
    }

    String mergeStrategy = options.getMergeStrategy().toString();
    if (!Strings.isNullOrEmpty(mergeStrategy)) {
      command.add("--type");
      command.add(mergeStrategy);
    }

    command.add("--patch");
    command.add(patchBody);

    JobResult<String> status = executeKubectlCommand(credentials, command);

    persistKubectlJobOutput(credentials, status, kind + "/" + name, task, opName);

    if (status.getResult() != JobResult.Result.SUCCESS) {
      String errMsg = status.getError();
      if (Strings.isNullOrEmpty(errMsg)) {
        errMsg = status.getOutput();
      }
      if (errMsg.contains("not patched")) {
        log.warn("No change occurred after patching {} {}:{}, ignoring", kind, namespace, name);
        return null;
      }

      throw new KubectlException(
          "Patch failed for: " + name + " in namespace: " + namespace + ": " + errMsg);
    }

    return null;
  }

  private ReaderConsumer<ImmutableList<KubernetesManifest>> parseManifestList() {
    return (@WillClose BufferedReader r) -> {
      try (JsonReader reader = new JsonReader(r)) {
        try {
          reader.beginObject();
        } catch (EOFException e) {
          // If the stream we're parsing is empty, just return an empty list
          return ImmutableList.of();
        }
        ImmutableList.Builder<KubernetesManifest> manifestList = new ImmutableList.Builder<>();
        while (reader.hasNext()) {
          if (reader.nextName().equals("items")) {
            reader.beginArray();
            while (reader.hasNext()) {
              KubernetesManifest manifest = gson.fromJson(reader, KubernetesManifest.class);
              manifestList.add(manifest);
            }
            reader.endArray();
          } else {
            reader.skipValue();
          }
        }
        reader.endObject();
        return manifestList.build();
      } catch (IllegalStateException | JsonSyntaxException e) {
        // An IllegalStageException is thrown when we call beginObject, nextName(), etc. and the
        // next token is not what we are asserting it to be. A JsonSyntaxException is thrown when
        // gson.fromJson isn't able to map the next token to a KubernetesManifest.
        // In both of these cases, the error is due to the output from kubectl being malformed (or
        // at least malformed relative to our expectations) so we'll wrap the exception in a
        // KubectlException.
        throw new KubectlException("Failed to parse kubectl output: " + e.getMessage(), e);
      }
    };
  }

  /**
   * This method executes the actual kubectl command and determines if retries are required, on
   * failure.
   *
   * @param credentials k8s account credentials
   * @param command the actual kubectl command to be performed
   * @return - the result of the kubectl command
   */
  private JobResult<String> executeKubectlCommand(
      KubernetesCredentials credentials, List<String> command) {
    return executeKubectlCommand(credentials, command, Optional.empty());
  }

  /**
   * This method executes the actual kubectl command and determines if retries are required, on
   * failure.
   *
   * @param credentials k8s account credentials
   * @param command the actual kubectl command to be performed
   * @param manifest the manifest supplied to the kubectl command
   * @return - the result of the kubectl command
   */
  private JobResult<String> executeKubectlCommand(
      KubernetesCredentials credentials,
      List<String> command,
      Optional<KubernetesManifest> manifest) {
    // retry registry is empty if retries are not enabled.
    if (retryRegistry.isEmpty()) {
      return jobExecutor.runJob(createJobRequest(command, manifest));
    }

    // capture the original result obtained from the jobExecutor.runJob(jobRequest) call.
    JobResult.JobResultBuilder<String> finalResult = JobResult.builder();

    KubectlActionIdentifier identifier =
        new KubectlActionIdentifier(credentials, command, manifest);
    Retry retryContext = retryRegistry.get().retry(identifier.getRetryInstanceName());
    try {
      return retryContext.executeSupplier(
          () -> {
            JobResult<String> result = jobExecutor.runJob(createJobRequest(command, manifest));
            return processJobResult(identifier, result, finalResult);
          });
    } catch (KubectlException | NoRetryException e) {
      // the caller functions expect any failures to be defined in a JobResult object and not in
      // the form of an exception. Hence, we need to translate the above exceptions back into a
      // JobResult object - but we only need to do it for KubectlException and NoRetryException (
      // since these are the ones explicitly thrown above) and not for any other ones.
      return finalResult.build();
    }
  }

  /**
   * This method executes the actual kubectl command and determines if retries are required, on
   * failure.
   *
   * @param credentials k8s account credentials
   * @param command the actual kubectl command to be performed
   * @param readerConsumer A function that transforms the job's standard output
   * @param <T> return type of the JobResult output
   * @return the result of the kubectl command
   */
  private <T> JobResult<T> executeKubectlCommand(
      KubernetesCredentials credentials, List<String> command, ReaderConsumer<T> readerConsumer) {
    // retry registry is empty if retries are not enabled.
    if (retryRegistry.isEmpty()) {
      return jobExecutor.runJob(new JobRequest(command), readerConsumer);
    }

    // capture the original result obtained from the jobExecutor.runJob(jobRequest, readerConsumer)
    // call.
    JobResult.JobResultBuilder<T> finalResult = JobResult.builder();
    KubectlActionIdentifier identifier = new KubectlActionIdentifier(credentials, command);
    Retry retryContext = retryRegistry.get().retry(identifier.getRetryInstanceName());
    try {
      return retryContext.executeSupplier(
          () -> {
            JobResult<T> result = jobExecutor.runJob(new JobRequest(command), readerConsumer);
            return processJobResult(identifier, result, finalResult);
          });
    } catch (KubectlException | NoRetryException e) {
      // the caller functions expect any failures to be defined in a JobResult object and not in
      // the form of an exception. Hence, we need to translate the above exceptions back into a
      // JobResult object - but we only need to do it for KubectlException and NoRetryException
      // (since these are the ones explicitly thrown above) and not for any other ones.
      return finalResult.build();
    }
  }

  /**
   * helper function to create a JobRequest using the input parameters
   *
   * @param command the command to be executed in the job request
   * @param manifest the manifest to be used by the command. This is optional.
   * @return a job request object
   */
  @VisibleForTesting
  JobRequest createJobRequest(List<String> command, Optional<KubernetesManifest> manifest) {
    // depending on the presence of the manifest, an appropriate job request is created
    if (manifest.isPresent()) {
      String manifestAsJson = gson.toJson(manifest.get());
      return new JobRequest(
          command, new ByteArrayInputStream(manifestAsJson.getBytes(StandardCharsets.UTF_8)));
    }

    return new JobRequest(command);
  }

  /**
   * helper function to handle a job result obtained after performing a job request. This either
   * returns the result, if successful, or throws an exception on failure.
   *
   * @param identifier uniquely identifies the job in the logs
   * @param result the job result to be processed
   * @param finalResult a buffer that keeps track of the result. This ensures on retries, the
   *     original is not lost
   * @param <T> the return type of the JobResult output
   * @return the result of the kubectl command, in the form of a JobResult object
   */
  @VisibleForTesting
  <T> JobResult<T> processJobResult(
      KubectlActionIdentifier identifier,
      JobResult<T> result,
      JobResult.JobResultBuilder<T> finalResult) {
    if (result.getResult() == JobResult.Result.SUCCESS) {
      return result;
    }

    // save the result as it'll be needed later on when we are done with retries
    finalResult
        .error(result.getError())
        .killed(result.isKilled())
        .output(result.getOutput())
        .result(result.getResult());

    // if result is not successful, that means we need to determine if we should retry
    // or not.
    //
    // Since Kubectl binary doesn't throw any exceptions by default, we need to
    // check the result to see if retries are needed. Resilience.4j needs an exception to be
    // thrown to decide if retries are needed and also, to capture retry metrics correctly.
    throw convertKubectlJobResultToException(identifier.getKubectlAction(), result);
  }

  /**
   * this method is meant to be invoked only for those JobResults which are unsuccessful. It
   * determines if the error contained in the JobResult should be retried or not. If the error needs
   * to be retried, then KubectlException is returned. Otherwise, NoRetryException is returned.
   *
   * @param identifier used to log which action's job result is being processed
   * @param result the job result which needs to be checked to see if it has an error that can be
   *     retried
   * @param <T> job result generic type
   * @return - Either KubectlException or NoRetryException
   */
  private <T> RuntimeException convertKubectlJobResultToException(
      String identifier, JobResult<T> result) {
    // the error matches the configured list of retryable errors.
    if (this.kubernetesConfigurationProperties
        .getJobExecutor()
        .getRetries()
        .getRetryableErrorMessages()
        .stream()
        .anyMatch(errorMessage -> result.getError().contains(errorMessage))) {
      return new KubectlException(identifier + " failed. Error: " + result.getError());
    }

    // even though the error is not explicitly configured to be retryable, the job was killed -
    // hence, we should retry
    if (result.isKilled()) {
      return new KubectlException(
          "retrying " + identifier + " since the job " + result + " was killed");
    }

    String message =
        "Not retrying "
            + identifier
            + " as retries are not enabled for error: "
            + result.getError();
    log.warn(message);
    // we want to let the retry library know that such errors should not be retried.
    // Since we have configured the global retry registry to ignore errors of type
    // NoRetryException, we return this here
    return new NoRetryException(message);
  }

  private void persistKubectlJobOutput(
      KubernetesCredentials credentials,
      JobResult<String> status,
      String manifestName,
      Task task,
      String taskName) {
    if (kubernetesConfigurationProperties.getJobExecutor().isPersistTaskOutput()) {
      if (kubernetesConfigurationProperties.getJobExecutor().isEnableTaskOutputForAllAccounts()
          || credentials.isDebug()) {
        task.updateOutput(manifestName, taskName, status.getOutput(), status.getError());
      }
    }
  }

  private void addLabelSelectors(List<String> command, KubernetesSelectorList labelSelectors) {
    if (labelSelectors != null && !labelSelectors.isEmpty()) {
      command.add("-l=" + labelSelectors);
    }
  }

  public static class KubectlException extends RuntimeException {
    public KubectlException(String message) {
      super(message);
    }

    public KubectlException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class KubectlNotFoundException extends KubectlException {
    public KubectlNotFoundException(String message) {
      super(message);
    }
  }

  /**
   * this exception is only meant to be used in cases where we want resilience4j to not retry
   * kubectl calls. It should not be used anywhere else.
   */
  static class NoRetryException extends RuntimeException {
    NoRetryException(String message) {
      super(message);
    }
  }

  /** helper class to identify the kubectl command in logs and metrics when retries are enabled */
  static class KubectlActionIdentifier {
    KubernetesCredentials credentials;
    List<String> command;
    String namespace;
    String resource;

    public KubectlActionIdentifier(
        KubernetesCredentials credentials,
        List<String> command,
        String namespace,
        String resource) {
      this.credentials = credentials;
      this.command = command;
      this.namespace = namespace;
      this.resource = resource;
    }

    public KubectlActionIdentifier(KubernetesCredentials credentials, List<String> command) {
      this(credentials, command, "", "");
    }

    public KubectlActionIdentifier(
        KubernetesCredentials credentials,
        List<String> command,
        Optional<KubernetesManifest> manifest) {
      this(credentials, command);
      if (manifest.isPresent()) {
        this.namespace = manifest.get().getNamespace();
        this.resource = manifest.get().getFullResourceName();
      }
    }

    /**
     * this returns the sanitized kubectl command. This can be used to log the command during retry
     * attempts, among other things.
     *
     * @return - the sanitized kubectl command
     */
    public String getKubectlAction() {
      // no need to display everything in a kubectl command
      List<String> commandToLog =
          command.stream()
              .filter(
                  s ->
                      !(s.contains(KUBECTL_COMMAND_OPTION_TOKEN)
                          || s.contains(KUBECTL_COMMAND_OPTION_KUBECONFIG)
                          || s.contains(KUBECTL_COMMAND_OPTION_CONTEXT)))
              .collect(Collectors.toList());

      String identifier =
          "command: '"
              + String.join(" ", commandToLog)
              + "' in account: "
              + this.credentials.getAccountName();

      if (!namespace.isEmpty()) {
        identifier += " in namespace: " + namespace;
      }

      if (!resource.isEmpty()) {
        identifier += " for resource: " + resource;
      }
      return identifier;
    }

    /**
     * this returns a name which uniquely identifies a retry instance. This name shows up in the
     * logs when each retry event is logged. Also, when capturing the retry metrics, the 'name' tag
     * in the metric corresponds to this.
     *
     * @return - the name to be used to uniquely identify a retry instance
     */
    public String getRetryInstanceName() {
      return this.credentials.getAccountName();
    }
  }
}
