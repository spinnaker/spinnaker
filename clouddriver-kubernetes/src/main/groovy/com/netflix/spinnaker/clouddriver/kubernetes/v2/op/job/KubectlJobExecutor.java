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
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobStatus;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPatchOptions;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPodMetric.ContainerMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestList;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesSelectorList;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import io.kubernetes.client.models.V1DeleteOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class KubectlJobExecutor {
  @Value("${kubernetes.kubectl.poll.minSleepMillis:200}")
  Long minSleepMillis;

  @Value("${kubernetes.kubectl.poll.maxSleepMillis:4000}")
  Long maxSleepMillis;

  @Value("${kubernetes.kubectl.poll.timeoutMillis:100000}")
  Long timeoutMillis;

  @Value("${kubernetes.kubectl.poll.maxInterruptRetries:10}")
  Long maxInterruptRetries;

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

    String jobId = jobExecutor.startJob(new JobRequest(command),
      System.getenv(),
      new ByteArrayInputStream(new byte[0]));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      throw new KubectlException("Failed get current configuration context");
    }

    return status.getStdOut();
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

    String jobId = jobExecutor.startJob(new JobRequest(command),
      System.getenv(),
      new ByteArrayInputStream(new byte[0]));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      throw new KubectlException("Failed get current configuration context");
    }
    return status.getStdOut();
  }

  public String logs(KubernetesV2Credentials credentials, String namespace, String podName, String containerName) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);
    command.add("logs");
    command.add(podName);
    command.add("-c=" + containerName);

    String jobId = jobExecutor.startJob(new JobRequest(command),
        System.getenv(),
        new ByteArrayInputStream(new byte[0]));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      throw new KubectlException("Failed to get logs from " + podName + "/" + containerName + " in " + namespace + ": " + status.getStdErr());
    }

    return status.getStdOut();
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

    String jobId = jobExecutor.startJob(new JobRequest(command),
        System.getenv(),
        new ByteArrayInputStream(new byte[0]));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      String id;
      if (StringUtils.isNotEmpty(name)) {
        id = kind + "/" + name;
      } else {
        id = labelSelectors.toString();
      }
      throw new KubectlException("Failed to delete " + id + " from " + namespace + ": " + status.getStdErr());
    }

    if (StringUtils.isEmpty(status.getStdOut()) || status.getStdOut().equals("No output from command.") || status.getStdOut().startsWith("No resources found")) {
      return new ArrayList<>();
    }

    return Arrays.stream(status.getStdOut().split("\n"))
        .map(m -> m.substring(m.indexOf("\"") + 1))
        .map(m -> m.substring(0, m.lastIndexOf("\"")))
        .collect(Collectors.toList());
  }

  public Void scale(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name, int replicas) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("scale");
    command = kubectlLookupInfo(command, kind, name, null);
    command.add("--replicas=" + replicas);

    String jobId = jobExecutor.startJob(new JobRequest(command),
        System.getenv(),
        new ByteArrayInputStream(new byte[0]));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      throw new KubectlException("Failed to scale " + kind + "/" + name + " from " + namespace + ": " + status.getStdErr());
    }

    return null;
  }

  public List<Integer> historyRollout(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("rollout");
    command.add("history");
    command.add(kind.toString() + "/" + name);

    String jobId = jobExecutor.startJob(new JobRequest(command),
        System.getenv(),
        new ByteArrayInputStream(new byte[0]));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      throw new KubectlException("Failed to get rollout history of " + kind + "/" + name + " from " + namespace + ": " + status.getStdErr());
    }

    String stdout = status.getStdOut();
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

    String jobId = jobExecutor.startJob(new JobRequest(command),
        System.getenv(),
        new ByteArrayInputStream(new byte[0]));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      throw new KubectlException("Failed to undo rollout " + kind + "/" + name + " from " + namespace + ": " + status.getStdErr());
    }

    return null;
  }

  public Void pauseRollout(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("rollout");
    command.add("pause");
    command.add(kind.toString() + "/" + name);

    String jobId = jobExecutor.startJob(new JobRequest(command),
        System.getenv(),
        new ByteArrayInputStream(new byte[0]));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      throw new KubectlException("Failed to pause rollout " + kind + "/" + name + " from " + namespace + ": " + status.getStdErr());
    }

    return null;
  }

  public Void resumeRollout(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("rollout");
    command.add("resume");
    command.add(kind.toString() + "/" + name);

    String jobId = jobExecutor.startJob(new JobRequest(command),
        System.getenv(),
        new ByteArrayInputStream(new byte[0]));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      throw new KubectlException("Failed to resume rollout " + kind + "/" + name + " from " + namespace + ": " + status.getStdErr());
    }

    return null;
  }


  public KubernetesManifest get(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedGet(credentials, Collections.singletonList(kind), namespace);
    command.add(name);

    String jobId = jobExecutor.startJob(new JobRequest(command),
        System.getenv(),
        new ByteArrayInputStream(new byte[0]));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      if (status.getStdErr().contains("(NotFound)")) {
        return null;
      } else if (status.getStdErr().contains(NO_RESOURCE_TYPE_ERROR)) {
        throw new NoResourceTypeException(status.getStdErr());
      }

      throw new KubectlException("Failed to read " + kind + " from " + namespace + ": " + status.getStdErr());
    }

    try {
      return gson.fromJson(status.getStdOut(), KubernetesManifest.class);
    } catch (JsonSyntaxException e) {
      throw new KubectlException("Failed to parse kubectl output: " + e.getMessage(), e);
    }
  }

  public List<KubernetesManifest> list(KubernetesV2Credentials credentials, List<KubernetesKind> kinds, String namespace) {
    String jobId = jobExecutor.startJob(new JobRequest(kubectlNamespacedGet(credentials, kinds, namespace)),
        System.getenv(),
        new ByteArrayInputStream(new byte[0]));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      if (status.getStdErr().contains(NO_RESOURCE_TYPE_ERROR)) {
        throw new NoResourceTypeException(status.getStdErr());
      } else {
        throw new KubectlException("Failed to read " + kinds + " from " + namespace + ": " + status.getStdErr());
      }
    }

    if (status.getStdErr().contains("No resources found")) {
      return new ArrayList<>();
    }

    try {
      KubernetesManifestList list = gson.fromJson(status.getStdOut(), KubernetesManifestList.class);
      return list.getItems();
    } catch (JsonSyntaxException e) {
      throw new KubectlException("Failed to parse kubectl output: " + e.getMessage(), e);
    }
  }

  public Void deploy(KubernetesV2Credentials credentials, KubernetesManifest manifest) {
    List<String> command = kubectlAuthPrefix(credentials);

    String manifestAsJson = gson.toJson(manifest);

    // Read from stdin
    command.add("apply");
    command.add("-f");
    command.add("-");

    String jobId = jobExecutor.startJob(new JobRequest(command),
        System.getenv(),
        new ByteArrayInputStream(manifestAsJson.getBytes()));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      throw new KubectlException("Deploy failed: " + status.getStdErr());
    }

    return null;
  }

  private JobStatus backoffWait(String jobId, boolean debug) {
    long nextSleep = minSleepMillis;
    long totalSleep = 0;
    long interrupts = 0;
    JobStatus jobStatus = null;

    while (totalSleep < timeoutMillis && interrupts < maxInterruptRetries) {
      try {
        Thread.sleep(nextSleep);
      } catch (InterruptedException e) {
        log.warn("{} was interrupted", jobId, e);
        interrupts += 1;
      } finally {
        totalSleep += nextSleep;
        nextSleep = Math.min(nextSleep * 2, maxSleepMillis);
      }

      jobStatus = jobExecutor.updateJob(jobId);
      if (jobStatus == null) {
        log.warn("Job status couldn't be inferred from {}", jobId);
      } else if (jobStatus.getState() == JobStatus.State.COMPLETED) {
        if (debug) {
          logDebugMessages(jobId, jobStatus);
        }
        return jobStatus;
      }
    }

    if (debug) {
      logDebugMessages(jobId, jobStatus);
    }
    throw new KubectlException("Job took too long to complete");
  }

  private void logDebugMessages(String jobId, JobStatus jobStatus) {
    if (jobStatus != null) {
      log.info("{} stdout:\n{}", jobId, jobStatus.getStdOut());
      log.info("{} stderr:\n{}", jobId, jobStatus.getStdErr());
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

    String jobId = jobExecutor.startJob(new JobRequest(command),
      System.getenv(),
      new ByteArrayInputStream(new byte[0]));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      throw new KubectlException("Could not fetch OAuth token: " + status.getStdErr());
    }
    return status.getStdOut();
  }

  public Collection<KubernetesPodMetric> topPod(KubernetesV2Credentials credentials, String namespace) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);
    command.add("top");
    command.add("po");
    command.add("--containers");


    String jobId = jobExecutor.startJob(new JobRequest(command),
        System.getenv(),
        new ByteArrayInputStream(new byte[0]));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      throw new KubectlException("Could not read metrics: " + status.getStdErr());
    }

    Map<String, KubernetesPodMetric> result = new HashMap<>();

    String output = status.getStdOut().trim();
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


  public Void patch(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace,
    String name, KubernetesPatchOptions options, KubernetesManifest manifest) {
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
    command.add(gson.toJson(manifest));

    String jobId = jobExecutor.startJob(new JobRequest(command),
      System.getenv(),
      new ByteArrayInputStream(new byte[0]));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      String errMsg = status.getStdErr();
      if (StringUtils.isEmpty(errMsg)) {
        errMsg = status.getStdOut();
      }
      throw new KubectlException("Patch failed: " + errMsg);
    }

    return null;
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
