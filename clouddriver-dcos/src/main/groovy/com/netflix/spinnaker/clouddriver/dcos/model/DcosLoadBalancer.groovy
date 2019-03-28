/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.UpsertDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerLbId
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import groovy.transform.EqualsAndHashCode
import mesosphere.marathon.client.model.v2.App

import java.time.Instant

import static com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.UpsertDcosLoadBalancerAtomicOperationDescription.PortRange

@EqualsAndHashCode(includes = ["name", "region", "account"])
class DcosLoadBalancer implements LoadBalancer, Serializable, LoadBalancerProvider.Item {
  String name
  final String type = DcosCloudProvider.ID
  final String cloudProvider = DcosCloudProvider.ID

  String region
  String account
  Long createdTime
  App app
  String json

  Set<LoadBalancerServerGroup> serverGroups = [] as Set
  UpsertDcosLoadBalancerAtomicOperationDescription description

  DcosLoadBalancer(String name, String region, String accountName) {
    this.name = name
    this.region = region
    this.account = accountName
  }

  DcosLoadBalancer(String account, String cluster, App app, List<DcosServerGroup> serverGroupList) {
    this.app = app
    this.json = app.toString()

    def id = DcosSpinnakerLbId.parse(app.id, account).get()
    this.account = id.account
    this.name = id.loadBalancerName
    this.region = cluster
    this.description = toDescription(cluster, id, app)

    this.createdTime = app.versionInfo?.lastConfigChangeAt ? Instant.parse(app.versionInfo.lastConfigChangeAt).toEpochMilli() : null

    this.serverGroups = serverGroupList?.collect { serverGroup ->
      new LoadBalancerServerGroup(
        // TODO account not part of this model, but it appears the deck UI uses it when diffing servergroups for a loadbalancer.
        // Causes a display bug in deck that affects at least kubernetes as well.
        name: serverGroup?.name,
        account: serverGroup?.account,
        region: serverGroup?.region,
        isDisabled: serverGroup?.isDisabled(),
        instances: serverGroup?.instances?.findResults { instance ->
          // TODO once we can do this
          //if (instance.isAttached(this.name)) {

          return new LoadBalancerInstance(
            id: instance.name,
            zone: instance.zone,
            health: [
              state: instance.healthState.toString()
            ]
          )
          //} else {
          //  return (LoadBalancerInstance) null // Groovy generics need to be convinced all control flow paths return the same object type
          //}
        } as Set,
        // TODO once we can do this
        detachedInstances: [],
        cloudProvider: DcosCloudProvider.ID
      )
    } as Set
  }

  static UpsertDcosLoadBalancerAtomicOperationDescription toDescription(String cluster, DcosSpinnakerLbId id, App app) {

    def description = new UpsertDcosLoadBalancerAtomicOperationDescription()

    description.account = id.account
    description.name = id.loadBalancerName
    description.region = cluster
    description.dcosCluster = cluster

    def names = Names.parseName(description.name)

    description.app = names.app
    description.stack = names.stack
    description.detail = names.detail

    def sortedPorts = app.portDefinitions?.collect({ it.port })?.sort() ?: []
    description.bindHttpHttps = sortedPorts.containsAll([80, 443])

    description.cpus = app.cpus ?: 0
    description.mem = app.mem ?: 0
    description.instances = app.instances ?: 0

    description.acceptedResourceRoles = app.acceptedResourceRoles

    // TODO This won't work for non-sequential port ranges.
    sortedPorts = sortedPorts - [80, 443, 9090, 9091]

    if (!sortedPorts.isEmpty()) {
      description.portRange = new PortRange(minPort: sortedPorts.first(), maxPort: sortedPorts.last(), protocol: app.portDefinitions.first().protocol)
    }

    description
  }

  @Override
  @JsonIgnore
  List<LoadBalancerProvider.ByAccount> getByAccounts() {
    [new ByAccount(name: account)]
  }

  static class ByAccount implements LoadBalancerProvider.ByAccount {
    String name
    List<LoadBalancerProvider.ByRegion> byRegions = []
  }
}
