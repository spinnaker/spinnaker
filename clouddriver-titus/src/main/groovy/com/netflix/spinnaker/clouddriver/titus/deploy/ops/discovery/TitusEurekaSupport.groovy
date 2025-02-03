/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.deploy.ops.discovery

import com.netflix.spinnaker.clouddriver.eureka.api.Eureka
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.AbstractEurekaSupport
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.EurekaUtil
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.model.TitusInstance
import com.netflix.spinnaker.clouddriver.titus.model.TitusServerGroup
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class TitusEurekaSupport extends AbstractEurekaSupport {

  @Autowired
  TitusClientProvider titusClientProvider

  @Autowired
  ServiceClientProvider serviceClientProvider

  Eureka getEureka(def credentials, String region) {
    if (!credentials.discoveryEnabled) {
      throw new AbstractEurekaSupport.DiscoveryNotConfiguredException()
    }
    EurekaUtil.getWritableEureka(credentials.discovery, region, serviceClientProvider)
  }

  boolean verifyInstanceAndAsgExist(def credentials,
                                    String region,
                                    String instanceId,
                                    String asgName) {

    if (asgName) {
      def titusClient = titusClientProvider.getTitusClient(credentials, region)
      def asg = new TitusServerGroup(titusClient.findJobByName(asgName,true), credentials.name, region)
      if (!asg || asg.isDisabled()) {
        // ASG does not exist or is in the process of being deleted
        return false
      }
      log.info("AutoScalingGroup (${asgName}) exists")
      if (!asg.instances.find { it.name == instanceId }) {
        return false
      }
      log.info("AutoScalingGroup (${asgName}) contains instance (${instanceId})")
      if (asg.capacity.desired == 0) {
        return false
      }
      log.info("AutoScalingGroup (${asgName}) has non-zero desired capacity (desiredCapacity: ${autoScalingGroup.desiredCapacity})")
    }
    if (instanceId) {
      def titusClient = titusClientProvider.getTitusClient(credentials, region)
      def instance = new TitusInstance(titusClient.getTask(instanceId))
      if (!instance) {
        return false
      }
      log.info("Instance (${instanceId}) exists")
    }
    return true
  }
}
