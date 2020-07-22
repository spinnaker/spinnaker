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
import com.netflix.spinnaker.clouddriver.kubernetes.description.JsonPatch;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPatchOptions;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiGroup;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesSelectorList;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesV2Credentials;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.WillClose;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KubectlJobExecutor {
  private static final String NOT_FOUND_STRING = "(NotFound)";
  private final JobExecutor jobExecutor;
  private final String executable;
  private final String oAuthExecutable;

  private final Gson gson = new Gson();

  @Autowired
  KubectlJobExecutor(
      JobExecutor jobExecutor,
      @Value("${kubernetes.kubectl.executable:kubectl}") String executable,
      @Value("${kubernetes.o-auth.executable:oauth2l}") String oAuthExecutable) {
    this.jobExecutor = jobExecutor;
    this.executable = executable;
    this.oAuthExecutable = oAuthExecutable;
  }

  public String logs(
      KubernetesV2Credentials credentials, String namespace, String podName, String containerName) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);
    command.add("logs");
    command.add(podName);
    command.add("-c=" + containerName);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

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
      KubernetesV2Credentials credentials, String namespace, String jobName, String containerName) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);
    command.add("logs");
    command.add("job/" + jobName);
    command.add("-c=" + containerName);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to get logs from job/" + jobName + " in " + namespace + ": " + status.getError());
    }

    return status.getOutput();
  }

  public List<String> delete(
      KubernetesV2Credentials credentials,
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

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      String id;
      if (!Strings.isNullOrEmpty(name)) {
        id = kind + "/" + name;
      } else {
        id = labelSelectors.toString();
      }
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
      KubernetesV2Credentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      int replicas) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("scale");
    command = kubectlLookupInfo(command, kind, name, null);
    command.add("--replicas=" + replicas);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to scale " + kind + "/" + name + " from " + namespace + ": " + status.getError());
    }

    return null;
  }

  public List<Integer> historyRollout(
      KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("rollout");
    command.add("history");
    command.add(kind.toString() + "/" + name);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

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
      KubernetesV2Credentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      int revision) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("rollout");
    command.add("undo");
    command.add(kind.toString() + "/" + name);
    command.add("--to-revision=" + revision);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

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
      KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("rollout");
    command.add("pause");
    command.add(kind.toString() + "/" + name);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

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
      KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("rollout");
    command.add("resume");
    command.add(kind.toString() + "/" + name);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

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
      KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("rollout");
    command.add("restart");
    command.add(kind.toString() + "/" + name);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

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
      KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedGet(credentials, ImmutableList.of(kind), namespace);
    command.add(name);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

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
      KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command =
        kubectlNamespacedGet(credentials, ImmutableList.of(KubernetesKind.EVENT), namespace);
    command.add("--field-selector");
    command.add(
        String.format(
            "involvedObject.name=%s,involvedObject.kind=%s",
            name, StringUtils.capitalize(kind.getName())));

    JobResult<ImmutableList<KubernetesManifest>> status =
        jobExecutor.runJob(new JobRequest(command), parseManifestList());

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to read events from " + namespace + ": " + status.getError());
    }

    if (status.getError().contains("No resources found")) {
      return ImmutableList.of();
    }

    return status.getOutput().stream()
        .filter(
            x ->
                x.getInvolvedObject()
                    .getOrDefault("apiVersion", KubernetesApiGroup.NONE.toString())
                    .startsWith(kind.getApiGroup().toString()))
        .collect(ImmutableList.toImmutableList());
  }

  @Nonnull
  public ImmutableList<KubernetesManifest> list(
      KubernetesV2Credentials credentials,
      List<KubernetesKind> kinds,
      String namespace,
      KubernetesSelectorList selectors) {
    List<String> command = kubectlNamespacedGet(credentials, kinds, namespace);
    if (selectors.isNotEmpty()) {
      command.add("-l=" + selectors.toString());
    }

    JobResult<ImmutableList<KubernetesManifest>> status =
        jobExecutor.runJob(new JobRequest(command), parseManifestList());

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException(
          "Failed to read " + kinds + " from " + namespace + ": " + status.getError());
    }

    if (status.getError().contains("No resources found")) {
      return ImmutableList.of();
    }

    return status.getOutput();
  }

  public KubernetesManifest deploy(
      KubernetesV2Credentials credentials, KubernetesManifest manifest) {
    List<String> command = kubectlAuthPrefix(credentials);

    String manifestAsJson = gson.toJson(manifest);

    // Read from stdin
    command.add("apply");
    command.add("-o");
    command.add("json");
    command.add("-f");
    command.add("-");

    JobResult<String> status =
        jobExecutor.runJob(
            new JobRequest(command, new ByteArrayInputStream(manifestAsJson.getBytes())));

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
      KubernetesV2Credentials credentials, KubernetesManifest manifest) {
    List<String> command = kubectlAuthPrefix(credentials);

    String manifestAsJson = gson.toJson(manifest);

    // Read from stdin
    command.add("replace");
    command.add("-o");
    command.add("json");
    command.add("-f");
    command.add("-");

    JobResult<String> status =
        jobExecutor.runJob(
            new JobRequest(command, new ByteArrayInputStream(manifestAsJson.getBytes())));

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

  public KubernetesManifest create(
      KubernetesV2Credentials credentials, KubernetesManifest manifest) {
    List<String> command = kubectlAuthPrefix(credentials);

    String manifestAsJson = gson.toJson(manifest);

    // Read from stdin
    command.add("create");
    command.add("-o");
    command.add("json");
    command.add("-f");
    command.add("-");

    JobResult<String> status =
        jobExecutor.runJob(
            new JobRequest(command, new ByteArrayInputStream(manifestAsJson.getBytes())));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException("Create failed: " + status.getError());
    }

    try {
      return gson.fromJson(status.getOutput(), KubernetesManifest.class);
    } catch (JsonSyntaxException e) {
      throw new KubectlException("Failed to parse kubectl output: " + e.getMessage(), e);
    }
  }

  private List<String> kubectlAuthPrefix(KubernetesV2Credentials credentials) {
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
      KubernetesV2Credentials credentials, String namespace) {
    List<String> command = kubectlAuthPrefix(credentials);

    if (!Strings.isNullOrEmpty(namespace)) {
      command.add("--namespace=" + namespace);
    }

    return command;
  }

  private List<String> kubectlNamespacedGet(
      KubernetesV2Credentials credentials, List<KubernetesKind> kind, String namespace) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);
    command.add("-o");
    command.add("json");

    command.add("get");
    command.add(kind.stream().map(KubernetesKind::toString).collect(Collectors.joining(",")));

    return command;
  }

  private String getOAuthToken(KubernetesV2Credentials credentials) {
    List<String> command = new ArrayList<>();
    command.add(oAuthExecutable);
    command.add("fetch");
    command.add("--json");
    command.add(credentials.getOAuthServiceAccount());
    command.addAll(credentials.getOAuthScopes());

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException("Could not fetch OAuth token: " + status.getError());
    }
    return status.getOutput();
  }

  public ImmutableList<KubernetesPodMetric> topPod(
      KubernetesV2Credentials credentials, String namespace, String pod) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);
    command.add("top");
    command.add("po");
    if (pod != null) {
      command.add(pod);
    }
    command.add("--containers");

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      if (status.getError().contains("not available")) {
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
      KubernetesV2Credentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      KubernetesPatchOptions options,
      List<JsonPatch> patches) {
    return patch(credentials, kind, namespace, name, options, gson.toJson(patches));
  }

  public Void patch(
      KubernetesV2Credentials credentials,
      KubernetesKind kind,
      String namespace,
      String name,
      KubernetesPatchOptions options,
      KubernetesManifest manifest) {
    return patch(credentials, kind, namespace, name, options, gson.toJson(manifest));
  }

  private Void patch(
      KubernetesV2Credentials credentials,
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

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

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
