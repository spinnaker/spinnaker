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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestList;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import io.kubernetes.client.models.V1DeleteOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class KubectlJobExecutor {
  @Value("${kubernetes.kubectl.poll.minSleepMillis:100}")
  Long minSleepMillis;

  @Value("${kubernetes.kubectl.poll.maxSleepMillis:2000}")
  Long maxSleepMillis;

  @Value("${kubernetes.kubectl.poll.timeoutMillis:100000}")
  Long timeoutMillis;

  @Value("${kubernetes.kubectl.poll.maxInterruptRetries:10}")
  Long maxInterruptRetries;

  @Value("${kubernetes.kubectl.executable:kubectl}")
  String executable;

  private final JobExecutor jobExecutor;

  private final Gson gson = new Gson();

  @Autowired
  KubectlJobExecutor(JobExecutor jobExecutor) {
    this.jobExecutor = jobExecutor;
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

  public void delete(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name, V1DeleteOptions deleteOptions) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("delete");
    command.add(kind.toString());
    command.add(name);

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
      throw new KubectlException("Failed to delete " + kind + "/" + name + " from " + namespace + ": " + status.getStdErr());
    }
  }

  public void scale(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name, int replicas) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);

    command.add("scale");
    command.add(kind.toString() + "/" + name);
    command.add("--replicas=" + replicas);

    String jobId = jobExecutor.startJob(new JobRequest(command),
        System.getenv(),
        new ByteArrayInputStream(new byte[0]));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      throw new KubectlException("Failed to scale " + kind + "/" + name + " from " + namespace + ": " + status.getStdErr());
    }
  }

  public KubernetesManifest get(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace, String name) {
    List<String> command = kubectlNamespacedGet(credentials, kind, namespace);
    command.add(name);

    String jobId = jobExecutor.startJob(new JobRequest(command),
        System.getenv(),
        new ByteArrayInputStream(new byte[0]));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      if (status.getStdErr().contains("(NotFound)")) {
        return null;
      }

      throw new KubectlException("Failed to read " + kind + " from " + namespace + ": " + status.getStdErr());
    }

    try {
      return gson.fromJson(status.getStdOut(), KubernetesManifest.class);
    } catch (JsonSyntaxException e) {
      throw new KubectlException("Failed to parse kubectl output: " + e.getMessage(), e);
    }
  }

  public List<KubernetesManifest> getAll(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace) {
    String jobId = jobExecutor.startJob(new JobRequest(kubectlNamespacedGet(credentials, kind, namespace)),
        System.getenv(),
        new ByteArrayInputStream(new byte[0]));

    JobStatus status = backoffWait(jobId, credentials.isDebug());

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      throw new KubectlException("Failed to read " + kind + " from " + namespace + ": " + status.getStdErr());
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

  public void deploy(KubernetesV2Credentials credentials, KubernetesManifest manifest) {
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

    if (status.getResult() == JobStatus.Result.SUCCESS) {
      return;
    }

    throw new KubectlException("Deploy failed: " + status.getStdErr());
  }

  private JobStatus backoffWait(String jobId, boolean debug) {
    long nextSleep = minSleepMillis;
    long totalSleep = 0;
    long interrupts = 0;
    JobStatus jobStatus = null;

    while (totalSleep < timeoutMillis && interrupts < maxInterruptRetries) {
      try {
        Thread.sleep(totalSleep);
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
    command.add(executable);

    if (credentials.isDebug()) {
      command.add("-v");
      command.add("9");
    }

    String kubeconfigFile = credentials.getKubeconfigFile();
    if (StringUtils.isNotEmpty(kubeconfigFile)) {
      command.add("--kubeconfig=" + kubeconfigFile);
    }

    String context = credentials.getContext();
    if (StringUtils.isNotEmpty(context)) {
      command.add("--context=" + context);
    }

    return command;
  }

  private List<String> kubectlNamespacedAuthPrefix(KubernetesV2Credentials credentials, String namespace) {
    List<String> command = kubectlAuthPrefix(credentials);
    if (StringUtils.isEmpty(namespace)) {
      namespace = credentials.getDefaultNamespace();
    }

    command.add("--namespace=" + namespace);

    return command;
  }

  private List<String> kubectlNamespacedGet(KubernetesV2Credentials credentials, KubernetesKind kind, String namespace) {
    List<String> command = kubectlNamespacedAuthPrefix(credentials, namespace);
    command.add("-o");
    command.add("json");

    command.add("get");
    command.add(kind.toString());

    return command;
  }

  public class KubectlException extends RuntimeException {
    public KubectlException(String message) {
      super(message);
    }

    public KubectlException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
