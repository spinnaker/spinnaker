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

package com.netflix.spinnaker.clouddriver.google.model.health

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.model.Health
import com.netflix.spinnaker.clouddriver.model.HealthState
import groovy.transform.Canonical

@Canonical
class GoogleLoadBalancerHealth {

  String instanceName
  String instanceZone

  List<LBHealthSummary> lbHealthSummaries

  PlatformStatus status

  enum PlatformStatus {
    HEALTHY,
    UNHEALTHY

    HealthState toHeathState() {
      switch (this) {
        case HEALTHY:
          return HealthState.Up
        default:
          return HealthState.Down
      }
    }

    LBHealthSummary.ServiceStatus toServiceStatus() {
      switch (this) {
        case HEALTHY:
          return LBHealthSummary.ServiceStatus.InService
        default:
          return LBHealthSummary.ServiceStatus.OutOfService
      }
    }
  }

  static class LBHealthSummary {
    // These aren't the most descriptive names, but it's what's expected in Deck.
    String loadBalancerName
    String instanceId
    ServiceStatus state

    String getDescription() {
      state == ServiceStatus.OutOfService ?
          "Instance has failed at least the Unhealthy Threshold number of health checks consecutively." :
          "Healthy"
    }

    enum ServiceStatus {
      InService,
      OutOfService
    }
  }

  @JsonIgnore
  View getView() {
    new View(this)
  }

  class View extends GoogleHealth implements Health {
    final Type type = Type.LoadBalancer
    final HealthClass healthClass = null

    List<LBHealthSummary> loadBalancers

    View(GoogleLoadBalancerHealth googleLoadBalancerHealth){
      loadBalancers = googleLoadBalancerHealth.lbHealthSummaries
    }

    HealthState getState() {
      GoogleLoadBalancerHealth.this.status?.toHeathState()
    }
  }
}
