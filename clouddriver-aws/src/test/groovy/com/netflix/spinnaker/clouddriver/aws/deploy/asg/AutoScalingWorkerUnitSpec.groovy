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

package com.netflix.spinnaker.clouddriver.aws.deploy.asg

import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.asgbuilders.AsgWithLaunchConfigurationBuilder
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.asgbuilders.AsgWithLaunchTemplateBuilder
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.asgbuilders.AsgWithMixedInstancesPolicyBuilder
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataOverride
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification
import spock.lang.Unroll

class AutoScalingWorkerUnitSpec extends Specification {

  @Autowired
  TaskRepository taskRepository

  def awsServerGroupNameResolver = Mock(AWSServerGroupNameResolver)
  def regionScopedProvider = Stub(RegionScopedProviderFactory.RegionScopedProvider) {
    getAWSServerGroupNameResolver() >> awsServerGroupNameResolver
  }
  def launchTemplateRollOutConfig = Mock(LaunchTemplateRollOutConfig)

  def userDataOverride = new UserDataOverride()
  def credential = TestCredential.named('foo')
  def task = new DefaultTask("task")
  def taskPhase = "AWS_DEPLOY"

  def setup() {
    TaskRepository.threadLocalTask.set(task)
  }

  @Unroll
  void "deploy workflow creates asg backed by launch config"() {
    setup:
    def autoScalingWorker = new AutoScalingWorker(regionScopedProvider, launchTemplateRollOutConfig)
    def asgConfig = AutoScalingWorker.AsgConfiguration.builder()
      .application("myasg")
      .stack("stack")
      .freeFormDetails("details")
      .credentials(credential)
      .sequence(sequence)
      .userDataOverride(userDataOverride)
      .ignoreSequence(ignoreSequence)
      .build()
    and:
    def asgBuilder = Mock(AsgWithLaunchConfigurationBuilder)
    regionScopedProvider.getAsgBuilderForLaunchConfiguration() >> asgBuilder

    when:
    autoScalingWorker.deploy(asgConfig)

    then:
    if (sequence) {
      awsServerGroupNameResolver.generateServerGroupName('myasg', 'stack', 'details', sequence, ignoreSequence) >> expectedAsgName
    } else {
      awsServerGroupNameResolver.resolveNextServerGroupName('myasg', 'stack', 'details', ignoreSequence) >> expectedAsgName
    }
    0 * awsServerGroupNameResolver._

    and:
    1 * asgBuilder.build(task, taskPhase, expectedAsgName, asgConfig)
    0 * asgBuilder._

    where:
    sequence || expectedAsgName              || ignoreSequence
    null     || "myasg-stack-details-v000"   ||  true
    null     || "myasg-stack-details"        ||  false
    0        || "myasg-stack-details-v000"   ||  false
    1        || "myasg-stack-details-v001"   ||  false
    11       || "myasg-stack-details-v011"   ||  false
    111      || "myasg-stack-details-v111"   ||  false
  }

  @Unroll
  void "deploy workflow creates asg backed by launch template if enabled"() {
    setup:
    def autoScalingWorker = new AutoScalingWorker(regionScopedProvider, launchTemplateRollOutConfig)
    def asgConfig = AutoScalingWorker.AsgConfiguration.builder()
      .application("myasg")
      .stack("stack")
      .region("us-east-1")
      .freeFormDetails("details")
      .credentials(credential)
      .sequence(sequence)
      .setLaunchTemplate(true)
      .ignoreSequence(ignoreSequence)
      .build()

    and:
    def asgBuilder = Mock(AsgWithLaunchTemplateBuilder)
    regionScopedProvider.getAsgBuilderForLaunchTemplate() >> asgBuilder

    when:
    autoScalingWorker.deploy(asgConfig)

    then:
    1 * launchTemplateRollOutConfig.shouldUseLaunchTemplateForReq("myasg", credential, "us-east-1") >> true

    and:
    if (sequence) {
      awsServerGroupNameResolver.generateServerGroupName('myasg', 'stack', 'details', sequence, ignoreSequence) >> expectedAsgName
    } else {
      awsServerGroupNameResolver.resolveNextServerGroupName('myasg', 'stack', 'details', ignoreSequence) >> expectedAsgName
    }
    0 * awsServerGroupNameResolver._

    and:
    1 * asgBuilder.build(task, taskPhase, expectedAsgName, asgConfig)
    0 * asgBuilder._

    where:
    sequence || expectedAsgName              || ignoreSequence
    null     || "myasg-stack-details-v000"   ||  true
    null     || "myasg-stack-details"        ||  false
    0        || "myasg-stack-details-v000"   ||  false
    1        || "myasg-stack-details-v001"   ||  false
    11       || "myasg-stack-details-v011"   ||  false
    111      || "myasg-stack-details-v111"   ||  false
  }

  @Unroll
  void "deploy workflow creates asg backed by mixed instances policy if certain fields are set"() {
    setup:
    def autoScalingWorker = new AutoScalingWorker(regionScopedProvider, launchTemplateRollOutConfig)
    def asgConfig = AutoScalingWorker.AsgConfiguration.builder()
      .application("myasg")
      .stack("stack")
      .region("us-east-1")
      .freeFormDetails("details")
      .credentials(credential)
      .sequence(1)
      .setLaunchTemplate(true)
      .ignoreSequence(false)
      .build()
    asgConfig."$mipFieldName" = mipFieldValue

    and:
    def asgBuilder = Mock(AsgWithMixedInstancesPolicyBuilder)
    regionScopedProvider.getAsgBuilderForMixedInstancesPolicy() >> asgBuilder

    when:
    autoScalingWorker.deploy(asgConfig)

    then:
    1 * launchTemplateRollOutConfig.shouldUseLaunchTemplateForReq('myasg', credential, 'us-east-1') >> true

    and:
    awsServerGroupNameResolver.generateServerGroupName('myasg', 'stack', 'details', 1, false) >> "myasg-stack-details-v001"

    and:
    1 * asgBuilder.build(task, taskPhase, "myasg-stack-details-v001", asgConfig)
    0 * asgBuilder._

    where:
           mipFieldName                      | mipFieldValue
    "onDemandBaseCapacity"                   |    1
    "onDemandPercentageAboveBaseCapacity"    |    50
    "spotAllocationStrategy"                 |"lowest-price"
    "spotAllocationStrategy"                 |"capacity-optimized"
    "spotInstancePools"                      |      3
    "launchTemplateOverridesForInstanceType" |[new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(instanceType: "t.test", weightedCapacity: 2)]
  }
}
