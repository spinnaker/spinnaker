/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.model.Health
import com.netflix.spinnaker.clouddriver.model.HealthState
import groovy.transform.Canonical

@Canonical
class OpenstackLoadBalancerHealth {
  String instanceId

  List<LBHealthSummary> lbHealthSummaries
  PlatformStatus status

  enum PlatformStatus {
    ONLINE,
    DISABLED

    HealthState toHealthState() {
      this == ONLINE ? HealthState.Up : HealthState.Down
    }

    LBHealthSummary.ServiceStatus toServiceStatus() {
      this == ONLINE ? LBHealthSummary.ServiceStatus.InService : LBHealthSummary.ServiceStatus.OutOfService
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

    /**
     * This seems to be needed by deck
     * @return
     */
    ServiceStatus getHealthState() {
      state
    }

    enum ServiceStatus {
      InService,
      OutOfService
    }
  }

  @JsonIgnore
  View getView() {
    new View()
  }

  class View extends OpenstackHealth implements Health {
    final OpenstackHealthType type = OpenstackHealthType.LoadBalancer
    final HealthClass healthClass = null

    List<LBHealthSummary> loadBalancers = OpenstackLoadBalancerHealth.this.lbHealthSummaries

    HealthState getState() {
      OpenstackLoadBalancerHealth.this.status?.toHealthState()
    }
  }
}
