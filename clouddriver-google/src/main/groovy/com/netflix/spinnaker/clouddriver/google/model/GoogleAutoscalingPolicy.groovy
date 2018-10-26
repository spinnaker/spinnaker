/*
 * Copyright 2016 Google, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.google.model

import groovy.transform.AutoClone
import groovy.transform.Canonical
import groovy.transform.ToString

@AutoClone
@Canonical
@ToString(includeNames = true)
class GoogleAutoscalingPolicy {
  Integer minNumReplicas
  Integer maxNumReplicas
  Integer coolDownPeriodSec

  CpuUtilization cpuUtilization
  LoadBalancingUtilization loadBalancingUtilization
  List<CustomMetricUtilization> customMetricUtilizations
  AutoscalingMode mode

  @ToString(includeNames = true)
  static class CpuUtilization {
    Double utilizationTarget
  }

  @ToString(includeNames = true)
  static class LoadBalancingUtilization {
    Double utilizationTarget
  }

  @ToString(includeNames = true)
  static class CustomMetricUtilization {
    String metric
    Double utilizationTarget
    UtilizationTargetType utilizationTargetType

    enum UtilizationTargetType {
      GAUGE,
      DELTA_PER_SECOND,
      DELTA_PER_MINUTE
    }
  }


  static enum AutoscalingMode {
    ON,
    OFF,
    ONLY_UP,
    ONLY_DOWN
  }
}
