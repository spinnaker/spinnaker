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

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.Job
import com.netflix.spinnaker.clouddriver.model.JobState
import io.fabric8.kubernetes.api.model.extensions.JobCondition
import io.fabric8.kubernetes.client.internal.SerializationUtils

class KubernetesJob implements Job, Serializable {
  String name
  String cluster
  String account
  String id
  String location
  String provider = "kubernetes"
  Set<Instance> instances
  Long launchTime
  Set<String> loadBalancers
  Set<String> securityGroups
  String yaml
  io.fabric8.kubernetes.api.model.extensions.Job job

  KubernetesJob(io.fabric8.kubernetes.api.model.extensions.Job job, Set<KubernetesInstance> instances, String account) {
    this.name = job.metadata.name
    this.cluster = Names.parseName(this.name).cluster
    this.location = job.metadata.namespace
    this.job = job
    this.account = account
    this.instances = instances ?: [] as Set
    this.launchTime = KubernetesModelUtil.translateTime(job.metadata.creationTimestamp)
    this.yaml = SerializationUtils.dumpWithoutRuntimeStateAsYaml(job)
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
    job.status.succeeded == job.spec.completions ? JobState.Succeeded : // Succeeded if threshold is met.
      job.status.active > 0 ? JobState.Running : // Otherwise, if anything is running, don't stop.
        job.status.conditions.find { JobCondition condition -> condition.type == "Failed" } ? JobState.Failed : // Condition reporting failure means we are done.
          job.status.active == 0 ? JobState.Starting : JobState.Unknown // If nothing is running, we are starting, otherwise we have negative active jobs.
  }
}
