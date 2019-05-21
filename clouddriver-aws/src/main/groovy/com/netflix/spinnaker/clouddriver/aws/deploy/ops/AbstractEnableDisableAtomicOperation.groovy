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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesResult
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.InstanceStateName
import com.amazonaws.services.ec2.model.Reservation
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.Instance
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerNotFoundException
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DeregisterTargetsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.InvalidTargetException
import com.amazonaws.services.elasticloadbalancingv2.model.RegisterTargetsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.TargetDescription
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroupNotFoundException
import com.netflix.spinnaker.clouddriver.aws.deploy.description.EnableDisableAsgDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.EnableDisableInstanceDiscoveryDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.discovery.AwsEurekaSupport
import com.netflix.spinnaker.clouddriver.aws.model.AutoScalingProcessType
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.AbstractEurekaSupport
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
abstract class AbstractEnableDisableAtomicOperation implements AtomicOperation<Void> {
  private static final long THROTTLE_MS = 150
  private static final String INSTANCE_ASG_TAG_NAME = "tag:aws:autoscaling:groupName"

  abstract boolean isDisable()

  abstract String getPhaseName()

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Autowired
  AwsEurekaSupport discoverySupport

  EnableDisableAsgDescription description

  AbstractEnableDisableAtomicOperation(EnableDisableAsgDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    String verb = disable ? 'Disable' : 'Enable'
    String descriptor = description.asgs.collect { it.toString() }
    task.updateStatus phaseName, "Initializing ${verb} ASG operation for $descriptor..."
    boolean allSucceeded = true
    for (asg in description.asgs) {
      allSucceeded = allSucceeded && operateOnAsg(asg.serverGroupName, asg.region)
    }

    if (!allSucceeded && (!task.status || !task.status.isFailed())) {
      task.fail()
    }
    task.updateStatus phaseName, "Finished ${verb} ASG operation for $descriptor."
  }

  private boolean operateOnAsg(String serverGroupName, String region) {
    String presentParticipling = disable ? 'Disabling' : 'Enabling'
    String verb = disable ? 'Disable' : 'Enable'
    def credentials = description.credentials
    try {
      def regionScopedProvider = regionScopedProviderFactory.forRegion(credentials, region)
      def loadBalancing = regionScopedProvider.amazonElasticLoadBalancing
      def lbv2 = regionScopedProvider.getAmazonElasticLoadBalancingV2(true)

      def asgService = regionScopedProvider.asgService
      def asg = asgService.getAutoScalingGroup(serverGroupName)
      if (!asg) {
        task.updateStatus phaseName, "No ASG named '$serverGroupName' found in $region"
        return true
      }

      if (asg.status) {
        // a non-null status indicates that a DeleteAutoScalingGroup action is in progress
        task.updateStatus phaseName, "ASG '$serverGroupName' is currently being destroyed and cannot be modified (status: ${asg.status})"
        return true
      }

      task.updateStatus phaseName, "${presentParticipling} ASG '$serverGroupName' in $region..."
      if (disable) {
        if (description.desiredPercentage == null || description.desiredPercentage == 100) {
          asgService.suspendProcesses(serverGroupName, AutoScalingProcessType.getDisableProcesses())
        }
      } else {
        asgService.resumeProcesses(serverGroupName, AutoScalingProcessType.getDisableProcesses())
      }

      List<String> instanceIds = asg.instances.findAll {
        it.lifecycleState == "InService" || it.lifecycleState.startsWith("Pending")
      }*.instanceId

      int failedAttempts = 0
      if (instanceIds) {
        DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest().withInstanceIds(instanceIds)
        List<Reservation> reservations = []
        while (true) {
          try {
            DescribeInstancesResult describeInstancesResult = regionScopedProvider.amazonEC2.describeInstances(describeInstancesRequest)
            reservations.addAll(describeInstancesResult.getReservations())
            if (!describeInstancesResult.nextToken) {
              break
            }

            describeInstancesRequest.setNextToken(describeInstancesResult.nextToken)
          } catch (Exception e1) {
            failedAttempts++
            log.error("Failed to describe one of the instances in {}", instanceIds, e1)
            if (failedAttempts >= 10) {
              task.updateStatus phaseName, "Failed to describe instances 10 times, aborting. This may happen if the server group has been disabled for a long period of time."

              try {
                // fallback to a tag-based instance lookup (will be slower in large region/accounts)
                task.updateStatus phaseName, "Falling back to tag-based instance lookup"
                reservations.addAll(fetchInstancesTaggedWithServerGroup(regionScopedProvider, serverGroupName))
                break
              } catch (Exception e2) {
                task.updateStatus phaseName, "Failed to lookup instances server group tag"
                log.error(
                    "Failed to describe instances using server group name filter (serverGroup: {})",
                    serverGroupName,
                    e2
                )
                return false
              }
            }
          }
        }

        Set<String> filteredInstanceIds = []
        for (Reservation reservation : reservations) {
          filteredInstanceIds += reservation.getInstances().findAll {
            [ InstanceStateName.Running, InstanceStateName.Pending ].contains(InstanceStateName.fromValue(it.getState().getName()))
          }*.instanceId
        }
        instanceIds = filteredInstanceIds as List<String>
      }

      if (instanceIds && description.desiredPercentage && disable) {
        instanceIds = discoverySupport.getInstanceToModify(credentials.name, region, serverGroupName, instanceIds, description.desiredPercentage)
        task.updateStatus phaseName, "Only disabling instances $instanceIds on ASG $serverGroupName with percentage ${description.desiredPercentage}"

        task.addResultObjects([
          ["instanceIdsToDisable": instanceIds]
        ])
      }

      // ELB/ALB registration
      if (disable) {
        changeRegistrationOfInstancesWithLoadBalancer(asg.loadBalancerNames, instanceIds) { String loadBalancerName, List<Instance> instances ->
          try {
            task.updateStatus phaseName, "Deregistering instances from Load Balancers..."
            loadBalancing.deregisterInstancesFromLoadBalancer(new DeregisterInstancesFromLoadBalancerRequest(loadBalancerName, instances))
          } catch (LoadBalancerNotFoundException e) {
            task.updateStatus phaseName, "Unable to deregister instances, ${loadBalancerName} does not exist (${e.message})"
          }
        }
        changeRegistrationOfInstancesWithTargetGroups(asg.targetGroupARNs, instanceIds) { String targetGroupArn, List<TargetDescription> instances ->
          try {
            task.updateStatus phaseName, "Deregistering instances from Target Groups..."
            lbv2.deregisterTargets(new DeregisterTargetsRequest().withTargetGroupArn(targetGroupArn).withTargets(instances))
          } catch (TargetGroupNotFoundException | InvalidTargetException ex) {
            task.updateStatus phaseName, "Unable to deregister targets, $targetGroupArn invalid ($ex.message)"
          }
        }
      } else {
        changeRegistrationOfInstancesWithLoadBalancer(asg.loadBalancerNames, instanceIds) { String loadBalancerName, List<Instance> instances ->
          task.updateStatus phaseName, "Registering instances with Load Balancers..."
          loadBalancing.registerInstancesWithLoadBalancer(new RegisterInstancesWithLoadBalancerRequest(loadBalancerName, instances))
        }
        changeRegistrationOfInstancesWithTargetGroups(asg.targetGroupARNs, instanceIds) { String targetGroupArn, List<TargetDescription> targets ->
          task.updateStatus phaseName, "Registering instances with Target Groups..."
          lbv2.registerTargets(new RegisterTargetsRequest().withTargetGroupArn(targetGroupArn).withTargets(targets))
        }
      }

      // eureka registration
      if (credentials.discoveryEnabled && instanceIds) {
        def status = disable ? AbstractEurekaSupport.DiscoveryStatus.Disable : AbstractEurekaSupport.DiscoveryStatus.Enable
        task.updateStatus phaseName, "Marking ASG $serverGroupName as $status with Discovery"

        def enableDisableInstanceDiscoveryDescription = new EnableDisableInstanceDiscoveryDescription(
            credentials: credentials,
            region: region,
            asgName: serverGroupName,
            instanceIds: instanceIds,
            targetHealthyDeployPercentage: description.targetHealthyDeployPercentage != null ? description.targetHealthyDeployPercentage : 100
        )
        discoverySupport.updateDiscoveryStatusForInstances(
            enableDisableInstanceDiscoveryDescription, task, phaseName, status, instanceIds
        )
      }
      task.updateStatus phaseName, "Finished ${presentParticipling} ASG $serverGroupName."
      return true
    } catch (e) {
      def errorMessage = "Could not ${verb} ASG '$serverGroupName' in region $region! Failure Type: ${e.class.simpleName}; Message: ${e.message}"
      log.error(errorMessage, e)
      if (task.status && (!task.status || !task.status.isFailed())) {
        task.updateStatus phaseName, errorMessage
      }
      return false
    }
  }

  /**
   * Fetch all instances associated with server group (as determined by `tag:aws:autoscaling:groupName` tag)
   */
  private List<Reservation> fetchInstancesTaggedWithServerGroup(
      RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider,
      String serverGroupName
  ) {
    DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest().withFilters(
        new Filter().withName(INSTANCE_ASG_TAG_NAME).withValues(serverGroupName)
    )

    DescribeInstancesResult describeInstancesResult = regionScopedProvider.amazonEC2.describeInstances(
        describeInstancesRequest
    )
    List<Reservation> reservations = describeInstancesResult.getReservations()

    while (describeInstancesResult.getNextToken()) {
      describeInstancesRequest.setNextToken(describeInstancesResult.getNextToken())
      describeInstancesResult = regionScopedProvider.amazonEC2.describeInstances(describeInstancesRequest)
      reservations += describeInstancesResult.getReservations()
    }

    return reservations
  }

  private static void changeRegistrationOfInstancesWithTargetGroups(Collection<String> targetGroupArns, Collection<String> instanceIds, Closure actOnInstancesAndTargetGroup) {
    handleInstancesWithLoadBalancing(targetGroupArns, instanceIds, { new TargetDescription().withId(it) }, actOnInstancesAndTargetGroup)
  }

  private static void changeRegistrationOfInstancesWithLoadBalancer(Collection<String> loadBalancerNames, Collection<String> instanceIds, Closure actOnInstancesAndLoadBalancer) {
    handleInstancesWithLoadBalancing(loadBalancerNames, instanceIds, { new Instance(instanceId: it)}, actOnInstancesAndLoadBalancer)
  }

  private static void handleInstancesWithLoadBalancing(Collection<String> lbIdentifiers, Collection<String> instanceIds, Closure instanceIdTransform, Closure actOnInstancesAndLoadBalancer) {
    if (instanceIds && lbIdentifiers) {
      def instances = instanceIds.collect(instanceIdTransform)
      for (String lbId : lbIdentifiers) {
        actOnInstancesAndLoadBalancer(lbId, instances)
      }
    }
  }


  Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
