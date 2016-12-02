/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.appengine.v1.model.Service
import com.netflix.spinnaker.clouddriver.appengine.AppEngineCloudProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

@CompileStatic
@EqualsAndHashCode(includes = ["name", "account"])
class AppEngineLoadBalancer implements LoadBalancer, Serializable {
  String name
  String region
  final String type = AppEngineCloudProvider.ID
  final String cloudProvider = AppEngineCloudProvider.ID
  String account
  Set<LoadBalancerServerGroup> serverGroups = new HashSet<>()
  TrafficSplit split

  AppEngineLoadBalancer() { }

  AppEngineLoadBalancer(Service service, String account, String region) {
    this.name = service.getId()
    this.account = account
    this.region = region;
    this.split = new ObjectMapper().convertValue(service.getSplit(), TrafficSplit)
  }

  Void setLoadBalancerServerGroups(Set<AppEngineServerGroup> serverGroups) {
    this.serverGroups = serverGroups?.collect { serverGroup ->
      def instances = serverGroup.isDisabled() ? [] : serverGroup.instances?.collect { instance ->
          new LoadBalancerInstance(id: instance.name, health: [state: instance.healthState.toString()])
        } ?: []

      def detachedInstances = serverGroup.isDisabled() ? serverGroup.instances?.collect { it.name } ?: [] : []

      new LoadBalancerServerGroup(
        name: serverGroup.name,
        region: serverGroup.region,
        isDisabled: serverGroup.isDisabled(),
        instances: instances as Set,
        detachedInstances: detachedInstances as Set
      )
    } as Set
    null
  }
}

class TrafficSplit {
  Map<String, Double> allocations
  ShardBy shardBy
}

enum ShardBy {
  /*
  * See https://cloud.google.com/appengine/docs/admin-api/reference/rest/v1/apps.services#ShardBy
  * */
  UNSPECIFIED,
  COOKIE,
  IP,
}
