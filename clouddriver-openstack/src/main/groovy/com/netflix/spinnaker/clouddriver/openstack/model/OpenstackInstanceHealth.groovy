/*
 * Copyright 2016 Target Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.model.Health
import com.netflix.spinnaker.clouddriver.model.HealthState

import static org.openstack4j.model.compute.Server.Status

class OpenstackInstanceHealth {
  Status status

  HealthState toHealthState() {
    switch (status) {
      case Status.ACTIVE:
        HealthState.Unknown
        break
      case Status.BUILD:
        HealthState.Starting
        break
      case Status.ERROR:
        HealthState.Failed
        break
      case Status.UNKNOWN:
        HealthState.Unknown
        break
      default:
        HealthState.Unknown
    }
  }

  @JsonIgnore
  View getView() {
    new View()
  }

  class View extends OpenstackHealth implements Health {

    final OpenstackHealthType type = OpenstackHealthType.Openstack
    final HealthClass healthClass = HealthClass.platform

    HealthState getState() {
      OpenstackInstanceHealth.this.toHealthState()
    }
  }
}
