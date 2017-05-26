/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.model

import com.netflix.spinnaker.clouddriver.model.HealthState

class InstanceTargetGroupState {
  final String instanceId
  final String targetGroupName
  final String state
  final String reasonCode
  final String description

  final HealthState healthState

  InstanceTargetGroupState(String instanceId, String targetGroupName, String state, String reasonCode, String description) {
    this.instanceId = instanceId
    this.targetGroupName = targetGroupName

    this.state = state
    this.reasonCode = reasonCode
    this.description = description
    this.healthState = deriveHealthState()
  }

  private HealthState deriveHealthState() {
    //ELBv2 has concrete states: unused -> initial -> healthy    -> draining
    //                                            \-> unhealthy -/
    if (state == 'healthy') {
      return HealthState.Up
    }

    if (state == 'initial') {
      return HealthState.Starting
    }
    if (state == 'unused' || state == 'draining') {
      return HealthState.OutOfService
    }
    return HealthState.Down
  }
}
