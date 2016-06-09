/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.instance

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.instance.OpenstackInstancesRegistrationDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.openstack4j.model.network.ext.LbPool

/**
 * Base class that will handle both load balancer registration and deregistration.
 */
abstract class AbstractRegistrationOpenstackInstancesAtomicOperation implements AtomicOperation<Void> {

  abstract String getBasePhase() // Either 'REGISTER' or 'DEREGISTER'.
  abstract Boolean getAction() // Either 'true' or 'false', for Register and Deregister respectively.
  abstract String getVerb() // Either 'registering' or 'deregistering'.
  abstract String getPreposition() // Either 'with' or 'from'

  OpenstackInstancesRegistrationDescription description

  AbstractRegistrationOpenstackInstancesAtomicOperation(OpenstackInstancesRegistrationDescription description) {
    this.description = description
  }

  protected static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus basePhase, "Start $verb all instances $preposition load balancers..."
    OpenstackClientProvider provider = description.credentials.provider
    description.loadBalancerIds.each { lb ->
      description.instanceIds.each { id ->
        task.updateStatus basePhase, "$verb instance $id $preposition load balancer $lb"
        task.updateStatus basePhase, "Getting details for load balancer $lb"
        LbPool pool = provider.getLoadBalancerPool(description.region, lb)
        task.updateStatus basePhase, "Getting ip address for service instance $id"
        String ip = provider.getIpForInstance(description.region, id)
        if (action) {
          task.updateStatus basePhase, "Getting internal port from load balancer $pool.name"
          int internalPort = provider.getInternalLoadBalancerPort(pool)
          task.updateStatus basePhase, "Adding member with ip $ip to load balancer $pool.name on internal port $internalPort with weight $description.weight"
          provider.addMemberToLoadBalancerPool(description.region, ip, lb, internalPort, description.weight)
        } else {
          task.updateStatus basePhase, "Getting member id for server instance $id and ip $ip on load balancer $pool.name"
          String memberId = provider.getMemberIdForInstance(description.region, ip, pool)
          task.updateStatus basePhase, "Removing member with ip $ip from load balancer $pool.name"
          provider.removeMemberFromLoadBalancerPool(description.region, memberId)
        }
        task.updateStatus basePhase, "Completed $verb instance $id $preposition load balancer $lb"
      }
    }

    task.updateStatus basePhase, "Completed $verb instances $preposition load balancers."
  }
}
