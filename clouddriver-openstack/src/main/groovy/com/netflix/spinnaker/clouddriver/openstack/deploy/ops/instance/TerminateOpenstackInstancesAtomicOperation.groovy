/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.instance

import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.instance.OpenstackInstancesDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup.AbstractStackUpdateOpenstackAtomicOperation
import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerResolver
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import groovy.util.logging.Slf4j
import org.openstack4j.api.Builders
import org.openstack4j.model.compute.Server
import org.openstack4j.model.heat.Resource
import org.openstack4j.model.heat.Stack

/**
 * Terminates an Openstack instance by marking the stack resource unhealthy and doing a stack update. This will
 * recreate instances until the stack reaches the correct size.
 *
 * TODO test upsert load balancer
 */
@Slf4j
class TerminateOpenstackInstancesAtomicOperation extends AbstractStackUpdateOpenstackAtomicOperation implements LoadBalancerResolver {

  final String phaseName = "TERMINATE_INSTANCES"

  final String operation = AtomicOperations.TERMINATE_INSTANCES


  TerminateOpenstackInstancesAtomicOperation(OpenstackInstancesDescription description) {
    super(description)
  }

  /*
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstances": { "instanceIds": ["os-test-v000-beef"], "account": "test", "region": "region1" }} ]' localhost:7002/openstack/ops
   * curl -X GET -H "Accept: application/json" localhost:7002/task/1
   */

  @Override
  String getServerGroupName() {
    String instanceId = description.instanceIds?.find() ?: null
    String serverGroupName = ""
    if (instanceId) {
      task.updateStatus phaseName, "Getting server group name from instance $instanceId ..."
      Server server = provider.getServerInstance(description.region, instanceId)
      serverGroupName = server.metadata?.get("metering.stack.name") ?: provider.getStack(description.region, server.metadata?.get("metering.stack"))?.name
      task.updateStatus phaseName, "Found server group name $serverGroupName from instance $instanceId."
    }
    serverGroupName
  }

  @Override
  void preUpdate(Stack stack) {

    //get asg_resource stack id and name
    task.updateStatus phaseName, "Finding asg resource for $stack.name ..."
    Resource asg = provider.getAsgResourceForStack(description.region, stack)
    task.updateStatus phaseName, "Finding nested stack for resource $asg.type ..."
    Stack nested = provider.getStack(description.region, asg.physicalResourceId)

    description.instanceIds.each { id ->

      //get server name
      task.updateStatus phaseName, "Getting server details for $id ..."
      Server server = provider.getServerInstance(description.region, id)

      //get resource
      task.updateStatus phaseName, "Finding server group resource for $id ..."
      //for some reason it only works to look up the resource from the parent stack, not the nested stack
      Resource instance = provider.getInstanceResourceForStack(description.region, stack, server.name)

      //mark unhealthy - subsequent stack update will delete and recreate the resource
      task.updateStatus phaseName, "Marking server group resource $instance.resourceName unhealthy ..."
      provider.markStackResourceUnhealthy(description.region, nested.name, nested.id, instance.resourceName,
        Builders.resourceHealth().markUnhealthy(true).resourceStatusReason("Deleted instance $id").build())

    }
  }

  OpenstackClientProvider getProvider() {
    description.credentials.provider
  }

}
