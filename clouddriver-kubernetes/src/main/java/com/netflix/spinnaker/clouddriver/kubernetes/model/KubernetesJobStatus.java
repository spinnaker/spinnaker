/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.model.JobState;
import com.netflix.spinnaker.clouddriver.model.JobStatus;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import java.util.*;
import lombok.Data;

@Data
public class KubernetesJobStatus implements JobStatus {

  String name;
  String cluster;
  String account;
  String id;
  String location;
  String provider = "kubernetes";
  Long createdTime;
  Long completedTime;
  String message;
  String reason;
  Integer exitCode;
  Integer signal;
  String logs;
  @JsonIgnore V1Job job;
  List<PodStatus> pods;

  public KubernetesJobStatus(V1Job job, String account) {
    this.job = job;
    this.account = account;
    this.name = job.getMetadata().getName();
    this.location = job.getMetadata().getNamespace();
    this.createdTime = job.getMetadata().getCreationTimestamp().getMillis();
  }

  @Override
  public Map<String, String> getCompletionDetails() {
    Map<String, String> details = new HashMap<>();
    details.put("exitCode", this.exitCode != null ? this.exitCode.toString() : "");
    details.put("signal", this.signal != null ? this.signal.toString() : "");
    details.put("message", this.message != null ? this.message : "");
    details.put("reason", this.reason != null ? this.reason : "");
    return details;
  }

  @Override
  public JobState getJobState() {
    V1JobStatus status = job.getStatus();
    if (status == null) {
      return JobState.Running;
    }
    int completions = Optional.of(job.getSpec()).map(V1JobSpec::getCompletions).orElse(1);
    int succeeded = Optional.of(status).map(V1JobStatus::getSucceeded).orElse(0);

    if (succeeded < completions) {
      List<V1JobCondition> conditions = status.getConditions();
      conditions = conditions != null ? conditions : ImmutableList.of();
      Optional<V1JobCondition> condition = conditions.stream().filter(this::jobFailed).findFirst();
      return condition.isPresent() ? JobState.Failed : JobState.Running;
    }
    return JobState.Succeeded;
  }

  private boolean jobFailed(V1JobCondition condition) {
    return "Failed".equalsIgnoreCase(condition.getType())
        && "True".equalsIgnoreCase(condition.getStatus());
  }

  @Data
  public static class PodStatus {
    private String name;
    private V1PodStatus status;

    public PodStatus(V1Pod pod) {
      this.name = pod.getMetadata().getName();
      this.status = pod.getStatus();
    }
  }
}
