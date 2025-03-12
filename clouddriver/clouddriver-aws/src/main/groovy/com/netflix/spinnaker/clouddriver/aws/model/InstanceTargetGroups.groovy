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
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.Immutable
import groovy.transform.TypeCheckingMode

@CompileStatic
@Immutable
@EqualsAndHashCode(cache = true)
class InstanceTargetGroups {
  public static final String HEALTH_TYPE = 'TargetGroup'
  public String getType() {
    HEALTH_TYPE
  }
  String instanceId
  HealthState state
  List<InstanceTargetGroupState> targetGroups

  static HealthState deriveInstanceHealthState(List<InstanceTargetGroupState> instanceTargetGroupStates) {
    instanceTargetGroupStates.any { it.healthState == HealthState.Starting } ? HealthState.Starting :
      instanceTargetGroupStates.any { it.healthState == HealthState.Down } ? HealthState.Down :
        instanceTargetGroupStates.any { it.healthState == HealthState.OutOfService } ? HealthState.OutOfService :
          instanceTargetGroupStates.any { it.healthState == HealthState.Draining } ? HealthState.Draining :
          HealthState.Up
  }

  static List<InstanceTargetGroups> fromInstanceTargetGroupStates(List<InstanceTargetGroupState> instanceTargetGroupStates) {
    instanceTargetGroupStates.groupBy { InstanceTargetGroupState itgs ->
      itgs.instanceId
    }.collect { String instanceId, List<InstanceTargetGroupState> tgs ->
      new InstanceTargetGroups(instanceId: instanceId, state: deriveInstanceHealthState(tgs), targetGroups: tgs)
    }

  }
}
