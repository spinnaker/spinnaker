/*
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spinnaker.kato.services

import com.netflix.frigga.Names
import com.netflix.spinnaker.kato.data.task.Task
import groovy.transform.Canonical
import org.springframework.web.client.RestTemplate

@Canonical
class EurekaService {

  final ThrottleService throttleService
  final String discoveryHostFormat
  final RestTemplate restTemplate
  final Task task
  final String phase
  final String environment
  final String region

  private final enum EurekaStatus {
    Enable('UP'),
    Disable('OUT_OF_SERVICE')

    String value

    EurekaStatus(String value) {
      this.value = value
    }
  }

  void enableInstancesForAsg(String asgName, Collection<String> instanceIds) {
    changeStatusOfAsgInstances(EurekaStatus.Enable, asgName, instanceIds)
  }

  void disableInstancesForAsg(String asgName, Collection<String> instanceIds) {
    changeStatusOfAsgInstances(EurekaStatus.Disable, asgName, instanceIds)
  }

  private void changeStatusOfAsgInstances(EurekaStatus eurekaStatus, String asgName, Collection<String> instanceIds = []) {
    if (discoveryHostFormat) {
      def names = Names.parseName(asgName)
      if (!names.app) {
        task.updateStatus phase, "Could not derive application name from ASG name and unable to ${eurekaStatus.name().toLowerCase()} in Eureka!"
      } else {
        instanceIds.eachWithIndex{ instanceId, index ->
          if (index > 0) {
            throttleService.sleepMillis(150)
          }
          task.updateStatus phase, "Attempting to ${eurekaStatus.name().toLowerCase()} instance '$instanceId'."
          def discovery = String.format(discoveryHostFormat, region, environment)
          restTemplate.put("$discovery/eureka/v2/apps/$names.app/$instanceId/status?value=$eurekaStatus.value", [:])
        }
      }
    }
  }

}
