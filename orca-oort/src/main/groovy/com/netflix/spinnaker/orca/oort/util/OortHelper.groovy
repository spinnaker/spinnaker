package com.netflix.spinnaker.orca.oort.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.oort.OortService
import org.springframework.beans.factory.annotation.Autowired

/**
 * Helper methods for filtering Cluster/ASG/Instance information from Oort
 */
class OortHelper {
  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  Map getInstancesForCluster(Map context, String expectedAsgName = null, boolean expectOneAsg = false, boolean failIfAnyInstancesUnhealthy = false) {
    // infer the app from the cluster prefix since this is used by quip and we want to be able to quick patch different apps from the same pipeline
    def app
    if(context.clusterName.indexOf("-") > 0) {
      app = context.clusterName.substring(0, context.clusterName.indexOf("-"))
    } else {
      app = context.clusterName
    }

    def response = oortService.getCluster(app, context.account, context.clusterName, context.providerType ?: "aws")
    def oortCluster = objectMapper.readValue(response.body.in().text, Map)
    def instanceMap = [:]

    if (!oortCluster || !oortCluster.serverGroups) {
      throw new RuntimeException("unable to find any server groups")
    }

    def asgsForCluster = oortCluster.serverGroups.findAll {
      it.region == context.region
    }

    def searchAsg
    if (expectOneAsg) {
      if(asgsForCluster.size() != 1) {
        throw new RuntimeException("there more than one server group in the cluster")
      }
      searchAsg = asgsForCluster.get(0)
    } else if(expectedAsgName) {
      searchAsg = asgsForCluster.findResult {it.name == expectedAsgName}
      if(!searchAsg) {
        throw new RuntimeException("did not find the expected asg name : ${expectedAsgName}")
      }
    }

    searchAsg.instances.each { instance ->
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

      if(failIfAnyInstancesUnhealthy && (index == -1 || instance.health.get(index).status == "STARTING")) {
        throw new RuntimeException("at least one instance is down or in the STARTING state, exiting")
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
