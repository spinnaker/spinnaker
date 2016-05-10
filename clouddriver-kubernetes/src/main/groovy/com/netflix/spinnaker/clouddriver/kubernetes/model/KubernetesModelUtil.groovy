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

import com.netflix.spinnaker.clouddriver.model.HealthState

import java.text.SimpleDateFormat

class KubernetesModelUtil {
  static long translateTime(String time) {
    time ? (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX").parse(time)).getTime() : 0
  }

  static HealthState getHealthState(health) {
    someUpRemainingUnknown(health) ? HealthState.Up :
        someSucceededRemainingUnknown(health) ? HealthState.Succeeded :
            anyStarting(health) ? HealthState.Starting :
                anyDown(health) ? HealthState.Down :
                    anyFailed(health) ? HealthState.Failed :
                        anyOutOfService(health) ? HealthState.OutOfService :
                            HealthState.Unknown
  }

  private static boolean anyDown(List<Map<String, String>> healthsList) {
    healthsList.any { it.state == HealthState.Down.name() }
  }

  private static boolean someUpRemainingUnknown(List<Map<String, String>> healthsList) {
    List<Map<String, String>> knownHealthList = healthsList.findAll { it.state != HealthState.Unknown.name() }
    knownHealthList ? knownHealthList.every { it.state == HealthState.Up.name() } : false
  }

  private static boolean someSucceededRemainingUnknown(List<Map<String, String>> healthsList) {
    List<Map<String, String>> knownHealthList = healthsList.findAll { it.state != HealthState.Unknown.name() }
    knownHealthList ? knownHealthList.every { it.state == HealthState.Succeeded.name() } : false
  }

  private static boolean anyStarting(List<Map<String, String>> healthsList) {
    healthsList.any { it.state == HealthState.Starting.name() }
  }

  private static boolean anyFailed(List<Map<String, String>> healthsList) {
    healthsList.any { it.state == HealthState.Failed.name() }
  }

  private static boolean anyOutOfService(List<Map<String, String>> healthsList) {
    healthsList.any { it.state == HealthState.OutOfService.name() }
  }
}
