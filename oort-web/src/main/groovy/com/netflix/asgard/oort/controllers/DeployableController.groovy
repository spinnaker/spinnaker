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

import com.netflix.asgard.oort.clusters.Cluster
import com.netflix.asgard.oort.deployables.Deployable
import com.netflix.asgard.oort.deployables.DeployableProvider
import com.netflix.asgard.oort.remoting.RemoteResource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import rx.schedulers.Schedulers

@RestController
@RequestMapping("/deployables")
class DeployableController {

  @Autowired
  RemoteResource bakery

  @Autowired
  List<DeployableProvider> deployableProviders

  @RequestMapping(method = RequestMethod.GET)
  def list() {
    Map<String, Deployable> deployables = [:]
    deployableProviders.each {
      it.list().each { Deployable deployable ->
        if (deployables.containsKey(deployable.name)) {
          def existing = deployables[deployable.name]
          def merged = Deployable.merge(existing, deployable)
          deployables[deployable.name] = merged
        } else {
          deployables[deployable.name] = deployable
        }
      }
    }
    deployables.inject(new HashMap()) { Map map, String name, Deployable deployable ->
      if (!map.containsKey(name)) {
        map[name] = [clusterCount: 0, instanceCount: 0, serverGroupCount: 0, attributes: deployable.attributes]
      }
      deployable.clusters.list().each { Cluster cluster ->
        map[name].clusterCount += 1
        map[name].serverGroupCount += cluster.serverGroups?.size()
        map[name].instanceCount += cluster.serverGroups?.collect { it.getInstanceCount() }?.sum() ?: 0
      }
      map
    }
  }

  @RequestMapping(value = "/{name}")
  def get(@PathVariable("name") String name) {
    def deployables = deployableProviders.collect { it.get(name) }
    Deployable deployable = deployables.inject(new HashMap()) { Map map, Deployable deployable ->
      if (map.containsKey(deployable.name)) {
        def existing = map[deployable.name]
        def merged = Deployable.merge(existing, deployable)
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

  @RequestMapping(value = "/{name}/images")
  def getImages(@PathVariable("name") String name) {
    rx.Observable.from(["us-east-1", "us-west-1", "us-west-2", "eu-west-1"]).flatMap {
      rx.Observable.from(it).observeOn(Schedulers.io()).map { String region ->
        def list = bakery.query("/api/v1/${region}/bake/;package=${name};store_type=ebs;region=${region};vm_type=pv;base_os=ubuntu;base_label=release")
        list.findAll { it.ami } collect {
          def version = it.ami_name?.split('-')?.getAt(1..2)?.join('.')
          [name: it.ami, region: region, version: version]
        }
      }
    }.reduce([], { objs, obj ->
      if (obj) {
        objs << obj
      }
      objs
    }).toBlockingObservable().first()?.flatten()
  }



}
