/*
 * Copyright 2016 Google, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.model.JobState
import com.netflix.spinnaker.clouddriver.model.JobStatus
import io.fabric8.kubernetes.api.model.Pod

class KubernetesJobStatus implements JobStatus, Serializable {
  String name
  String cluster
  String account
  String id
  String location
  String provider = "kubernetes"
  Long createdTime
  Long completedTime
  String message
  String reason
  Integer exitCode
  Integer signal
  Set<String> loadBalancers
  Set<String> securityGroups
  @JsonIgnore
  Pod pod

  KubernetesJobStatus(Pod pod, String account) {
    this.name = pod.metadata.name
    this.cluster = Names.parseName(this.name).cluster
    this.location = pod.metadata.namespace
    this.account = account
    this.createdTime = KubernetesModelUtil.translateTime(pod.metadata.creationTimestamp)
    this.pod = pod

  }

  @Override
  Map<String, String> getCompletionDetails() {
    [
      exitCode: exitCode?.toString(),
      signal: signal?.toString(),
      message: message?.toString(),
      reason: reason?.toString(),
    ]
  }

  @Override
  JobState getJobState() {
    def state = pod?.status?.containerStatuses?.getAt(0)?.state
    if (state?.getRunning()) {
      return JobState.Running
    } else if (state?.getWaiting()) {
      return JobState.Starting
    } else if (state?.getTerminated()) {
      def terminated = state.getTerminated()
      completedTime = KubernetesModelUtil.translateTime(terminated.getFinishedAt())
      signal = terminated.getSignal()
      exitCode = terminated.getExitCode()
      message = terminated.getMessage()
      reason = terminated.getReason()

      if (exitCode == 0) {
        return JobState.Succeeded
      } else {
        return JobState.Failed
      }
    } else {
      return JobState.Unknown
    }
  }
}
