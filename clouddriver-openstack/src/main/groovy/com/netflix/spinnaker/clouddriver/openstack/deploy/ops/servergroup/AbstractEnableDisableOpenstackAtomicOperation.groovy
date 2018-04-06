/*
 * Copyright 2016 Veritas Technologies LLC.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.consul.deploy.ops.EnableDisableConsulInstance
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.BlockingStatusChecker
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.EnableDisableAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import com.netflix.spinnaker.clouddriver.openstack.deploy.ops.loadbalancer.LoadBalancerChecker
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.util.logging.Slf4j
import org.openstack4j.model.compute.Server
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.network.ext.LoadBalancerV2StatusTree
import retrofit.RetrofitError

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.function.Supplier

@Slf4j
abstract class AbstractEnableDisableOpenstackAtomicOperation implements AtomicOperation<Void> {
  abstract boolean isDisable()

  abstract String getPhaseName()

  abstract String getOperation()

  static final int DEFAULT_WEIGHT = 1

  EnableDisableAtomicOperationDescription description

  AbstractEnableDisableOpenstackAtomicOperation(EnableDisableAtomicOperationDescription description) {
    this.description = description
  }

  protected static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Void operate(List priorOutputs) {
    String verb = disable ? 'disable' : 'enable'
    String gerund = disable ? 'Disabling' : 'Enabling'

    task.updateStatus phaseName, "Initializing $verb server group operation for $description.serverGroupName in $description.region..."
    def credentials = description.credentials

    if (credentials.credentials.consulConfig?.enabled) {
      task.updateStatus phaseName, "$gerund server group in Consul..."

      List<String> instanceIds = provider.getInstanceIdsForStack(description.region, description.serverGroupName)
      instanceIds.each { String instanceId ->
        Server instance = provider.getServerInstance(description.region, instanceId)
        if (!instance) {
          throw new OpenstackResourceNotFoundException("Could not find server: $instanceId in region: $description.region")
        }
        try {
          EnableDisableConsulInstance.operate(credentials.credentials.consulConfig,
            instance.name,
            disable
              ? EnableDisableConsulInstance.State.disable
              : EnableDisableConsulInstance.State.enable)
        } catch (RetrofitError e) {
          // Consul isn't running
          log.warn(e.message)
        }
      }
    }

    try {
      task.updateStatus phaseName, "Getting stack details for $description.serverGroupName..."
      List<String> instanceIds = provider.getInstanceIdsForStack(description.region, description.serverGroupName)
      if (instanceIds?.size() > 0) {
        Stack stack = provider.getStack(description.region, description.serverGroupName)
        if (!stack) {
          throw new OpenstackResourceNotFoundException("Could not find stack $description.serverGroupName in region: $description.region")
        }
        if (stack.tags?.size() > 0) {
          enableDisableLoadBalancerMembers(instanceIds, stack.tags)
          task.updateStatus phaseName, "Done ${gerund.toLowerCase()} server group $description.serverGroupName in $description.region."
        } else {
          task.updateStatus phaseName, "Did not find any load balancers associated with $description.serverGroupName, nothing to do."
        }
      } else {
        task.updateStatus phaseName, "Did not find any instances for $description.serverGroupName, nothing to do."
      }
    } catch (Exception e) {
      throw new OpenstackOperationException(operation, e)
    }
  }

  void enableDisableLoadBalancerMembers(List<String> instanceIds, List<String> loadBalancerIds) {
    String gerund = disable ? 'Disabling' : 'Enabling'
    task.updateStatus phaseName, "$gerund instances in load balancers..."
    Map<String, Future<LoadBalancerV2StatusTree>> statusTrees = [:]
    Map<String, Future<List<String>>> ips = instanceIds.collectEntries { instanceId ->
      task.updateStatus phaseName, "Getting ip for instance $instanceId..."
      [(instanceId): CompletableFuture.supplyAsync({
        provider.getIpsForInstance(description.region, instanceId).collect { it.addr }
      } as Supplier<String>).exceptionally { t ->
        null
      }]
    }
    loadBalancerIds.each { lbId ->
      task.updateStatus phaseName, "Getting load balancer tree for $lbId..."
      statusTrees << [(lbId): CompletableFuture.supplyAsync({
        provider.getLoadBalancerStatusTree(description.region, lbId)
      } as Supplier<LoadBalancerV2StatusTree>)]
    }
    CompletableFuture.allOf([statusTrees.values(), ips.values()].flatten() as CompletableFuture[]).join()
    for (String id : instanceIds) {
      List<String> ip = ips[(id)].get()
      if (!ip) {
        task.updateStatus phaseName, "Could not find floating ip for instance $id, continuing with next instance"
      } else {
        loadBalancerIds.each { lbId ->
          LoadBalancerV2StatusTree status = statusTrees[(lbId)].get()
          status.loadBalancerV2Status?.listenerStatuses?.each { listenerStatus ->
            listenerStatus.lbPoolV2Statuses?.each { poolStatus ->
              poolStatus.memberStatuses?.each { memberStatus ->
                if (memberStatus.address && ip.contains(memberStatus.address)) {
                  task.updateStatus phaseName, "$gerund member instance $id with ip $memberStatus.address on load balancer $lbId with listener ${listenerStatus.id} and pool ${poolStatus.id}..."
                  provider.updatePoolMemberStatus(description.region, poolStatus.id, memberStatus.id, !disable)
                  task.updateStatus phaseName, "Waiting on $gerund member instance $id with ip $memberStatus.address on load balancer $lbId with listener ${listenerStatus.id} and pool ${poolStatus.id}..."
                  LoadBalancerChecker.from(description.credentials.credentials.lbaasConfig, LoadBalancerChecker.Operation.UPDATE).execute {
                    provider.getLoadBalancer(description.region, lbId)
                  }
                }
              }
            }
          }
        }
      }
    }
    task.updateStatus phaseName, "Finished deregistering instances from load balancers."
  }

  OpenstackClientProvider getProvider() {
    description.credentials.provider
  }

}
