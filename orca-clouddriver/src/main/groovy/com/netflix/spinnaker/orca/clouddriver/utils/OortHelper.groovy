/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.utils

import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.model.Cluster
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Helper methods for filtering Cluster/ASG/Instance information from Oort
 */
@Component
class OortHelper {

  private final CloudDriverService cloudDriverService

  @Autowired
  OortHelper(CloudDriverService cloudDriverService) {
    this.cloudDriverService = cloudDriverService
  }

  // TODO: failIfAnyInstancesUnhealthy seems to only be false in tasks that call this
  Map<String, Object> getInstancesForCluster(Map<String, Object> context, String expectedAsgName, boolean expectOneAsg, boolean failIfAnyInstancesUnhealthy) {
    // infer the app from the cluster prefix since this is used by quip and we want to be able to quick patch different apps from the same pipeline
    String app
    String clusterName
    if (expectedAsgName) {
      app = expectedAsgName.substring(0, expectedAsgName.indexOf("-"))
      clusterName = expectedAsgName.substring(0, expectedAsgName.lastIndexOf("-"))
    } else if (context.clusterName?.indexOf("-") > 0) {
      app = context.clusterName.substring(0, context.clusterName.indexOf("-"))
      clusterName = context.clusterName
    } else {
      app = context.clusterName
      clusterName = context.clusterName
    }

    String account = context.account
    String cloudProvider = context.cloudProvider ?: context.providerType ?: "aws"

    Cluster oortCluster = cloudDriverService.getCluster(app, account, clusterName, cloudProvider)

    if (!oortCluster || !oortCluster.serverGroups) {
      throw new RuntimeException("unable to find any server groups")
    }

    String region = context.region ?: context.source.region

    if (!region) {
      throw new RuntimeException("unable to determine region")
    }

    List<ServerGroup> asgsForCluster = oortCluster.serverGroups.findAll {
      it.region == region
    }

    def searchAsg
    if (expectOneAsg) {
      if (asgsForCluster.size() != 1) {
        throw new RuntimeException("there is more than one server group in the cluster : ${clusterName}:${region}")
      }
      searchAsg = asgsForCluster.get(0)
    } else if (expectedAsgName) {
      searchAsg = asgsForCluster.findResult {
        if (it.name == expectedAsgName) {
          return it
        }
      }
      if (!searchAsg) {
        throw new RuntimeException("did not find the expected asg name : ${expectedAsgName}")
      }
    }

    Map<String, Object> instanceMap = [:]
    searchAsg.instances.each { instance ->
      String hostName = instance.publicDnsName
      if (!hostName || hostName.isEmpty()) { // some instances dont have a public address, fall back to the private ip
        hostName = instance.privateIpAddress
      }

      String healthCheckUrl = null
      instance.health.each { health ->
        if (health.healthCheckUrl != null && !health.healthCheckUrl.isEmpty()) {
          healthCheckUrl = health.healthCheckUrl
        }
      }

      def status = instance.health.findResult { healthItem ->
        healthItem.status // TODO: should this be state?
      }

      if (failIfAnyInstancesUnhealthy && (!healthCheckUrl || !status || status != "UP")) {
        throw new RuntimeException("at least one instance is DOWN or in the STARTING state, exiting")
      }

      Map<String, Object> instanceInfo = [
          hostName: hostName,
          healthCheckUrl: healthCheckUrl,
          privateIpAddress: instance.privateIpAddress
      ]
      instanceMap.put(instance.instanceId, instanceInfo)
    }

    return instanceMap
  }
}
