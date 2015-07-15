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

package com.netflix.spinnaker.orca.oort.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.oort.OortService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Helper methods for filtering Cluster/ASG/Instance information from Oort
 */
@Component
class OortHelper {
  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  Map getInstancesForCluster(Map context, String expectedAsgName = null, boolean expectOneAsg = false, boolean failIfAnyInstancesUnhealthy = false) {
    // infer the app from the cluster prefix since this is used by quip and we want to be able to quick patch different apps from the same pipeline
    def app
    def clusterName
    if(expectedAsgName) {
      app = expectedAsgName.substring(0, expectedAsgName.indexOf("-"))
      clusterName = expectedAsgName.substring(0, expectedAsgName.lastIndexOf("-"))
    } else if(context?.clusterName.indexOf("-") > 0) {
      app = context.clusterName.substring(0, context.clusterName.indexOf("-"))
      clusterName = context.clusterName
    } else {
      app = context.clusterName
      clusterName = context.clusterName
    }

    def response = oortService.getCluster(app, context.account, clusterName, context.providerType ?: "aws")
    def oortCluster = objectMapper.readValue(response.body.in().text, Map)
    def instanceMap = [:]

    if (!oortCluster || !oortCluster.serverGroups) {
      throw new RuntimeException("unable to find any server groups")
    }

    def region = context.region ?: context.source.region

    if(!region) {
      throw new RuntimeException("unable to determine region")
    }

    def asgsForCluster = oortCluster.serverGroups.findAll {
      it.region == region
    }

    def searchAsg
    if (expectOneAsg) {
      if(asgsForCluster.size() != 1) {
        throw new RuntimeException("there is more than one server group in the cluster : ${clusterName}:${region}")
      }
      searchAsg = asgsForCluster.get(0)
    } else if(expectedAsgName) {
      searchAsg = asgsForCluster.findResult {
        if(it.name == expectedAsgName) {
        return it
        }
      }
      if(!searchAsg) {
        throw new RuntimeException("did not find the expected asg name : ${expectedAsgName}")
      }
    }

    searchAsg.instances.each { instance ->

      println "instance: ${instance.dump()}"

      String hostName = instance.publicDnsName
      if(!hostName || hostName.isEmpty()) { // some instances dont have a public address, fall back to the private ip
        hostName = instance.privateIpAddress
      }

      int index = -1
      instance.health.eachWithIndex { health, idx ->
        if (health.healthCheckUrl != null && !health.healthCheckUrl.isEmpty()) {
          index = idx
        }
      }

      def status = instance.health.find { healthItem ->
        healthItem.find {
          key, value ->
            key == "status"
        }
      }.status

      if(failIfAnyInstancesUnhealthy && (index == -1 || !status || status != "UP")) {
        throw new RuntimeException("at least one instance is DOWN or in the STARTING state, exiting")
      }

      String healthCheckUrl = instance.health.get(index).healthCheckUrl
      Map instanceInfo = [hostName : hostName, healthCheckUrl : healthCheckUrl]
      instanceMap.put(instance.instanceId, instanceInfo)
    }

    if(instanceMap.size() == 0) {
      throw new RuntimeException("could not find any instances")
    }
    return instanceMap
  }
}
