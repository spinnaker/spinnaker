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

package com.netflix.asgard.oort.controllers

import com.netflix.asgard.oort.clusters.ClusterProvider
import com.netflix.asgard.oort.deployables.Deployable
import com.netflix.asgard.oort.deployables.DeployableProvider
import com.netflix.asgard.oort.remoting.AggregateRemoteResource
import org.springframework.beans.factory.annotation.Value

import javax.servlet.http.HttpServletResponse

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/applications/{deployable}/clusters")
class ClusterController {

  @Value('${discovery.url.format}')
  String discoveryUrlFormat

  @Autowired
  AggregateRemoteResource edda

  @Autowired
  List<DeployableProvider> deployableProviders

  @Autowired
  List<ClusterProvider> clusterProviders

  @RequestMapping(method = RequestMethod.GET)
  def list(@PathVariable("deployable") String deployable) {
    Map<String, Deployable> deployables = [:]
    deployableProviders.each {
      def deployableObject = it.get(deployable)
      if (!deployableObject) return

      if (deployables.containsKey(deployableObject.name)) {
        def existing = deployables[deployableObject.name]
        deployables[deployableObject.name] = Deployable.merge(existing, deployableObject)
      } else {
        deployables[deployableObject.name] = deployableObject
      }
    }
    def clusters = deployables.values()?.getAt(0)?.clusters?.list()
    clusters.each {
      it.serverGroups.each { serverGroup ->
        serverGroup.instances = serverGroup.instances.collect { getInstance(serverGroup.region, it.instanceId) + [eureka: getDiscoveryHealth(serverGroup.region, deployable, it.instanceId)]}
      }
    }
  }

  @RequestMapping(value = "/{cluster}", method = RequestMethod.GET)
  def get(@PathVariable("deployable") String deployable, @PathVariable("cluster") String clusterName,
          @RequestParam(value = "zone", required = false) String zoneName) {
    clusterProviders.collect {
      def clusters = zoneName ? it.getByNameAndZone(deployable, clusterName, zoneName) : it.getByName(deployable, clusterName)
      clusters.each {
        it.serverGroups.each { serverGroup ->
          serverGroup.instances = serverGroup.instances.collect { getInstance serverGroup.region, it.instanceId }
        }
      }
    }?.flatten()
  }

  @RequestMapping(value = "/{cluster}/serverGroups/{serverGroup}", method = RequestMethod.GET)
  def getAsgs(@PathVariable("deployable") String deployable, @PathVariable("cluster") String clusterName,
             @PathVariable("serverGroup") String serverGroupName, HttpServletResponse response) {
    def serverGroups = []
    for (provider in clusterProviders) {
      def clusters = provider.getByName(deployable, clusterName)
      for (cluster in clusters) {
        def serverGroup = cluster.serverGroups.find { it.name == serverGroupName }
        if (serverGroup) {
          def copied = new HashMap(serverGroup)
          copied.instances = copied.instances.collect { getInstance cluster.zone, it.instanceId }
          serverGroups << copied
        }
      }
    }
    serverGroups
  }

  @RequestMapping(value = "/{cluster}/serverGroups/{serverGroup}/{zone}", method = RequestMethod.GET)
  def getAsg(@PathVariable("deployable") String deployable, @PathVariable("cluster") String clusterName,
             @PathVariable("serverGroup") String serverGroupName, @PathVariable("zone") String zoneName, HttpServletResponse response) {
    def serverGroup
    for (provider in clusterProviders) {
      def clusters = provider.getByNameAndZone(deployable, clusterName, zoneName)
      serverGroup = clusters.serverGroups?.flatten()?.find { it.name == serverGroupName }
      if (serverGroup) {
        def copied = new HashMap(serverGroup)
        def instances = copied.instances.collect { getInstance zoneName, it.instanceId }
        copied.instances = instances
        return copied
      }
    }
    response.sendError 404
  }

  def getInstance(String region, String instanceId) {
    try {
      edda.getRemoteResource(region) get "/REST/v2/view/instances/$instanceId"
    } catch (IGNORE) { [instanceId: instanceId, state: [name: "offline"]] }
  }

  def getDiscoveryHealth(String region, String application, String instanceId) {
    try {
      def map = edda.getRemoteResource(region) get "/REST/v2/discovery/applications/$application;_pp:(instances:(id,status,overriddenStatus))"
      def instance = map.instances.find { it.id == instanceId }
      if (instance) {
        return [id: instanceId, status: instance.overriddenStatus?.name != "UNKNOWN" ? instance.overriddenStatus.name : instance.status.name]
      }
    } catch (IGNORE) { [instanceId: instanceId, state: [name: "unknown"]] }
  }
}
