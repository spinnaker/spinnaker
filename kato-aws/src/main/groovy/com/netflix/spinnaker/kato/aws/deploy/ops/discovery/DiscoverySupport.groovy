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


package com.netflix.spinnaker.kato.aws.deploy.ops.discovery

import com.netflix.frigga.Names
import com.netflix.spinnaker.kato.aws.deploy.description.AbstractAmazonCredentialsDescription
import com.netflix.spinnaker.kato.data.task.Task
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class DiscoverySupport {
  private static final long THROTTLE_MS = 150

  @Autowired
  RestTemplate restTemplate

  void updateDiscoveryStatusForInstances(AbstractAmazonCredentialsDescription description,
                                         Task task,
                                         String phaseName,
                                         String region,
                                         DiscoveryStatus discoveryStatus,
                                         String asgName,
                                         List<String> instanceIds) {
    if (!description.credentials.discoveryEnabled) {
      throw new DiscoveryNotConfiguredException()
    }
    def names = Names.parseName(asgName)
    if (!names?.app) {
      task.updateStatus phaseName, "Could not derive application name from ASG name and unable to ${discoveryStatus.name().toLowerCase()} in Eureka!"
      task.fail()
    } else {
      instanceIds.eachWithIndex { instanceId, index ->
        if (index > 0) {
          sleep THROTTLE_MS
        }
        task.updateStatus phaseName, "Attempting to mark ${instanceId} as '${discoveryStatus.value}' in discovery."
        def discovery = String.format(description.credentials.discovery, region)
        restTemplate.put("${discovery}/v2/apps/${names.app}/${instanceId}/status?value=${discoveryStatus.value}", [:])
        task.updateStatus phaseName, "Marked ${instanceId} as '${discoveryStatus.value}' in discovery."
      }
    }
  }

  enum DiscoveryStatus {
    Enable('UP'),
    Disable('OUT_OF_SERVICE')

    String value

    DiscoveryStatus(String value) {
      this.value = value
    }
  }

  @InheritConstructors
  static class DiscoveryNotConfiguredException extends RuntimeException {}
}
