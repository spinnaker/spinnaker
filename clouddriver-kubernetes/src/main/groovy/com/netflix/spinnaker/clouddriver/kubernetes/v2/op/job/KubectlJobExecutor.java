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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobStatus;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
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

  @Value("${kubernetes.kubectl.poll.maxSleepMillis:1000}")
  Long maxSleepMillis;

  @Value("${kubernetes.kubectl.poll.timeout:10000}")
  Long timeoutMillis;

  @Value("${kubernetes.kubectl.poll.maxInterruptRetries:10}")
  Long maxInterruptRetries;

  @Value("${kubernetes.kubectl.executable:kubectl}")
  String executable;

  private final JobExecutor jobExecutor;

  private final ObjectMapper mapper;

  @Autowired
  KubectlJobExecutor(JobExecutor jobExecutor, ObjectMapper mapper) {
    this.jobExecutor = jobExecutor;
    this.mapper = mapper;
  }

  public void deployManifest(KubernetesV2Credentials credentials, KubernetesManifest manifest) {
    List<String> command = new ArrayList<>();
    command.add(executable);

    String kubeconfigFile = credentials.getKubeconfigFile();
    if (StringUtils.isNotEmpty(kubeconfigFile)) {
      command.add("--kubeconfig");
      command.add(kubeconfigFile);
    }

    String context = credentials.getContext();
    if (StringUtils.isNotEmpty(context)) {
      command.add("--context");
      command.add(context);
    }

    String manifestAsJson;
    try {
      manifestAsJson = mapper.writeValueAsString(manifest);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Unexpected json deserialize error: " + e.getMessage(), e);
    }

    // Read from stdin
    command.add("apply");
    command.add("-f");
    command.add("-");

    String jobId = jobExecutor.startJob(new JobRequest(command),
        System.getenv(),
        new ByteArrayInputStream(manifestAsJson.getBytes()));

    JobStatus status = backoffWait(jobId);

    if (status.getResult() == JobStatus.Result.SUCCESS) {
      return;
    }

    throw new KubectlException("Deploy failed: " + status.getStdErr());
  }

  private JobStatus backoffWait(String jobId) {
    long nextSleep = minSleepMillis;
    long totalSleep = 0;
    long interrupts = 0;

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

      JobStatus jobStatus = jobExecutor.updateJob(jobId);
      if (jobStatus == null) {
        log.warn("Job status couldn't be inferred from {}", jobId);
      } else if (jobStatus.getState() == JobStatus.State.COMPLETED) {
        return jobStatus;
      }
    }

    throw new KubectlException("Job took too long to complete");
  }

  public class KubectlException extends RuntimeException {
    public KubectlException(String message) {
      super(message);
    }
  }
}
