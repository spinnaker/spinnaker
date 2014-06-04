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
package com.netflix.spinnaker.kato.deploy
import com.amazonaws.services.autoscaling.model.*
import com.amazonaws.services.elasticloadbalancing.model.DescribeInstanceHealthRequest
import com.amazonaws.services.elasticloadbalancing.model.InstanceState
import com.amazonaws.services.simpleworkflow.flow.annotations.ManualActivityCompletion
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.frigga.Names
import com.netflix.glisten.ActivityOperations
import com.netflix.glisten.impl.swf.SwfActivityOperations
import com.netflix.spinnaker.kato.deploy.aws.AutoScalingWorker
import com.netflix.spinnaker.kato.deploy.aws.description.*
import com.netflix.spinnaker.kato.deploy.aws.ops.*
import com.netflix.spinnaker.kato.deploy.aws.userdata.UserDataProvider
import com.netflix.spinnaker.kato.security.NamedAccountCredentialsHolder
import com.netflix.spinnaker.kato.model.aws.AsgDeploymentNames
import com.netflix.spinnaker.kato.model.aws.AutoScalingGroupOptions
import com.netflix.spinnaker.kato.model.aws.LaunchConfigurationOptions
import com.netflix.spinnaker.kato.model.aws.OperationContext
import org.springframework.beans.factory.annotation.Autowired

class DeploymentActivitiesImpl implements DeploymentActivities {

  ActivityOperations activity = new SwfActivityOperations()

  @Autowired
  AmazonClientProvider amazonClientProvider // TODO: inject to get working quickly and then remove this eventually so that all work happens in operations

  @Autowired
  List<UserDataProvider> userDataProviders

  @Autowired
  NamedAccountCredentialsHolder namedAccountCredentialsHolder

  private AmazonCredentials getCredentials(OperationContext operationContext) {
    (AmazonCredentials) namedAccountCredentialsHolder.getCredentials(operationContext.accountName).credentials
  }

  @Override
  AsgDeploymentNames getAsgDeploymentNames(OperationContext operationContext, String clusterName) {
    def names = Names.parseName(clusterName)
    def autoScaling = amazonClientProvider.getAutoScaling(getCredentials(operationContext), operationContext.region)
    def nameLookUp = new AutoScalingWorker(autoScaling: autoScaling, application: names.app, stack: names.stack)
    def ancestorAsg = nameLookUp.ancestorAsg
    Integer nextSequence
    if (ancestorAsg) {
      Names ancestorNames = Names.parseName(ancestorAsg.autoScalingGroupName as String)
      nextSequence = ancestorNames.sequence + 1
    } else {
      nextSequence = 0
    }
    new AsgDeploymentNames(
      previousAsgName: ancestorAsg.autoScalingGroupName,
      previousLaunchConfigName: ancestorAsg.launchConfigurationName,
      nextAsgName: nameLookUp.getAutoScalingGroupName(nextSequence),
      nextLaunchConfigName: nameLookUp.getLaunchConfigurationName(nextSequence)
    )
  }

  @Override
  LaunchConfigurationOptions constructLaunchConfigForNextAsg(OperationContext operationContext,
      AutoScalingGroupOptions nextAutoScalingGroup, LaunchConfigurationOptions inputs) {
    LaunchConfigurationOptions launchConfiguration = LaunchConfigurationOptions.from(inputs)
    launchConfiguration.launchConfigurationName = nextAutoScalingGroup.launchConfigurationName
    // TODO: include default security groups (for VPC and nonVPC)
    // TODO: inject default IAM profile if non exists?
    launchConfiguration
  }


  @Override
  String createLaunchConfigForNextAsg(OperationContext operationContext,
      AutoScalingGroupOptions autoScalingGroup, LaunchConfigurationOptions launchConfiguration) {
    def userDataBuilder = new AutoScalingWorker(
      userDataProviders: userDataProviders
    )
    launchConfiguration.userData = userDataBuilder.getUserData(autoScalingGroup.autoScalingGroupName, launchConfiguration.launchConfigurationName)
    def operation = new CreateLaunchConfigAtomicOperation(new CreateLaunchConfigDescription(
      credentials: getCredentials(operationContext),
      asgName: autoScalingGroup.autoScalingGroupName,
      regions: [(operationContext.region)],
      launchConfigOptions: launchConfiguration)
    )
    operation.operate([])
    launchConfiguration.launchConfigurationName
  }

  @Override
  String createNextAsgForClusterWithoutInstances(OperationContext operationContext, AutoScalingGroupOptions asgOptions) {
    def operation = new CreateAsgAtomicOperation(new CreateAsgDescription(
      credentials: getCredentials(operationContext),
      asgName: asgOptions.autoScalingGroupName,
      regions: [(operationContext.region)],
      asgOptions: asgOptions)
    )
    operation.operate([])
    asgOptions.autoScalingGroupName
  }

  @Override
  Integer copyScalingPolicies(OperationContext operationContext, AsgDeploymentNames asgDeploymentNames) {
    def autoScaling = amazonClientProvider.getAutoScaling(getCredentials(operationContext), operationContext.region)
    // TODO: copy alarms as well and point scaling policy to them, need AmazonCloudWatch from proivider
    def result = autoScaling.describePolicies(new DescribePoliciesRequest(autoScalingGroupName: asgDeploymentNames.previousAsgName))
    result.scalingPolicies.each {
      def newName = [asgDeploymentNames.nextAsgName, UUID.randomUUID().toString()].join('-')
      def request = new PutScalingPolicyRequest(
        policyName: newName,
        autoScalingGroupName: asgDeploymentNames.nextAsgName,
        scalingAdjustment: it.scalingAdjustment,
        minAdjustmentStep: it.minAdjustmentStep,
        adjustmentType: it.adjustmentType,
        cooldown: it.cooldown
      )
      autoScaling.putScalingPolicy(request)
    }
    result.scalingPolicies.size()
  }

  @Override
  Integer copyScheduledActions(OperationContext operationContext, AsgDeploymentNames asgDeploymentNames) {
    def autoScaling = amazonClientProvider.getAutoScaling(getCredentials(operationContext), operationContext.region)
    def result = autoScaling.describeScheduledActions(new DescribeScheduledActionsRequest(autoScalingGroupName: asgDeploymentNames.previousAsgName))
    result.scheduledUpdateGroupActions.each {
      def newName = [asgDeploymentNames.nextAsgName, UUID.randomUUID().toString()].join('-')
      def request = new PutScheduledUpdateGroupActionRequest(
        scheduledActionName: newName,
        autoScalingGroupName: asgDeploymentNames.nextAsgName,
        time: it.time,
        startTime: it.startTime,
        endTime: it.endTime,
        recurrence: it.recurrence,
        minSize: it.minSize,
        maxSize: it.maxSize,
        desiredCapacity: it.desiredCapacity
      )
      autoScaling.putScheduledUpdateGroupAction(request)
    }
    result.scheduledUpdateGroupActions.size()
  }

  @Override
  void resizeAsg(OperationContext operationContext, String asgName, int min, int desired, int max) {
    def operation = new ResizeAsgAtomicOperation(new ResizeAsgDescription(
      credentials: getCredentials(operationContext),
      asgName: asgName,
      regions: [operationContext.region],
      capacity: new ResizeAsgDescription.Capacity(min: min, desired: desired, max: max)
    ))
    operation.operate([])
  }

  @Override
  String reasonAsgIsNotOperational(OperationContext operationContext, String asgName, int expectedInstanceCount) {
    // TODO: may need to be in Oort rather than Kato
    if (expectedInstanceCount == 0) {
      return ''
    }
    def autoScaling = amazonClientProvider.getAutoScaling(getCredentials(operationContext), operationContext.region)
    def amazonElasticLoadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(getCredentials(operationContext), operationContext.region)
    def result = autoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: [asgName]))
    AutoScalingGroup asg = result.autoScalingGroups ? result.autoScalingGroups[0] : null
    if (!asg) {
      throw new IllegalStateException("ASG '${asgName}' does not exist.")
    }
    if (asg.instances.size() < expectedInstanceCount) {
      return "Instance count is ${asg.instances.size()}. Waiting for ${expectedInstanceCount}."
    }
    if (asg.instances.find { it.lifecycleState != LifecycleState.InService.name() }) {
      return 'Waiting for instances to be in service.'
    }

    // TODO: wait for health in eureka
//    if (configService.getRegionalDiscoveryServer(userContext.region)) {
//      List<ApplicationInstance> applicationInstances = discoveryService.getAppInstancesByIds(userContext,
//        asg.instances*.instanceId)
//      if (applicationInstances.size() < expectedInstanceCount) {
//        return 'Waiting for Eureka data about instances.'
//      }
//      if (applicationInstances.find { it.status != EurekaStatus.UP.name() }) {
//        return 'Waiting for all instances to be available in Eureka.'
//      }
//      if (!awsEc2Service.checkHostsHealth(applicationInstances*.healthCheckUrl)) {
//        return 'Waiting for all instances to pass health checks.'
//      }
//    }

    if (asg.loadBalancerNames) {
      String loadBalancerThatSeesOutOfServiceInstance = asg.loadBalancerNames.find {

        DescribeInstanceHealthRequest request = new DescribeInstanceHealthRequest(loadBalancerName: it,
          instances: asg.instances.collect { new com.amazonaws.services.elasticloadbalancing.model.Instance(instanceId: it.instanceId) })
        List<InstanceState> states = amazonElasticLoadBalancing.describeInstanceHealth(request).instanceStates
        states.find {
          it.state != LifecycleState.InService.name()
        }
      }
      if (loadBalancerThatSeesOutOfServiceInstance) {
        return 'Waiting for all instances to pass ELB health checks.'
      }
    }
    ''
  }

  @Override
  void enableAsg(OperationContext operationContext, String asgName) {
    def operation = new EnableAsgAtomicOperation(new EnableAsgDescription(
      credentials: getCredentials(operationContext),
      asgName: asgName,
      regions: [operationContext.region]
    ))
    operation.operate([])
  }

  @Override
  void disableAsg(OperationContext operationContext, String asgName) {
    def operation = new DisableAsgAtomicOperation(new DisableAsgDescription(
      credentials: getCredentials(operationContext),
      asgName: asgName,
      regions: [operationContext.region]
    ))
    operation.operate([])
  }

  @Override
  void deleteAsg(OperationContext operationContext, String asgName) {
    def operation = new DeleteAsgAtomicOperation(new DeleteAsgDescription(
      credentials: getCredentials(operationContext),
      asgName: asgName,
      regions: [operationContext.region],
      forceDelete: true
    ))
    operation.operate([])
    // TODO: Asgard also deletes old Launch Configs too, maybe should be a separate activity
  }

  @ManualActivityCompletion
  @Override
  Boolean askIfDeploymentShouldProceed(OperationContext operationContext, String notificationDestination,
                                       String asgName, String operationDescription) {
    println "deployment for '$asgName' manual activity token: $activity.taskToken"
    // TODO: store token so that it can be used to manually complete the activity
    // TODO: send notification, maybe from something other than Kato
    true
  }

  @Override
  void sendNotification(OperationContext operationContext, String notificationDestination,
                        String clusterName, String subject, String message) {
    // TODO: send notification, maybe from something other than Kato
  }

}
