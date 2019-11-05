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

package com.netflix.spinnaker.orca.deploymentmonitor

import com.netflix.spinnaker.config.DeploymentMonitorDefinition
import com.netflix.spinnaker.config.MonitoredDeployConfigurationProperties
import spock.lang.Specification

class DeploymentMonitorCapabilitiesSpec extends Specification {
  void 'returns an empty list when no monitors registered or disabled'() {
    when: 'monitored deploy is not enabled'
    DeploymentMonitorCapabilities capabilities = new DeploymentMonitorCapabilities(Optional.empty())

    then: 'no monitors are returned'
    noExceptionThrown()
    capabilities.getDeploymentMonitors() == []

    when: 'monitored deploy is enabled but no monitors are registeres'
    capabilities = new DeploymentMonitorCapabilities(Optional.of(new MonitoredDeployConfigurationProperties()))

    then: 'no monitors are returned'
    noExceptionThrown()
    capabilities.getDeploymentMonitors() == []
  }

  void 'returns valid data'() {
    given:
    MonitoredDeployConfigurationProperties configProperties = new MonitoredDeployConfigurationProperties()
    configProperties.deploymentMonitors = new ArrayList<>()
    (0..2).forEach({
      def dmdef = new DeploymentMonitorDefinition()
      dmdef.setName("name${it}")
      dmdef.setId("id${it}")
      dmdef.setStable((it % 2) == 0)
      configProperties.deploymentMonitors.add(dmdef)
    })

    DeploymentMonitorCapabilities capabilities = new DeploymentMonitorCapabilities(Optional.of(configProperties))

    when:
    def result = capabilities.getDeploymentMonitors()

    then:
    result.size() == 3
    result[0].id == "id0"
    result[0].name == "name0"
    result[0].stable == true
    result[1].id == "id1"
    result[1].name == "name1"
    result[1].stable == false
  }
}
