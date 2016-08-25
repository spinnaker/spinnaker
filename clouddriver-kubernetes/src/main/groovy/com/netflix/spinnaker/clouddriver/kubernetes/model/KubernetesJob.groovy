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
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Job
import com.netflix.spinnaker.clouddriver.model.JobState

class KubernetesJob implements Job, Serializable {
  String name
  String cluster
  String account
  String id
  String location
  String provider = "kubernetes"
  Long createdTime
  KubernetesInstance instance
  Set<String> loadBalancers
  Set<String> securityGroups

  KubernetesJob(KubernetesInstance instance, String account) {
    this.name = instance.name
    this.cluster = Names.parseName(this.name).cluster
    this.location = instance.location
    this.account = account
    this.instance = instance
    this.createdTime = instance.launchTime
  }

  @Override
  Long getFinishTime() {
    if (jobState == JobState.Running || jobState == JobState.Starting || jobState == JobState.Unknown) {
      return 0;
    } else {
      return 0 // TODO(lwander)
    }
  }

  @Override
  JobState getJobState() {
    switch (instance.healthState) {
      case HealthState.Up:
        return JobState.Running
      case HealthState.Down:
        return JobState.Unknown
      case HealthState.Unknown:
        return JobState.Unknown
      case HealthState.Starting:
        return JobState.Starting
      case HealthState.Succeeded:
        return JobState.Succeeded
      case HealthState.Failed:
        return JobState.Failed
      default:
        return JobState.Unknown
    }
  }
}
