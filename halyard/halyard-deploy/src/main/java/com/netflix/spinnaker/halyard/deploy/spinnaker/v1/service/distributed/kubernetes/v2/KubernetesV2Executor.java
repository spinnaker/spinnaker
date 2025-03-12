/*
 * Copyright 2019 Andreas Bergmeier
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2;

import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.core.job.v1.JobRequest;
import com.netflix.spinnaker.halyard.core.job.v1.JobStatus;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskInterrupted;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class KubernetesV2Executor {
  private KubernetesAccount account;
  private JobExecutor executor;
  private KubernetesV2Utils kubernetesV2Utils;

  public KubernetesV2Executor(
      JobExecutor executor, KubernetesAccount account, KubernetesV2Utils kubernetesV2Utils) {
    this.executor = executor;
    this.account = account;
    this.kubernetesV2Utils = kubernetesV2Utils;
  }

  public KubernetesV2Utils getKubernetesV2Utils() {
    return kubernetesV2Utils;
  }

  public boolean exists(String manifest) {
    Map<String, Object> parsedManifest = kubernetesV2Utils.parseManifest(manifest);
    String kind = (String) parsedManifest.get("kind");
    Map<String, Object> metadata =
        (Map<String, Object>) parsedManifest.getOrDefault("metadata", new HashMap<>());
    String name = (String) metadata.get("name");
    String namespace = (String) metadata.get("namespace");

    return exists(namespace, kind, name);
  }

  private boolean exists(String namespace, String kind, String name) {
    log.info("Checking for " + kind + "/" + name);
    List<String> command = kubernetesV2Utils.kubectlPrefix(account);

    if (StringUtils.isNotEmpty(namespace)) {
      command.add("-n");
      command.add(namespace);
    }

    command.add("get");
    command.add(kind);
    command.add(name);

    JobRequest request = new JobRequest().setTokenizedCommand(command);

    String jobId = executor.startJob(request);

    JobStatus status;
    try {
      status = executor.backoffWait(jobId);
    } catch (InterruptedException e) {
      throw new DaemonTaskInterrupted(e);
    }

    if (status.getState() != JobStatus.State.COMPLETED) {
      throw new HalException(
          Problem.Severity.FATAL,
          String.join(
              "\n",
              "Unterminated check for " + kind + "/" + name + " in " + namespace,
              status.getStdErr(),
              status.getStdOut()));
    }

    if (status.getResult() == JobStatus.Result.SUCCESS) {
      return true;
    } else if (status.getStdErr().contains("NotFound")) {
      return false;
    } else {
      throw new HalException(
          Problem.Severity.FATAL,
          String.join(
              "\n",
              "Failed check for " + kind + "/" + name + " in " + namespace,
              status.getStdErr(),
              status.getStdOut()));
    }
  }

  public boolean isReady(String namespace, String service) {
    log.info("Checking readiness for " + service);
    List<String> command = kubernetesV2Utils.kubectlPrefix(account);

    if (StringUtils.isNotEmpty(namespace)) {
      command.add("-n=" + namespace);
    }

    command.add("get");
    command.add("po");

    command.add("-l=cluster=" + service);
    command.add("-o=jsonpath='{.items[*].status.containerStatuses[*].ready}'");
    // This command returns a space-separated string of true/false values indicating whether each of
    // the pod's containers are READY.
    // e.g., if we are querying two spin-orca pods and both pods' monitoring-daemon containers are
    // READY but the orca containers are not READY, the output may be 'true false true false'.

    JobRequest request = new JobRequest().setTokenizedCommand(command);

    String jobId = executor.startJob(request);

    JobStatus status;
    try {
      status = executor.backoffWait(jobId);
    } catch (InterruptedException e) {
      throw new DaemonTaskInterrupted(e);
    }

    if (status.getState() != JobStatus.State.COMPLETED) {
      throw new HalException(
          Problem.Severity.FATAL,
          String.join(
              "\n",
              "Unterminated readiness check for " + service + " in " + namespace,
              status.getStdErr(),
              status.getStdOut()));
    }

    if (status.getResult() == JobStatus.Result.SUCCESS) {
      String readyStatuses = status.getStdOut();
      if (readyStatuses.isEmpty()) {
        return false;
      }
      readyStatuses =
          readyStatuses.substring(
              1, readyStatuses.length() - 1); // Strip leading and trailing single quote
      if (readyStatuses.isEmpty()) {
        return false;
      }
      return Arrays.stream(readyStatuses.split(" ")).allMatch(s -> s.equals("true"));
    } else {
      throw new HalException(
          Problem.Severity.FATAL,
          String.join(
              "\n",
              "Failed readiness check for " + service + " in " + namespace,
              status.getStdErr(),
              status.getStdOut()));
    }
  }

  public void deleteSpinnaker(String namespace) {
    List<String> command = kubernetesV2Utils.kubectlPrefix(account);
    if (StringUtils.isNotEmpty(namespace)) {
      command.add("-n=" + namespace);
    }

    command.add("delete");
    command.add("deploy,svc,secret");
    command.add("-l=app=spin");

    JobRequest request = new JobRequest().setTokenizedCommand(command);

    String jobId = executor.startJob(request);

    JobStatus status;
    try {
      status = executor.backoffWait(jobId);
    } catch (InterruptedException e) {
      throw new DaemonTaskInterrupted(e);
    }

    if (status.getState() != JobStatus.State.COMPLETED) {
      throw new HalException(
          Problem.Severity.FATAL,
          String.join(
              "\n",
              "Deleting spinnaker never completed in " + namespace,
              status.getStdErr(),
              status.getStdOut()));
    }

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      throw new HalException(
          Problem.Severity.FATAL,
          String.join(
              "\n",
              "Deleting spinnaker failed in " + namespace,
              status.getStdErr(),
              status.getStdOut()));
    }
  }

  public void delete(String namespace, String service) {
    List<String> command = kubernetesV2Utils.kubectlPrefix(account);
    if (StringUtils.isNotEmpty(namespace)) {
      command.add("-n=" + namespace);
    }

    command.add("delete");
    command.add("deploy,svc,secret");
    command.add("-l=cluster=" + service);

    JobRequest request = new JobRequest().setTokenizedCommand(command);

    String jobId = executor.startJob(request);

    JobStatus status;
    try {
      status = executor.backoffWait(jobId);
    } catch (InterruptedException e) {
      throw new DaemonTaskInterrupted(e);
    }

    if (status.getState() != JobStatus.State.COMPLETED) {
      throw new HalException(
          Problem.Severity.FATAL,
          String.join(
              "\n",
              "Deleting service " + service + " never completed",
              status.getStdErr(),
              status.getStdOut()));
    }

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      throw new HalException(
          Problem.Severity.FATAL,
          String.join(
              "\n",
              "Deleting service " + service + " failed",
              status.getStdErr(),
              status.getStdOut()));
    }
  }

  public void apply(String manifest) {
    manifest = kubernetesV2Utils.prettify(manifest);
    List<String> command = kubernetesV2Utils.kubectlPrefix(account);
    command.add("apply");
    command.add("-f");
    command.add("-"); // read from stdin

    JobRequest request = new JobRequest().setTokenizedCommand(command);

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    String jobId =
        executor.startJob(
            request,
            System.getenv(),
            new ByteArrayInputStream(manifest.getBytes()),
            stdout,
            stderr);

    JobStatus status;
    try {
      status = executor.backoffWait(jobId);
    } catch (InterruptedException e) {
      throw new DaemonTaskInterrupted(e);
    }

    if (status.getState() != JobStatus.State.COMPLETED) {
      throw new HalException(
          Problem.Severity.FATAL,
          String.join(
              "\n",
              "Unterminated deployment of manifest:",
              manifest,
              stderr.toString(),
              stdout.toString()));
    }

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      throw new HalException(
          Problem.Severity.FATAL,
          String.join(
              "\n", "Failed to deploy manifest:", manifest, stderr.toString(), stdout.toString()));
    }
  }

  public void replace(String manifest) {
    manifest = kubernetesV2Utils.prettify(manifest);
    List<String> command = kubernetesV2Utils.kubectlPrefix(account);
    command.add("replace");
    command.add("--force");
    command.add("-f");
    command.add("-"); // read from stdin

    JobRequest request = new JobRequest().setTokenizedCommand(command);

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    String jobId =
        executor.startJob(
            request,
            System.getenv(),
            new ByteArrayInputStream(manifest.getBytes()),
            stdout,
            stderr);

    JobStatus status;
    try {
      status = executor.backoffWait(jobId);
    } catch (InterruptedException e) {
      throw new DaemonTaskInterrupted(e);
    }

    if (status.getState() != JobStatus.State.COMPLETED) {
      throw new HalException(
          Problem.Severity.FATAL,
          String.join(
              "\n",
              "Unterminated deployment of manifest:",
              manifest,
              stderr.toString(),
              stdout.toString()));
    }

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      throw new HalException(
          Problem.Severity.FATAL,
          String.join(
              "\n", "Failed to deploy manifest:", manifest, stderr.toString(), stdout.toString()));
    }
  }
}
