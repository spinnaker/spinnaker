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

package com.netflix.spinnaker.clouddriver.titus.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.AbstractEurekaSupport
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.client.model.ActivateJobRequest
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.description.EnableDisableInstanceDiscoveryDescription
import com.netflix.spinnaker.clouddriver.titus.deploy.description.EnableDisableServerGroupDescription
import com.netflix.spinnaker.clouddriver.titus.deploy.ops.discovery.TitusEurekaSupport
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
abstract class AbstractEnableDisableAtomicOperation implements AtomicOperation<Void> {

  @Autowired
  TitusEurekaSupport discoverySupport

  private static final long THROTTLE_MS = 150

  abstract boolean isDisable()

  abstract String getPhaseName()

  TitusClientProvider titusClientProvider

  EnableDisableServerGroupDescription description

  AbstractEnableDisableAtomicOperation(TitusClientProvider titusClientProvider, EnableDisableServerGroupDescription description) {
    this.titusClientProvider = titusClientProvider
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    String verb = disable ? 'Disable' : 'Enable'
    task.updateStatus phaseName, "Initializing ${verb} ServerGroup operation for $description.serverGroupName"
    boolean succeeded = operateOnServerGroup(description.serverGroupName, description.credentials, description.region)
    if (!succeeded && (!task.status || !task.status.isFailed())) {
      task.fail()
    }
    task.updateStatus phaseName, "Finished ${verb} ServerGroup operation for $description.serverGroupName"
  }

  private boolean operateOnServerGroup(String serverGroupName, NetflixTitusCredentials credentials, String region) {
    String presentParticipling = disable ? 'Disabling' : 'Enabling'
    String verb = disable ? 'Disable' : 'Enable'

    try {

      def provider = titusClientProvider.getTitusClient(credentials, region)
      def loadBalancingClient = titusClientProvider.getTitusLoadBalancerClient(credentials, region)
      def job = provider.findJobByName(serverGroupName, true)

      if (!job) {
        task.updateStatus phaseName, "No Job named '$serverGroupName' found in $region"
        return true
      }

      task.updateStatus phaseName, "${presentParticipling} ServerGroup '$serverGroupName' in $region..."

      provider.activateJob(
        new ActivateJobRequest()
          .withUser('spinnaker')
          .withJobId(job.id)
          .withInService(!disable)
      )

      if (loadBalancingClient && job.labels.containsKey("spinnaker.targetGroups")) {
        if (disable) {
          task.updateStatus phaseName, "Removing ${job.id} from target groups"
          loadBalancingClient.getJobLoadBalancers(job.id).each { loadBalancerId ->
            task.updateStatus phaseName, "Removing ${job.id} from ${loadBalancerId.id} "
            loadBalancingClient.removeLoadBalancer(job.id, loadBalancerId.getId())
          }
        } else {
          task.updateStatus phaseName, "Restoring ${job.id} into target groups"
          List<String> attachedLoadBalancers = loadBalancingClient.getJobLoadBalancers(job.id)*.id
          job.labels.get("spinnaker.targetGroups").split(',').each { loadBalancerId ->
            if (!attachedLoadBalancers.contains(loadBalancerId)) {
              task.updateStatus phaseName, "Restoring ${job.id} into ${loadBalancerId}"
              loadBalancingClient.addLoadBalancer(job.id, loadBalancerId)
            }
          }
        }
      }

      if (job.tasks) {
        def status = disable ? AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE : AbstractEurekaSupport.DiscoveryStatus.UP
        task.updateStatus phaseName, "Marking ServerGroup $serverGroupName as $status with Discovery"

        def enableDisableInstanceDiscoveryDescription = new EnableDisableInstanceDiscoveryDescription(
          credentials: credentials,
          region: region,
          asgName: serverGroupName,
          instanceIds: job.tasks*.instanceId
        )
        discoverySupport.updateDiscoveryStatusForInstances(
          enableDisableInstanceDiscoveryDescription, task, phaseName, status, job.tasks*.instanceId
        )
      }

      try {
        provider.setAutoscaleEnabled(job.id, !disable)
      } catch (Exception e) {
        log.error("Error toggling autoscale enabled for Titus job ${job.id} in ${credentials.name}/${region}", e)
      }

      task.updateStatus phaseName, "Finished ${presentParticipling} ServerGroup $serverGroupName."

      return true
    } catch (e) {
      def errorMessage = "Could not ${verb} ServerGroup '$serverGroupName' in region $region! Failure Type: ${e.class.simpleName}; Message: ${e.message}"
      log.error(errorMessage, e)
      if (task.status && (!task.status || !task.status.isFailed())) {
        task.updateStatus phaseName, errorMessage
      }
      return false
    }
  }

  Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
