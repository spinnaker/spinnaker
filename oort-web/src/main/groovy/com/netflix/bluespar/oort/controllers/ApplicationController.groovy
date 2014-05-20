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

import com.netflix.bluespar.oort.clusters.Cluster
import com.netflix.bluespar.oort.deployables.Application
import com.netflix.bluespar.oort.deployables.ApplicationProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/applications")
class ApplicationController {

  @Autowired
  List<ApplicationProvider> deployableProviders

  @RequestMapping(method = RequestMethod.GET)
  def list() {
    Map<String, Application> deployables = [:]
    deployableProviders.each {
      it.list().each { Application deployable ->
        if (deployables.containsKey(deployable.name)) {
          def existing = deployables[deployable.name]
          def merged = Application.merge(existing, deployable)
          deployables[deployable.name] = merged
        } else {
          deployables[deployable.name] = deployable
        }
      }
    }
    deployables.inject(new HashMap()) { Map map, String name, Application deployable ->
      if (!map.containsKey(name)) {
        map[name] = [clusterCount: 0, instanceCount: 0, serverGroupCount: 0, attributes: deployable.attributes]
      }
      deployable.clusters.list().each { Cluster cluster ->
        map[name].clusters = (map[name].clusters ?: new HashSet()) << cluster.name
        map[name].clusterCount += 1
        map[name].serverGroupCount += cluster.serverGroups?.size()
        map[name].instanceCount += cluster.serverGroups?.collect { it.getInstanceCount() }?.sum() ?: 0
      }
      map
    }
  }

  @RequestMapping(value = "/{name}")
  def get(@PathVariable("name") String name) {
    def deployables = deployableProviders.collect { it.get(name) }.findAll { it }
    Application deployable = deployables.inject(new HashMap()) { Map map, Application deployable ->
      if (map.containsKey(deployable.name)) {
        def existing = map[deployable.name]
        def merged = Application.merge(existing, deployable)
        map[deployable.name] = merged
      } else {
        map[deployable.name] = deployable
      }
      map
    }?.getAt(name)

    if (!deployable) {
      return null
    }

    def clusters = deployable.clusters.list()
    def serverGroupCount = clusters.collect { it.serverGroups.size() }?.sum()
    def instanceCount = clusters.inject(0) { int c, Cluster cluster -> c += (cluster.serverGroups?.collect { it.getInstanceCount() }?.sum() ?: 0); c }

    [clusterCount: clusters.size(), instanceCount: instanceCount, serverGroupCount: serverGroupCount, attributes: deployable.attributes]
  }
}
