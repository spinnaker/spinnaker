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

package com.netflix.bluespar.oort.controllers

import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.netflix.bluespar.amazon.security.AmazonClientProvider
import com.netflix.bluespar.amazon.security.AmazonCredentials
import com.netflix.bluespar.oort.clusters.ClusterProvider
import com.netflix.bluespar.oort.deployables.Application
import com.netflix.bluespar.oort.deployables.ApplicationProvider
import com.netflix.bluespar.oort.remoting.AggregateRemoteResource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.*

import javax.servlet.http.HttpServletResponse

@RestController
@RequestMapping("/applications/{application}/clusters")
class ClusterController {

  @Value('${discovery.url.format:#{null}}')
  String discoveryUrlFormat

  @Autowired
  AggregateRemoteResource edda

  @Autowired
  List<ApplicationProvider> deployableProviders

  @Autowired
  List<ClusterProvider> clusterProviders

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  AmazonCredentials amazonCredentials

  @RequestMapping(method = RequestMethod.GET)
  def list(@PathVariable("application") String application) {
    Map<String, Application> applications = [:]
    deployableProviders.each {
      def deployableObject = it.get(application)
      if (!deployableObject) return

      if (applications.containsKey(deployableObject.name)) {
        def existing = applications[deployableObject.name]
        applications[deployableObject.name] = Application.merge(existing, deployableObject)
      } else {
        applications[deployableObject.name] = deployableObject
      }
    }
    def clusters = applications.values()?.getAt(0)?.clusters?.list()
    clusters.each {
      it.serverGroups.each { serverGroup ->
        serverGroup.instances = serverGroup.instances.collect { getInstance(serverGroup.region, it.instanceId) + [eureka: getDiscoveryHealth(serverGroup.region, application, it.instanceId)]}
          if (serverGroup.asg && serverGroup.asg.suspendedProcesses?.collect { it.processName }?.containsAll(["Terminate", "Launch"])) {
            serverGroup.status = "DISABLED"
        } else {
          serverGroup.status = "ENABLED"
        }
      }
    }
  }

  @RequestMapping(value = "/{cluster}", method = RequestMethod.GET)
  def get(@PathVariable("application") String application, @PathVariable("cluster") String clusterName,
          @RequestParam(value = "zone", required = false) String zoneName) {
    clusterProviders.collect {
      def clusters = zoneName ? it.getByNameAndZone(application, clusterName, zoneName) : it.getByName(application, clusterName)
      clusters.each {
        it.serverGroups.each { serverGroup ->
          serverGroup.instances = serverGroup.instances.collect { getInstance(serverGroup.region, it.instanceId) + [eureka: getDiscoveryHealth(serverGroup.region, application, it.instanceId)] }
            if (serverGroup.asg && serverGroup.asg.suspendedProcesses?.collect { it.processName }?.containsAll(["Terminate", "Launch"])) {
              serverGroup.status = "DISABLED"
            }
           else {
            serverGroup.status = "ENABLED"
          }
        }
      }
    }?.flatten()
  }

  @RequestMapping(value = "/{cluster}/serverGroups/{serverGroup}", method = RequestMethod.GET)
  def getAsgs(@PathVariable("application") String application, @PathVariable("cluster") String clusterName,
             @PathVariable("serverGroup") String serverGroupName) {
    def serverGroups = []
    for (provider in clusterProviders) {
      def clusters = provider.getByName(application, clusterName)
      for (cluster in clusters) {
        def serverGroup = cluster.serverGroups.find { it.name == serverGroupName }
        if (serverGroup) {
          def copied = new HashMap(serverGroup)
          copied.instances = copied.instances.collect { getInstance(cluster.zone, it.instanceId) + [eureka: getDiscoveryHealth(cluster.zone, application, it.instanceId)] }
            if (copied.asg && copied.asg.suspendedProcesses?.collect { it.processName }?.containsAll(["Terminate", "Launch"])) {
              copied.status = "DISABLED"
          } else {
            copied.status = "ENABLED"
          }
          serverGroups << copied
        }
      }
    }
    serverGroups
  }

  @RequestMapping(value = "/{cluster}/serverGroups/{serverGroup}/{zone}", method = RequestMethod.GET)
  def getAsg(@PathVariable("application") String application, @PathVariable("cluster") String clusterName,
             @PathVariable("serverGroup") String serverGroupName, @PathVariable("zone") String zoneName, HttpServletResponse response) {
    def serverGroup
    for (provider in clusterProviders) {
      def clusters = provider.getByNameAndZone(application, clusterName, zoneName)
      serverGroup = clusters.serverGroups?.flatten()?.find { it.name == serverGroupName }
      if (serverGroup) {
        def copied = new HashMap(serverGroup)
        def instances = copied.instances.collect { getInstance(zoneName, it.instanceId) + [eureka: getDiscoveryHealth(zoneName, application, it.instanceId)]}
        copied.instances = instances
          if (copied.asg && copied.asg.suspendedProcesses?.collect { it.processName }?.containsAll(["Terminate", "Launch"])) {
            copied.status = "DISABLED"
        } else {
          copied.status = "ENABLED"
        }
        return copied
      }
    }
    response.sendError 404
  }

  def getInstance(String region, String instanceId) {
    try {
      def client = amazonClientProvider.getAmazonEC2(amazonCredentials, region)
      def request = new DescribeInstancesRequest().withInstanceIds(instanceId)
      def result = client.describeInstances(request)
      new HashMap(result.reservations?.instances?.getAt(0)?.getAt(0)?.properties)
    } catch (IGNORE) { [instanceId: instanceId, state: [name: "offline"]] }
  }

  def getDiscoveryHealth(String region, String application, String instanceId) {
    if (!discoveryUrlFormat) return
    try {
      def map = edda.getRemoteResource(region) get "/REST/v2/discovery/applications/$application;_pp:(instances:(id,status,overriddenStatus))"
      def instance = map.instances.find { it.id == instanceId }
      if (instance) {
        return [id: instanceId, status: instance.overriddenStatus?.name != "UNKNOWN" ? instance.overriddenStatus.name : instance.status.name]
      }
    } catch (IGNORE) { [instanceId: instanceId, state: [name: "unknown"]] }
  }
}
