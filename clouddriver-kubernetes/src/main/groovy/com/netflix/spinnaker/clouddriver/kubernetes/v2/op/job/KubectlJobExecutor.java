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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import com.netflix.spinnaker.clouddriver.jobs.local.ReaderConsumer;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.JsonPatch;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPatchOptions;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPodMetric.ContainerMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesSelectorList;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import io.kubernetes.client.models.V1DeleteOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class KubectlJobExecutor {
  @Value("${kubernetes.kubectl.executable:kubectl}")
  String executable;

  @Value("${kubernetes.oAuth.executable:oauth2l}")
  String oAuthExecutable;

  private final static String NO_RESOURCE_TYPE_ERROR = "doesn't have a resource type";

  private final JobExecutor jobExecutor;

  private final Gson gson = new Gson();

  @Autowired
  KubectlJobExecutor(JobExecutor jobExecutor) {
    this.jobExecutor = jobExecutor;
  }

  public String configCurrentContext(KubernetesV2Credentials credentials) {
    List<String> command = kubectlAuthPrefix(credentials);
    command.add("config");
    command.add("current-context");

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException("Failed get current configuration context");
    }

    return status.getOutput();
  }

  public String defaultNamespace(KubernetesV2Credentials credentials) {
    String configCurrentContext = configCurrentContext(credentials);
    if (StringUtils.isEmpty(configCurrentContext)) {
      return "";
    }

    List<String> command = kubectlAuthPrefix(credentials);
    command.add("config");
    command.add("view");
    command.add("-o");
    String jsonPath = "{.contexts[?(@.name==\"" + configCurrentContext + "\")].context.namespace}";
    command.add("\"jsonPath=" + jsonPath + "\"");

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException("Failed get current configuration context");
    }
    return status.getOutput();
  }

  public String logs(KubernetesV2Credentials credentials, String namespace, String podName, String containerName) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);
    command.add("logs");
    command.add(podName);
    command.add("-c=" + containerName);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException("Failed to get logs from " + podName + "/" + containerName + " in " + namespace + ": " + status.getError());
    }

    return status.getOutput();
  }

  public String jobLogs(KubernetesV2Credentials credentials, String namespace, String jobName) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);
    command.add("logs");
    command.add("job/"+jobName);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException("Failed to get logs from job/" + jobName + " in " + namespace + ": " + status.getError());
    }

    return status.getOutput();
  }

  public List<String> delete(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name, KubernetesSelectorList labelSelectors, V1DeleteOptions deleteOptions) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("delete");

    command = kubectlLookupInfo(command, kind, name, labelSelectors);

    // spinnaker generally accepts deletes of resources that don't exist
    command.add("--ignore-not-found=true");

    if (deleteOptions.isOrphanDependents() != null) {
      command.add("--cascade=" + !deleteOptions.isOrphanDependents());
    }

    if (deleteOptions.getGracePeriodSeconds() != null) {
      command.add("--grace-period=" + deleteOptions.getGracePeriodSeconds());
    }

    if (StringUtils.isNotEmpty(deleteOptions.getPropagationPolicy())) {
      throw new IllegalArgumentException("Propagation policy is not yet supported as a delete option");
    }

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      String id;
      if (StringUtils.isNotEmpty(name)) {
        id = kind + "/" + name;
      } else {
        id = labelSelectors.toString();
      }
      throw new KubectlException("Failed to delete " + id + " from " + namespace + ": " + status.getError());
    }

    if (StringUtils.isEmpty(status.getOutput()) || status.getOutput().equals("No output from command.") || status.getOutput().startsWith("No resources found")) {
      return new ArrayList<>();
    }

    return Arrays.stream(status.getOutput().split("\n"))
        .map(m -> m.substring(m.indexOf("\"") + 1))
        .map(m -> m.substring(0, m.lastIndexOf("\"")))
        .collect(Collectors.toList());
  }

  public Void scale(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name, int replicas) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("scale");
    command = kubectlLookupInfo(command, kind, name, null);
    command.add("--replicas=" + replicas);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException("Failed to scale " + kind + "/" + name + " from " + namespace + ": " + status.getError());
    }

    return null;
  }

  public List<Integer> historyRollout(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("rollout");
    command.add("history");
    command.add(kind.toString() + "/" + name);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException("Failed to get rollout history of " + kind + "/" + name + " from " + namespace + ": " + status.getError());
    }

    String stdout = status.getOutput();
    if (StringUtils.isEmpty(stdout)) {
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

  public Void undoRollout(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name, int revision) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("rollout");
    command.add("undo");
    command.add(kind.toString() + "/" + name);
    command.add("--to-revision=" + revision);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException("Failed to undo rollout " + kind + "/" + name + " from " + namespace + ": " + status.getError());
    }

    return null;
  }

  public Void pauseRollout(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("rollout");
    command.add("pause");
    command.add(kind.toString() + "/" + name);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException("Failed to pause rollout " + kind + "/" + name + " from " + namespace + ": " + status.getError());
    }

    return null;
  }

  public Void resumeRollout(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("rollout");
    command.add("resume");
    command.add(kind.toString() + "/" + name);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException("Failed to resume rollout " + kind + "/" + name + " from " + namespace + ": " + status.getError());
    }

    return null;
  }


  public KubernetesManifest get(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedGet(credentials, Collections.singletonList(kind), namespace);
    command.add(name);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      if (status.getError().contains("(NotFound)")) {
        return null;
      } else if (status.getError().contains(NO_RESOURCE_TYPE_ERROR)) {
        throw new NoResourceTypeException(status.getError());
      }

      throw new KubectlException("Failed to read " + kind + " from " + namespace + ": " + status.getError());
    }

    try {
      return gson.fromJson(status.getOutput(), KubernetesManifest.class);
    } catch (JsonSyntaxException e) {
      throw new KubectlException("Failed to parse kubectl output: " + e.getMessage(), e);
    }
  }

  public List<KubernetesManifest> eventsFor(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedGet(credentials, Collections.singletonList(KubernetesKind.EVENT), namespace);
    command.add("--field-selector");
    command.add(String.format("involvedObject.name=%s,involvedObject.kind=%s", name, StringUtils.capitalize(kind.toString())));

    JobResult<List<KubernetesManifest>> status = jobExecutor.runJob(new JobRequest(command), parseManifestList());

    if (status.getResult() != JobResult.Result.SUCCESS) {
      if (status.getError().contains(NO_RESOURCE_TYPE_ERROR)) {
        throw new NoResourceTypeException(status.getError());
      } else {
        throw new KubectlException("Failed to read events from " + namespace + ": " + status.getError());
      }
    }

    if (status.getError().contains("No resources found")) {
      return new ArrayList<>();
    }

    return status.getOutput();
  }

  public List<KubernetesManifest> list(KubernetesV2Credentials credentials, List<KubernetesKind> kinds, String namespace, KubernetesSelectorList selectors) {
    List<String> command = kubectlNamespacedGet(credentials, kinds, namespace);
    if (selectors.isNotEmpty()) {
      command.add("-l=" + selectors.toString());
    }

    JobResult<List<KubernetesManifest>> status = jobExecutor.runJob(new JobRequest(command), parseManifestList());

    if (status.getResult() != JobResult.Result.SUCCESS) {
      if (status.getError().contains(NO_RESOURCE_TYPE_ERROR)) {
        throw new NoResourceTypeException(status.getError());
      } else {
        throw new KubectlException("Failed to read " + kinds + " from " + namespace + ": " + status.getError());
      }
    }

    if (status.getError().contains("No resources found")) {
      return new ArrayList<>();
    }

    return status.getOutput();
  }

  public Void deploy(KubernetesV2Credentials credentials, KubernetesManifest manifest) {
    List<String> command = kubectlAuthPrefix(credentials);

    String manifestAsJson = gson.toJson(manifest);

    // Read from stdin
    command.add("apply");
    command.add("-f");
    command.add("-");

    JobResult<String> status = jobExecutor.runJob(new JobRequest(
      command,
      new ByteArrayInputStream(manifestAsJson.getBytes())
    ));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException("Deploy failed: " + status.getError());
    }

    return null;
  }

  private void logDebugMessages(String jobId, JobResult<String> jobResult) {
    if (jobResult != null) {
      log.info("{} stdout:\n{}", jobId, jobResult.getOutput());
      log.info("{} stderr:\n{}", jobId, jobResult.getError());
    } else {
      log.info("{} job status not set");
    }
  }

  private List<String> kubectlAuthPrefix(KubernetesV2Credentials credentials) {
    List<String> command = new ArrayList<>();
    if (StringUtils.isNotEmpty(credentials.getKubectlExecutable())) {
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
      if (credentials.getOAuthServiceAccount() != null && !credentials.getOAuthServiceAccount().isEmpty()) {
        command.add("--token=" + getOAuthToken(credentials));
      }

      String kubeconfigFile = credentials.getKubeconfigFile();
      if (StringUtils.isNotEmpty(kubeconfigFile)) {
        command.add("--kubeconfig=" + kubeconfigFile);
      }

      String context = credentials.getContext();
      if (StringUtils.isNotEmpty(context)) {
        command.add("--context=" + context);
      }
    }

    return command;
  }

  private List<String> kubectlLookupInfo(List<String> command, KubernetesKind kind, String name, KubernetesSelectorList labelSelectors) {
    if (StringUtils.isNotEmpty(name)) {
      command.add(kind + "/" + name);
    } else {
      command.add(kind.toString());
    }

    if (labelSelectors != null && !labelSelectors.isEmpty()) {
      command.add("-l=" + labelSelectors);
    }

    return command;
  }

  private List<String> kubectlNamespacedAuthPrefix(KubernetesV2Credentials credentials, String namespace) {
    List<String> command = kubectlAuthPrefix(credentials);
    if (StringUtils.isEmpty(namespace)) {
      namespace = credentials.getDefaultNamespace();
    }

    if (StringUtils.isNotEmpty(namespace)) {
      command.add("--namespace=" + namespace);
    }

    return command;
  }

  private List<String> kubectlNamespacedGet(KubernetesV2Credentials credentials, List<KubernetesKind> kind, String namespace) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);
    command.add("-o");
    command.add("json");

    command.add("get");
    command.add(String.join(",", kind.stream().map(KubernetesKind::toString).collect(Collectors.toList())));

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

  public Collection<KubernetesPodMetric> topPod(KubernetesV2Credentials credentials, String namespace) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);
    command.add("top");
    command.add("po");
    command.add("--containers");


    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      throw new KubectlException("Could not read metrics: " + status.getError());
    }

    Map<String, KubernetesPodMetric> result = new HashMap<>();

    String output = status.getOutput().trim();
    if (StringUtils.isEmpty(output)) {
      log.warn("No output from `kubectl top` command, no metrics to report.");
      return new ArrayList<>();
    }

    String[] lines = output.split("\n");
    if (lines.length <= 1) {
      return new ArrayList<>();
    }

    // POD NAME CPU(cores) MEMORY(bytes) ...
    String[] header = lines[0].trim().split("\\s+");

    if (header.length <= 2) {
      log.warn("Unexpected metric format -- no metrics to report based on table header {}.", header);
      return new ArrayList<>();
    }

    // CPU(cores) MEMORY(bytes)
    String[] metricKeys = Arrays.copyOfRange(header, 2, header.length);
    for (int i = 1; i < lines.length; i++) {
      String[] entry = lines[i].trim().split("\\s+");
      if (entry.length != header.length) {
        log.warn("Entry {} does not match column width of {}, skipping", entry, header);
      }

      String podName = entry[0];
      String containerName = entry[1];

      Map<String, String> metrics = new HashMap<>();
      for (int j = 0; j < metricKeys.length; j++) {
        metrics.put(metricKeys[j], entry[j + 2]);
      }

      ContainerMetric containerMetric = ContainerMetric.builder()
          .containerName(containerName)
          .metrics(metrics)
          .build();

      KubernetesPodMetric podMetric = result.getOrDefault(podName, KubernetesPodMetric.builder()
          .podName(podName)
          .containerMetrics(new ArrayList<>())
          .build());

      podMetric.getContainerMetrics().add(containerMetric);

      result.put(podName, podMetric);
    }

    return result.values();
  }

  public Void patch(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name, KubernetesPatchOptions options, List<JsonPatch> patches) {
    return patch(credentials, kind, namespace, name, options, gson.toJson(patches));
  }

  public Void patch(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name, KubernetesPatchOptions options, KubernetesManifest manifest) {
    return patch(credentials, kind, namespace, name, options, gson.toJson(manifest));
  }

  private Void patch(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name, KubernetesPatchOptions options, String patchBody) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("patch");
    command.add(kind.toString());
    command.add(name);

    if (options.isRecord()) {
      command.add("--record");
    }

    String mergeStrategy = options.getMergeStrategy().toString();
    if (StringUtils.isNotEmpty(mergeStrategy)) {
      command.add("--type");
      command.add(mergeStrategy);
    }

    command.add("--patch");
    command.add(patchBody);

    JobResult<String> status = jobExecutor.runJob(new JobRequest(command));

    if (status.getResult() != JobResult.Result.SUCCESS) {
      String errMsg = status.getError();
      if (StringUtils.isEmpty(errMsg)) {
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

  private ReaderConsumer<List<KubernetesManifest>> parseManifestList() {
    return (BufferedReader r) -> {
      try (JsonReader reader = new JsonReader(r)) {
        List<KubernetesManifest> manifestList = new ArrayList<>();
        reader.beginObject();
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
        return manifestList;
      }
    };
  }

  public static class NoResourceTypeException extends RuntimeException {
    public NoResourceTypeException(String message) {
      super(message);
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
}
