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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.discovery

import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.InstanceStateName
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.eureka.api.Eureka
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.AbstractEurekaSupport
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class AwsEurekaSupport extends AbstractEurekaSupport {

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  Eureka getEureka(def credentials, String region) {
    if (!credentials.discoveryEnabled) {
      throw new AbstractEurekaSupport.DiscoveryNotConfiguredException()
    }

    def regionScopedProvider = regionScopedProviderFactory.forRegion(credentials, region)
    def eureka = regionScopedProvider.eureka
    return eureka
  }

  boolean verifyInstanceAndAsgExist(def credentials,
                                    String region,
                                    String instanceId,
                                    String asgName) {
    def regionScopedProvider = regionScopedProviderFactory.forRegion(credentials, region)
    if (asgName) {
      def asgService = regionScopedProvider.asgService
      def autoScalingGroup = asgService.getAutoScalingGroup(asgName)
      if (!autoScalingGroup || autoScalingGroup.status) {
        // ASG does not exist or is in the process of being deleted
        return false
      }
      log.info("AutoScalingGroup (${asgName}) exists")

      if (!autoScalingGroup.instances.find { it.instanceId == instanceId }) {
        return false
      }
      log.info("AutoScalingGroup (${asgName}) contains instance (${instanceId})")

      if (autoScalingGroup.desiredCapacity == 0) {
        return false
      }
      log.info("AutoScalingGroup (${asgName}) has non-zero desired capacity (desiredCapacity: ${autoScalingGroup.desiredCapacity})")
    }

    if (instanceId) {
      def amazonEC2 = regionScopedProvider.getAmazonEC2()
      def instances = amazonEC2.describeInstances(
        new DescribeInstancesRequest().withInstanceIds(instanceId)
      ).reservations*.instances.flatten()

      Instance instance = instances.find { it.instanceId == instanceId }
      if (!instance) {
        return false
      }

      def isRunning = [
        InstanceStateName.Running.toString(),
        InstanceStateName.Pending.toString()
      ].contains(instance.getState().getName())

      if (!isRunning) {
        log.info("Instance exists but is not running (instanceId: ${instanceId}) state: ${instance.getState().getName()})")
        return false
      }

      log.info("Instance exists (instanceId: ${instanceId})")
    }

    return true
  }
}
