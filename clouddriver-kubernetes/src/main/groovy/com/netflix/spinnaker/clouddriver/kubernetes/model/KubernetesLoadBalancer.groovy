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

package com.netflix.spinnaker.clouddriver.kubernetes.model

import com.netflix.spinnaker.clouddriver.kubernetes.api.KubernetesApiAdaptor
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.loadbalancer.KubernetesLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.client.internal.SerializationUtils

@CompileStatic
@EqualsAndHashCode(includes = ["name", "accountName"])
class KubernetesLoadBalancer implements LoadBalancer, Serializable {
  String name
  String type = "kubernetes"
  String region
  String namespace
  String account
  Long createdTime
  Service service
  String yaml
  // Set of server groups represented as maps of strings -> objects.
  Set<LoadBalancerServerGroup> serverGroups
  KubernetesLoadBalancerDescription description

  KubernetesLoadBalancer(String name, String namespace, String accountName) {
    this.name = name
    this.namespace = namespace
    this.region = namespace
    this.account = accountName
  }

  KubernetesLoadBalancer(Service service, List<KubernetesServerGroup> serverGroupList, String accountName) {
    this.service = service
    this.name = service.metadata.name
    this.namespace = service.metadata.namespace
    this.region = this.namespace
    this.description = KubernetesApiAdaptor.fromService(service)
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
        } as Set)
    } as Set
  }
}
