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

import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.asgbuilders.AsgWithLaunchConfigurationBuilder
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.asgbuilders.AsgWithLaunchTemplateBuilder
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataOverride
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
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
  def dynamicConfigService = Mock(DynamicConfigService)

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
    def autoScalingWorker = new AutoScalingWorker(regionScopedProvider, dynamicConfigService)
    def asgConfig = new AutoScalingWorker.AsgConfiguration(
            application: "myasg",
            stack: "stack",
            freeFormDetails: "details",
            credentials: credential,
            sequence: sequence,
            userDataOverride: userDataOverride,
            ignoreSequence: ignoreSequence)
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
    def autoScalingWorker = new AutoScalingWorker(regionScopedProvider, dynamicConfigService)
    def asgConfig = new AutoScalingWorker.AsgConfiguration(
            application: "myasg",
            stack: "stack",
            region: "us-east-1",
            freeFormDetails: "details",
            credentials: credential,
            sequence: sequence,
            setLaunchTemplate: true,
            ignoreSequence: ignoreSequence)

    and:
    def asgBuilder = Mock(AsgWithLaunchTemplateBuilder)
    regionScopedProvider.getAsgBuilderForLaunchTemplate() >> asgBuilder

    when:
    autoScalingWorker.deploy(asgConfig)

    then:
    1 * dynamicConfigService.isEnabled('aws.features.launch-templates', false) >> true
    1 * dynamicConfigService.isEnabled('aws.features.launch-templates.all-applications', false) >> false
    1 * dynamicConfigService.getConfig(String.class, "aws.features.launch-templates.excluded-accounts", "") >> ""
    0 * dynamicConfigService.getConfig(String.class, "aws.features.launch-templates.allowed-accounts", "") >> ""
    1 * dynamicConfigService.getConfig(String.class,"aws.features.launch-templates.excluded-applications", "") >> ""
    1 * dynamicConfigService.getConfig(String.class,"aws.features.launch-templates.allowed-applications", "") >> { "myasg:foo:us-east-1" }
    1 * dynamicConfigService.getConfig(Boolean.class, 'aws.features.launch-templates.ipv6.foo', false) >> false
    0 * dynamicConfigService._

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
  void "should check if current app, account and region match launch template flag"() {
    when:
    def result = AutoScalingWorker.matchesAppAccountAndRegion(application, accountName, region, applicationAccountRegions)

    then:
    result == matches

    where:
    applicationAccountRegions           | application   | accountName | region      || matches
    "foo:test:us-east-1"                | "foo"         | "test"      | "us-east-1" || true
    "foo:test:us-east-1,us-west-2"      | "foo"         | "test"      | "eu-west-1" || false
    "foo:prod:us-east-1"                | "foo"         | "test"      | "us-east-1" || false
  }
}
