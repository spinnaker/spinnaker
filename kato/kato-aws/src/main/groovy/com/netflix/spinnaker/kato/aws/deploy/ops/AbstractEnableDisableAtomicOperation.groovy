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

package com.netflix.spinnaker.kato.aws.deploy.ops
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.Instance
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerNotFoundException
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.kato.aws.deploy.description.EnableDisableAsgDescription
import com.netflix.spinnaker.kato.aws.deploy.description.EnableDisableInstanceDiscoveryDescription
import com.netflix.spinnaker.kato.aws.deploy.ops.discovery.DiscoverySupport
import com.netflix.spinnaker.kato.aws.model.AutoScalingProcessType
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
abstract class AbstractEnableDisableAtomicOperation implements AtomicOperation<Void> {
  private static final long THROTTLE_MS = 150

  abstract boolean isDisable()

  abstract String getPhaseName()

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Autowired
  DiscoverySupport discoverySupport

  @Autowired
  AmazonClientProvider amazonClientProvider

  EnableDisableAsgDescription description

  AbstractEnableDisableAtomicOperation(EnableDisableAsgDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    String verb = disable ? 'Disable' : 'Enable'
    String descriptor = description.asgName ?: description.asgs.collect { it.toString() }
    task.updateStatus phaseName, "Initializing ${verb} ASG operation for $descriptor..."
    boolean allSucceeded = true
    for (region in description.regions) {
      def asgName = description.asgName
      allSucceeded = allSucceeded && operateOnAsg(asgName, region)
    }

    for (asg in description.asgs) {
      allSucceeded = allSucceeded && operateOnAsg(asg.asgName, asg.region)
    }

    if (!allSucceeded && (!task.status || !task.status.isFailed())) {
      task.fail()
    }
    task.updateStatus phaseName, "Finished ${verb} ASG operation for $descriptor."
  }

  private boolean operateOnAsg(String asgName, String region) {
    String presentParticipling = disable ? 'Disabling' : 'Enabling'
    String verb = disable ? 'Disable' : 'Enable'
    def credentials = description.credentials
    try {
      def regionScopedProvider = regionScopedProviderFactory.forRegion(credentials, region)
      def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(credentials, region, true)

      def asgService = regionScopedProvider.asgService
      def asg = asgService.getAutoScalingGroup(asgName)
      if (!asg) {
        task.updateStatus phaseName, "No ASG named '$asgName' found in $region"
        return true
      }

      if (asg.status) {
        // a non-null status indicates that a DeleteAutoScalingGroup action is in progress
        task.updateStatus phaseName, "ASG '$asgName' is currently being destroyed and cannot be modified (status: ${asg.status})"
        return true
      }

      task.updateStatus phaseName, "${presentParticipling} ASG '$asgName' in $region..."
      if (disable) {
        asgService.suspendProcesses(asgName, AutoScalingProcessType.getDisableProcesses())
      } else {
        asgService.resumeProcesses(asgName, AutoScalingProcessType.getDisableProcesses())
      }

      if (disable) {
        changeRegistrationOfInstancesWithLoadBalancer(asg.loadBalancerNames, asg.instances*.instanceId) { String loadBalancerName, List<Instance> instances ->
          try {
            task.updateStatus phaseName, "Deregistering instances from Load Balancers..."
            loadBalancing.deregisterInstancesFromLoadBalancer(new DeregisterInstancesFromLoadBalancerRequest(loadBalancerName, instances))
          } catch (LoadBalancerNotFoundException e) {
            task.updateStatus phaseName, "Unable to deregister instances, ${loadBalancerName} does not exist (${e.message})"
          }
        }
      } else {
        changeRegistrationOfInstancesWithLoadBalancer(asg.loadBalancerNames, asg.instances*.instanceId) { String loadBalancerName, List<Instance> instances ->
          task.updateStatus phaseName, "Registering instances with Load Balancers..."
          loadBalancing.registerInstancesWithLoadBalancer(new RegisterInstancesWithLoadBalancerRequest(loadBalancerName, instances))
        }
      }

      if (credentials.discoveryEnabled && asg.instances) {
        def status = disable ? DiscoverySupport.DiscoveryStatus.Disable : DiscoverySupport.DiscoveryStatus.Enable
        task.updateStatus phaseName, "Marking ASG $asgName as $status with Discovery"

        def enableDisableInstanceDiscoveryDescription = new EnableDisableInstanceDiscoveryDescription(
            credentials: credentials,
            region: region,
            asgName: asgName,
            instanceIds: asg.instances*.instanceId
        )
        discoverySupport.updateDiscoveryStatusForInstances(
            enableDisableInstanceDiscoveryDescription, task, phaseName, status, asg.instances*.instanceId
        )
      }
      task.updateStatus phaseName, "Finished ${presentParticipling} ASG $asgName."
      return true
    } catch (e) {
      def errorMessage = "Could not ${verb} ASG '$asgName' in region $region! Failure Type: ${e.class.simpleName}; Message: ${e.message}"
      log.error(errorMessage, e)
      if (task.status && (!task.status || !task.status.isFailed())) {
        task.updateStatus phaseName, errorMessage
      }
      return false
    }
  }

  private static void changeRegistrationOfInstancesWithLoadBalancer(Collection<String> loadBalancerNames, Collection<String> instanceIds, Closure actOnInstancesAndLoadBalancer) {
    if (instanceIds && loadBalancerNames) {
      def instances = instanceIds.collect { new Instance(instanceId: it) }
      loadBalancerNames.eachWithIndex { String loadBalancerName, int index ->
        if (index > 0) {
          sleep THROTTLE_MS
        }
        actOnInstancesAndLoadBalancer(loadBalancerName, instances)
      }
    }
  }

  Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
