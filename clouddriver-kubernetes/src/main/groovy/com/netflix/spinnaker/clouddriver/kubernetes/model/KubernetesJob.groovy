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

import com.netflix.spinnaker.clouddriver.model.Job
import com.netflix.spinnaker.clouddriver.model.JobState
import com.netflix.spinnaker.clouddriver.model.Process

class KubernetesJob implements Job, Serializable {
  String name
  String account
  String id
  String location
  Set<Process> processes
  Long launchTime
  Set<String> loadBalancers
  Set<String> securityGroups
  io.fabric8.kubernetes.api.model.extensions.Job job

  KubernetesJob(io.fabric8.kubernetes.api.model.extensions.Job job, Set<KubernetesProcess> processes, String account) {
    this.name = job.metadata.name
    this.location = job.metadata.namespace
    this.job = job
    this.account = account
    this.processes = processes
    this.launchTime = KubernetesModelUtil.translateTime(job.metadata.creationTimestamp)
  }

  @Override
  Long getFinishTime() {
    if (jobState == JobState.Running || jobState == JobState.Starting || jobState == JobState.Unknown) {
      return 0;
    } else {
      return KubernetesModelUtil.translateTime(job.status.completionTime)
    }
  }


  @Override
  JobState getJobState() {
    job.status.succeeded == job.spec.completions ? JobState.Succeeded :
      job.status.active > 0 ? JobState.Running : JobState.Unknown  // TODO(lwander) figure out how to extract failure state
  }
}
