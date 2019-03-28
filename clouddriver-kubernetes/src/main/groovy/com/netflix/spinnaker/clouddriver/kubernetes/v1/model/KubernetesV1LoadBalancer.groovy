/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v1.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.provider.KubernetesModelUtil
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesApiConverter
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.loadbalancer.KubernetesLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import groovy.transform.EqualsAndHashCode
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.client.internal.SerializationUtils

@EqualsAndHashCode(includes = ["name", "namespace", "account"])
class KubernetesV1LoadBalancer implements LoadBalancer, Serializable, LoadBalancerProvider.Item {
  String name
  final String type = KubernetesCloudProvider.ID
  final String cloudProvider = KubernetesCloudProvider.ID
  String region
  String namespace
  String account
  Long createdTime
  Service service
  String yaml
  // Set of server groups represented as maps of strings -> objects.
  Set<LoadBalancerServerGroup> serverGroups = [] as Set
  List<String> securityGroups = []
  KubernetesLoadBalancerDescription description

  KubernetesV1LoadBalancer(String name, String namespace, String accountName) {
    this.name = name
    this.namespace = namespace
    this.region = namespace
    this.account = accountName
  }

  KubernetesV1LoadBalancer(Service service, List<KubernetesV1ServerGroup> serverGroupList, String accountName, List<String> securityGroups) {
    this.service = service
    this.name = service.metadata.name
    this.namespace = service.metadata.namespace
    this.securityGroups = securityGroups
    this.region = this.namespace
    this.description = KubernetesApiConverter.fromService(service, accountName)
    this.account = accountName
    this.createdTime = KubernetesModelUtil.translateTime(service.metadata?.creationTimestamp)
    this.yaml = SerializationUtils.dumpWithoutRuntimeStateAsYaml(service)
    this.serverGroups = serverGroupList?.collect { serverGroup ->
      new LoadBalancerServerGroup(
        name: serverGroup?.name,
        isDisabled: serverGroup?.isDisabled(),
        instances: serverGroup?.instances?.findResults { instance ->
          if (instance.isAttached(this.name)) {
            return new LoadBalancerInstance(
                id: instance.name,
                zone: region,
                health: [
                    state: instance.healthState.toString()
                ]
            )
          } else {
            return (LoadBalancerInstance) null // Groovy generics need to be convinced all control flow paths return the same object type
          }
        } as Set,
        detachedInstances: serverGroup?.instances?.findResults { instance ->
          if (!instance.isAttached(this.name)) {
            return instance.name
          } else {
            return (String) null
          }
        } as Set,
        cloudProvider: KubernetesCloudProvider.ID
      )
    } as Set
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
