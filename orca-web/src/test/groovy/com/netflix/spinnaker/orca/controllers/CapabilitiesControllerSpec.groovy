/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.controllers

import com.netflix.spinnaker.config.DeploymentMonitorDefinition
import com.netflix.spinnaker.config.MonitoredDeployConfigurationProperties
import spock.lang.Specification

class CapabilitiesControllerSpec extends Specification {
  void '/capabilities/deploymentMonitors returns a empty list when no monitors registered or disabled'() {
    given:
    CapabilitiesController controller = new CapabilitiesController(Optional.empty())

    expect:
    controller.getDeploymentMonitors() == []
  }

  void '/capabilities/deploymentMonitors returns valid data'() {
    given:
    MonitoredDeployConfigurationProperties configProperties = new MonitoredDeployConfigurationProperties()
    configProperties.deploymentMonitors = new ArrayList<>()
    (0..2).forEach({
      def dmdef = new DeploymentMonitorDefinition()
      dmdef.setName("name${it}")
      dmdef.setId("id${it}")
      configProperties.deploymentMonitors.add(dmdef)
    })

    CapabilitiesController controller = new CapabilitiesController(Optional.of(configProperties))

    when:
    def result = controller.getDeploymentMonitors()

    then:
    result.size() == 3
    result[0].id == "id0"
    result[0].name == "name0"
    result[1].id == "id1"
    result[1].name == "name1"
  }
}
