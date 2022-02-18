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
import io.github.resilience4j.retry.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
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
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KubectlJobExecutor {
  private static final Logger log = LoggerFactory.getLogger(KubectlJobExecutor.class);
  private static final String NOT_FOUND_STRING = "(NotFound)";
  private final JobExecutor jobExecutor;
  private final String executable;
  private final String oAuthExecutable;

  private final Gson gson = new Gson();

  private final KubernetesConfigurationProperties kubernetesConfigurationProperties;

  private final Optional<RetryRegistry> retryRegistry;

  @Autowired
  public KubectlJobExecutor(
      JobExecutor jobExecutor,
      @Value("${kubernetes.kubectl.executable:kubectl}") String executable,
      @Value("${kubernetes.o-auth.executable:oauth2l}") String oAuthExecutable,
      KubernetesConfigurationProperties kubernetesConfigurationProperties) {
    this.jobExecutor = jobExecutor;
    this.executable = executable;
    this.oAuthExecutable = oAuthExecutable;
    this.kubernetesConfigurationProperties = kubernetesConfigurationProperties;
    this.retryRegistry =
        getRetryRegistry(kubernetesConfigurationProperties.getJobExecutor().getRetries());

    log.info(
        "kubectl job executor configured with {}",
        kubernetesConfigurationProperties.getJobExecutor());
  }

  public String logs(
      KubernetesCredentials credentials, String namespace, String podName, String containerName) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);
    command.add("logs");
    command.add(podName);
    command.add("-c=" + containerName);

    JobResult<String> status =
        executeKubectlJob(
            credentials.getAccountName() + ".logs." + podName, new JobRequest(command));

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
    command.add("logs");
    command.add("job/" + jobName);
    command.add("-c=" + containerName);

    JobResult<String> status =
        executeKubectlJob(
            credentials.getAccountName() + ".jobLogs." + jobName, new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to get logs from job/" + jobName + " in " + namespace + ": " + status.getError());
    }

    return status.getOutput();
  }

  public List<String> delete(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      KubernetesSelectorList labelSelectors,
      V1DeleteOptions deleteOptions) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("delete");

    command = kubectlLookupInfo(command, kind, name, labelSelectors);

    // spinnaker generally accepts deletes of resources that don't exist
    command.add("--ignore-not-found=true");

    if (deleteOptions.getOrphanDependents() != null) {
      command.add("--cascade=" + !deleteOptions.getOrphanDependents());
    }

    if (deleteOptions.getGracePeriodSeconds() != null) {
      command.add("--grace-period=" + deleteOptions.getGracePeriodSeconds());
    }

    if (!Strings.isNullOrEmpty(deleteOptions.getPropagationPolicy())) {
      throw new IllegalArgumentException(
          "Propagation policy is not yet supported as a delete option");
    }

    String id;
    if (!Strings.isNullOrEmpty(name)) {
      id = kind + "/" + name;
    } else {
      id = labelSelectors.toString();
    }

    JobResult<String> status =
        executeKubectlJob(credentials.getAccountName() + ".delete." + id, new JobRequest(command));

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
      int replicas) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("scale");
    command = kubectlLookupInfo(command, kind, name, null);
    command.add("--replicas=" + replicas);

    JobResult<String> status =
        executeKubectlJob(
            credentials.getAccountName() + ".scale." + kind.toString() + "/" + name,
            new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to scale " + kind + "/" + name + " from " + namespace + ": " + status.getError());
    }

    return null;
  }

  public List<Integer> historyRollout(
      KubernetesCredentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("rollout");
    command.add("history");
    command.add(kind.toString() + "/" + name);

    JobResult<String> status =
        executeKubectlJob(
            credentials.getAccountName() + ".historyRollout." + kind.toString() + "/" + name,
            new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to get rollout history of "
              + kind
              + "/"
              + name
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

    command.add("rollout");
    command.add("undo");
    command.add(kind.toString() + "/" + name);
    command.add("--to-revision=" + revision);

    JobResult<String> status =
        executeKubectlJob(
            credentials.getAccountName() + ".undoRollout." + kind.toString() + "/" + name,
            new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to undo rollout "
              + kind
              + "/"
              + name
              + " from "
              + namespace
              + ": "
              + status.getError());
    }

    return null;
  }

  public Void pauseRollout(
      KubernetesCredentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("rollout");
    command.add("pause");
    command.add(kind.toString() + "/" + name);

    JobResult<String> status =
        executeKubectlJob(
            credentials.getAccountName() + ".pauseRollout." + kind.toString() + "/" + name,
            new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to pause rollout "
              + kind
              + "/"
              + name
              + " from "
              + namespace
              + ": "
              + status.getError());
    }

    return null;
  }

  public Void resumeRollout(
      KubernetesCredentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("rollout");
    command.add("resume");
    command.add(kind.toString() + "/" + name);

    JobResult<String> status =
        executeKubectlJob(
            credentials.getAccountName() + ".resumeRollout." + kind.toString() + "/" + name,
            new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to resume rollout "
              + kind
              + "/"
              + name
              + " from "
              + namespace
              + ": "
              + status.getError());
    }

    return null;
  }

  public Void rollingRestart(
      KubernetesCredentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("rollout");
    command.add("restart");
    command.add(kind.toString() + "/" + name);

    JobResult<String> status =
        executeKubectlJob(
            credentials.getAccountName() + ".rollingRestart." + kind.toString() + "/" + name,
            new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to complete rolling restart of "
              + kind
              + "/"
              + name
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
    List<String> command = kubectlNamespacedGet(credentials, ImmutableList.of(kind), namespace);
    command.add(name);

    JobResult<String> status =
        executeKubectlJob(
            credentials.getAccountName() + ".get." + kind.toString() + "/" + name,
            new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      if (status.getError().contains(NOT_FOUND_STRING)) {
        return null;
      }

      throw new KubectlException(
          "Failed to read " + kind + " from " + namespace + ": " + status.getError());
    }

    try {
      return gson.fromJson(status.getOutput(), KubernetesManifest.class);
    } catch (JsonSyntaxException e) {
      throw new KubectlException("Failed to parse kubectl output: " + e.getMessage(), e);
    }
  }

  @Nonnull
  public ImmutableList<KubernetesManifest> eventsFor(
      KubernetesCredentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command =
        kubectlNamespacedGet(credentials, ImmutableList.of(KubernetesKind.EVENT), namespace);
    command.add("--field-selector");
    command.add(
        String.format(
            "involvedObject.name=%s,involvedObject.kind=%s",
            name, StringUtils.capitalize(kind.toString())));

    JobResult<ImmutableList<KubernetesManifest>> status =
        executeKubectlJob(
            credentials.getAccountName() + ".eventsFor." + kind.toString() + "/" + name,
            new JobRequest(command),
            parseManifestList());

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to read events from " + namespace + ": " + status.getError());
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
    List<String> command = kubectlNamespacedGet(credentials, kinds, namespace);
    if (selectors.isNotEmpty()) {
      command.add("-l=" + selectors.toString());
    }

    JobResult<ImmutableList<KubernetesManifest>> status =
        executeKubectlJob(
            credentials.getAccountName() + ".list." + kinds + "." + namespace,
            new JobRequest(command),
            parseManifestList());

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to read " + kinds + " from " + namespace + ": " + status.getError());
    }

    if (status.getError().contains("No resources found")) {
      return ImmutableList.of();
    }

    return status.getOutput();
  }

  public KubernetesManifest deploy(KubernetesCredentials credentials, KubernetesManifest manifest) {
    List<String> command = kubectlAuthPrefix(credentials);

    String manifestAsJson = gson.toJson(manifest);

    // Read from stdin
    command.add("apply");
    command.add("-o");
    command.add("json");
    command.add("-f");
    command.add("-");

    JobResult<String> status =
        executeKubectlJob(
            credentials.getAccountName() + ".deploy." + manifest.getFullResourceName(),
            new JobRequest(
                command,
                new ByteArrayInputStream(manifestAsJson.getBytes(StandardCharsets.UTF_8))));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException("Deploy failed: " + status.getError());
    }

    try {
      return gson.fromJson(status.getOutput(), KubernetesManifest.class);
    } catch (JsonSyntaxException e) {
      throw new KubectlException("Failed to parse kubectl output: " + e.getMessage(), e);
    }
  }

  public KubernetesManifest replace(
      KubernetesCredentials credentials, KubernetesManifest manifest) {
    List<String> command = kubectlAuthPrefix(credentials);

    String manifestAsJson = gson.toJson(manifest);

    // Read from stdin
    command.add("replace");
    command.add("-o");
    command.add("json");
    command.add("-f");
    command.add("-");

    JobResult<String> status =
        executeKubectlJob(
            credentials.getAccountName() + ".replace." + manifest.getFullResourceName(),
            new JobRequest(
                command,
                new ByteArrayInputStream(manifestAsJson.getBytes(StandardCharsets.UTF_8))));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      if (status.getError().contains(NOT_FOUND_STRING)) {
        throw new KubectlNotFoundException("Replace failed: " + status.getError());
      }
      throw new KubectlException("Replace failed: " + status.getError());
    }

    try {
      return gson.fromJson(status.getOutput(), KubernetesManifest.class);
    } catch (JsonSyntaxException e) {
      throw new KubectlException("Failed to parse kubectl output: " + e.getMessage(), e);
    }
  }

  public KubernetesManifest create(KubernetesCredentials credentials, KubernetesManifest manifest) {
    List<String> command = kubectlAuthPrefix(credentials);

    String manifestAsJson = gson.toJson(manifest);

    // Read from stdin
    command.add("create");
    command.add("-o");
    command.add("json");
    command.add("-f");
    command.add("-");

    JobResult<String> status =
        executeKubectlJob(
            credentials.getAccountName() + ".create." + manifest.getFullResourceName(),
            new JobRequest(
                command,
                new ByteArrayInputStream(manifestAsJson.getBytes(StandardCharsets.UTF_8))));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException("Create failed: " + status.getError());
    }

    try {
      return gson.fromJson(status.getOutput(), KubernetesManifest.class);
    } catch (JsonSyntaxException e) {
      throw new KubectlException("Failed to parse kubectl output: " + e.getMessage(), e);
    }
  }

  private List<String> kubectlAuthPrefix(KubernetesCredentials credentials) {
    List<String> command = new ArrayList<>();
    if (!Strings.isNullOrEmpty(credentials.getKubectlExecutable())) {
      command.add(credentials.getKubectlExecutable());
    } else {
      command.add(executable);
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
        command.add("--token=" + getOAuthToken(credentials));
      }

      String kubeconfigFile = credentials.getKubeconfigFile();
      if (!Strings.isNullOrEmpty(kubeconfigFile)) {
        command.add("--kubeconfig=" + kubeconfigFile);
      }

      String context = credentials.getContext();
      if (!Strings.isNullOrEmpty(context)) {
        command.add("--context=" + context);
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

    if (labelSelectors != null && !labelSelectors.isEmpty()) {
      command.add("-l=" + labelSelectors);
    }

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
    command.add(oAuthExecutable);
    command.add("fetch");
    command.add("--json");
    command.add(credentials.getOAuthServiceAccount());
    command.addAll(credentials.getOAuthScopes());

    JobResult<String> status =
        executeKubectlJob(credentials.getAccountName() + ".getOAuthToken", new JobRequest(command));

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

    JobResult<String> status =
        executeKubectlJob(credentials.getAccountName() + ".topPod." + pod, new JobRequest(command));

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
      List<JsonPatch> patches) {
    return patch(credentials, kind, namespace, name, options, gson.toJson(patches));
  }

  public Void patch(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      KubernetesPatchOptions options,
      KubernetesManifest manifest) {
    return patch(credentials, kind, namespace, name, options, gson.toJson(manifest));
  }

  private Void patch(
      KubernetesCredentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      KubernetesPatchOptions options,
      String patchBody) {
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

    JobResult<String> status =
        executeKubectlJob(
            credentials.getAccountName() + ".patch." + kind.toString() + "/" + name,
            new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      String errMsg = status.getError();
      if (Strings.isNullOrEmpty(errMsg)) {
        errMsg = status.getOutput();
      }
      if (errMsg.contains("not patched")) {
        log.warn("No change occurred after patching {} {}:{}, ignoring", kind, namespace, name);
        return null;
      }

      throw new KubectlException("Patch failed: " + errMsg);
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

  private Optional<RetryRegistry> getRetryRegistry(
      KubernetesConfigurationProperties.KubernetesJobExecutorProperties.Retries retriesConfig) {
    if (retriesConfig.isEnabled()) {
      log.info("kubectl retries are enabled");

      // the retry config configured below is set to retry on all Throwable exceptions by default.
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

      return Optional.of(RetryRegistry.of(retryConfig.build()));
    } else {
      log.info("kubectl retries are disabled");
      return Optional.empty();
    }
  }

  private Retry getRetry(RetryRegistry retryRegistry, String identifier) {
    Retry retry = retryRegistry.retry(identifier);
    Retry.EventPublisher publisher = retry.getEventPublisher();
    publisher.onRetry(event -> log.warn(event.toString()));
    publisher.onSuccess(event -> log.info(event.toString()));
    publisher.onError(event -> log.error(event.toString()));
    return retry;
  }

  private <T> boolean shouldRetry(JobResult<T> result) {
    if (result.getResult() != JobResult.Result.SUCCESS) {
      // the error matches the configured list of retryable errors.
      if (this.kubernetesConfigurationProperties
          .getJobExecutor()
          .getRetries()
          .getRetryableErrorMessages()
          .stream()
          .anyMatch(errorMessage -> result.getError().contains(errorMessage))) {
        return true;
      }

      // even though the error is not explicitly configured to be retryable, the job was killed -
      // hence, we should retry
      if (result.isKilled()) {
        log.warn("retrying since the job {} was killed", result);
        return true;
      }

      log.warn("retries are not enabled for error: {}", result.getError());
    }
    return false;
  }

  private JobResult<String> executeKubectlJob(String identifier, JobRequest jobRequest) {
    // retry registry will be empty if retries are not enabled. Not logging anything here as it will
    // be very expensive to do so, since this method gets called for each and every kubectl
    // invocation
    if (retryRegistry.isEmpty()) {
      return jobExecutor.runJob(jobRequest);
    }

    // capture the original result obtained from the jobExecutor.runJob(jobRequest) call.
    JobResult.JobResultBuilder<String> finalResult = JobResult.builder();
    try {
      return Retry.decorateSupplier(
              getRetry(retryRegistry.get(), identifier),
              () -> {
                JobResult<String> result = jobExecutor.runJob(jobRequest);
                // even though the retry handler defaults to retrying on all throwable exceptions,
                // we have the following code because kubectl binary, when executed, does not throw
                // an exception.
                // This logic determines if the binary emits a result that is retryable or not.
                if (shouldRetry(result)) {
                  // save the result
                  finalResult
                      .error(result.getError())
                      .killed(result.isKilled())
                      .output(result.getOutput())
                      .result(result.getResult());
                  // throw explicit exception so that the retry library can log and handle it
                  // correctly.
                  throw new KubectlException(result.getError());
                }
                return result;
              })
          .get();
    } catch (KubectlException e) {
      // the caller functions expect Kubectl failures to be defined in a JobResult object and not in
      // the form of an exception.
      // Hence, we need to translate that exception back into a JobResult object - but
      // we only need to do it for KubectlException (since that is explicitly thrown above) and not
      // for any other ones.
      return finalResult.build();
    }
  }

  private <T> JobResult<T> executeKubectlJob(
      String identifier, JobRequest jobRequest, ReaderConsumer<T> readerConsumer) {
    // retry registry will be empty if retries are not enabled. Not logging anything here as it will
    // be very expensive to do so, since this method gets called for each and every kubectl
    // invocation
    if (retryRegistry.isEmpty()) {
      return jobExecutor.runJob(jobRequest, readerConsumer);
    }

    // capture the original result obtained from the jobExecutor.runJob(jobRequest) call.
    JobResult.JobResultBuilder<T> finalResult = JobResult.builder();
    try {
      return Retry.decorateSupplier(
              getRetry(retryRegistry.get(), identifier),
              () -> {
                JobResult<T> result = jobExecutor.runJob(jobRequest, readerConsumer);
                // even though the retry handler defaults to retrying on all throwable exceptions,
                // we have the following code because kubectl binary, when executed, does not throw
                // an exception.
                // This logic determines if the binary emits a result that is retryable or not.
                if (shouldRetry(result)) {
                  // save the result
                  finalResult
                      .error(result.getError())
                      .killed(result.isKilled())
                      .output(result.getOutput())
                      .result(result.getResult());
                  // throw explicit exception so that the retry library can log and handle it
                  // correctly.
                  throw new KubectlException(result.getError());
                }
                return result;
              })
          .get();
    } catch (KubectlException e) {
      // the caller functions expect Kubectl failures to be defined in a JobResult object and not in
      // the form of an exception.
      // Hence, we need to translate that exception back into a JobResult object - but
      // we only need to do it for KubectlException (since that is explicitly thrown above) and not
      // for any other ones.
      return finalResult.build();
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
}
