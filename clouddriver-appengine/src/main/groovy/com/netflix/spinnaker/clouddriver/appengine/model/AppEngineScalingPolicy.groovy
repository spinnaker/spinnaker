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

package com.netflix.spinnaker.clouddriver.appengine.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.google.api.services.appengine.v1.model.AutomaticScaling
import com.google.api.services.appengine.v1.model.BasicScaling
import com.google.api.services.appengine.v1.model.ManualScaling
import groovy.transform.TupleConstructor

@JsonInclude(JsonInclude.Include.NON_NULL)
class AppEngineScalingPolicy implements Serializable {
  ScalingPolicyType type

  // Automatic scaling
  String coolDownPeriod
  Integer maxConcurrentRequests
  Integer maxIdleInstances
  String maxPendingLatency
  Integer maxTotalInstances
  Integer minIdleInstances
  String minPendingLatency
  Integer minTotalInstances
  CpuUtilization cpuUtilization
  DiskUtilization diskUtilization
  NetworkUtilization networkUtilization
  RequestUtilization requestUtilization

  // Basic scaling
  String idleTimeout
  Integer maxInstances

  // Manual scaling
  Integer instances

  AppEngineScalingPolicy() {}

  AppEngineScalingPolicy(AutomaticScaling automaticScaling) {
    type = ScalingPolicyType.AUTOMATIC

    automaticScaling?.with {
      this.coolDownPeriod = getCoolDownPeriod()
      this.maxConcurrentRequests = getMaxConcurrentRequests()
      this.maxIdleInstances = getMaxIdleInstances()
      this.maxPendingLatency = getMaxPendingLatency()
      this.maxTotalInstances = getMaxTotalInstances()
      this.minIdleInstances = getMinIdleInstances()
      this.minPendingLatency = getMinPendingLatency()
      this.minTotalInstances = getMinTotalInstances()

      getCpuUtilization()?.with {
        this.cpuUtilization = new CpuUtilization(getAggregationWindowLength(),
                                                 getTargetUtilization())
      }

      getNetworkUtilization()?.with {
        this.networkUtilization = new NetworkUtilization(getTargetReceivedBytesPerSecond(),
                                                         getTargetReceivedPacketsPerSecond(),
                                                         getTargetSentBytesPerSecond(),
                                                         getTargetSentPacketsPerSecond())
      }

      getDiskUtilization()?.with {
        this.diskUtilization = new DiskUtilization(getTargetReadBytesPerSecond(),
                                                   getTargetReadOpsPerSecond(),
                                                   getTargetWriteBytesPerSecond(),
                                                   getTargetWriteOpsPerSecond())
      }

      getRequestUtilization()?.with {
        this.requestUtilization = new RequestUtilization(getTargetConcurrentRequests(),
                                                         getTargetRequestCountPerSecond())
      }
    }
  }

  AppEngineScalingPolicy(BasicScaling basicScaling) {
    type = ScalingPolicyType.BASIC
    idleTimeout = basicScaling.getIdleTimeout()
    maxInstances = basicScaling.getMaxInstances()
  }

  AppEngineScalingPolicy(ManualScaling manualScaling) {
    type = ScalingPolicyType.MANUAL
    instances = manualScaling.getInstances()
  }
}

enum ScalingPolicyType {
  AUTOMATIC,
  BASIC,
  MANUAL,
}

@TupleConstructor
class CpuUtilization {
  String aggregationWindowLength
  Double targetUtilization
}

@TupleConstructor
class DiskUtilization {
  Integer targetReadBytesPerSecond
  Integer targetReadOpsPerSecond
  Integer targetWriteBytesPerSecond
  Integer targetWriteOpsPerSecond
}

@TupleConstructor
class NetworkUtilization {
  Integer targetReceivedBytesPerSecond
  Integer targetReceivedPacketsPerSecond
  Integer targetSentBytesPerSecond
  Integer targetSentPacketsPerSecond
}

@TupleConstructor
class RequestUtilization {
  Integer targetConcurrentRequests
  Integer targetRequestCountPerSecond
}
