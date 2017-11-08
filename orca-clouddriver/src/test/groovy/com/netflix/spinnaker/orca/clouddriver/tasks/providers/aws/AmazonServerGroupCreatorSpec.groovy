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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws

import com.google.common.collect.Maps
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class AmazonServerGroupCreatorSpec extends Specification {

  @Subject
  def creator = new AmazonServerGroupCreator()
  def stage = new Stage(Execution.newPipeline("orca"), "whatever")

  def deployConfig = [
      application      : "hodor",
      amiName          : "hodor-ubuntu-1",
      instanceType     : "large",
      securityGroups   : ["a", "b", "c"],
      availabilityZones: ["us-east-1": ["a", "d"]],
      capacity         : [
          min    : 1,
          max    : 20,
          desired: 5
      ],
      credentials      : "fzlem"
  ]

  def setup() {
    creator.defaultBakeAccount = "test"
    stage.execution.stages.add(stage)
    stage.context = deployConfig
  }

  def cleanup() {
    stage.execution.stages.clear()
    stage.execution.stages.add(stage)
  }

  def "creates a deployment based on job parameters"() {
    given:
      def expected = Maps.newHashMap(deployConfig)
      expected.with {
        keyPair = 'nf-fzlem-keypair-a'
        securityGroups = securityGroups + ['nf-infrastructure', 'nf-datacenter']
      }

    when:
      def operations = creator.getOperations(stage)

    then:
      operations.find {
        it.containsKey("createServerGroup")
      }.createServerGroup == expected
  }

  def "requests an allowLaunch operation for each region"() {
    given:
    stage.context.availabilityZones["us-west-1"] = []

    when:
      def operations = creator.getOperations(stage)

    then:
      with(operations.findAll {
        it.containsKey("allowLaunchDescription")
      }.allowLaunchDescription) { ops ->
        ops.every {
          it instanceof Map
        }
        region == this.deployConfig.availabilityZones.keySet() as List
      }
  }

  def "don't create allowLaunch tasks when in same account"() {
    given:
      creator.defaultBakeAccount = 'fzlem'
    stage.context.availabilityZones["us-west-1"] = []

    when:
      def operations = creator.getOperations(stage)

    then:
      operations.findAll { it.containsKey("allowLaunchDescription") }.empty
  }

  def "can include optional parameters"() {
    given:
      stage.context.stack = stackValue
      stage.context.subnetType = subnetTypeValue

    when:
      def operations = creator.getOperations(stage)

    then:
      operations.size() == 2
      operations.find {
        it.containsKey("createServerGroup")
      }.createServerGroup == [
          amiName          : 'hodor-ubuntu-1',
          application      : 'hodor',
          availabilityZones: ['us-east-1': ['a', 'd']],
          capacity         : [min: 1, max: 20, desired: 5],
          credentials      : 'fzlem',
          instanceType     : 'large',
          keyPair          : 'nf-fzlem-keypair-a',
          securityGroups   : ['a', 'b', 'c', 'nf-infrastructure', 'nf-datacenter'],
          stack            : 'the-stack-value',
          subnetType       : 'the-subnet-type-value'
      ]

    where:
      stackValue = "the-stack-value"
      subnetTypeValue = "the-subnet-type-value"
  }

  def "can use the AMI supplied by deployment details"() {
    given:
      stage.context.amiName = null
      stage.context.deploymentDetails = [
          ["ami": "not-my-ami", "region": "us-west-1", cloudProvider: "aws"],
          ["ami": "definitely-not-my-ami", "region": "us-west-2", cloudProvider: "aws"],
          ["ami": amiName, "region": deployConfig.availabilityZones.keySet()[0], cloudProvider: "aws"]
      ]

    when:
      def operations = creator.getOperations(stage)

    then:
      operations.find {
        it.containsKey("createServerGroup")
      }.createServerGroup.amiName == amiName

    where:
      amiName = "ami-name-from-bake"
  }

  def "create deploy task adds imageId if present in deployment details"() {
    given:
    stage.context.credentials = creator.defaultBakeAccount
      stage.context.amiName = null
      stage.context.deploymentDetails = [
          ["imageId": "docker-image-is-not-region-specific", "region": "us-west-1"],
          ["imageId": "docker-image-is-not-region-specific", "region": "us-west-2"],
      ]

    when:
      def operations = creator.getOperations(stage)

    then:
      operations.find {
        it.containsKey("createServerGroup")
      }.createServerGroup.imageId == "docker-image-is-not-region-specific"

    where:
      amiName = "ami-name-from-bake"
  }

  def "create deploy throws an exception if allowLaunch cannot find an ami"() {
    given:
    stage.context.amiName = null

    when:
    creator.getOperations(stage)

    then:
    thrown(IllegalStateException)
  }

  def "create deploy adds an allowLaunch operation if needed"() {
    when:
    def operations = creator.getOperations(stage)

    then:
    operations.size() == 2
    operations[0].containsKey("allowLaunchDescription")
  }

  @Unroll
  def "prefers the deployment details from an upstream stage to one from global context"() {
    given:
    def deployRegion = "us-east-1"
    stage.context.amiName = null
    stage.execution.context.deploymentDetails = [
        ["ami": "not-my-ami", "region": deployRegion, cloudProvider: "aws"],
        ["ami": "also-not-my-ami", "region": deployRegion, cloudProvider: "aws"]
    ]

    and:
    def findImageStage =
      new Stage(stage.execution, "findImage", [regions: [deployRegion], amiDetails: [[ami: amiName]], cloudProvider: cloudProvider])
    findImageStage.id = UUID.randomUUID()
    findImageStage.refId = "1a"
    stage.execution.stages << findImageStage

    def intermediateStage = new Stage(stage.execution, "whatever")
    intermediateStage.id = UUID.randomUUID()
    intermediateStage.refId = "1b"
    stage.execution.stages << intermediateStage

    and:
    intermediateStage.requisiteStageRefIds = [findImageStage.refId]
    stage.requisiteStageRefIds = [intermediateStage.refId]

    when:
    def operations = creator.getOperations(stage)

    then:
    operations.find {
      it.containsKey("createServerGroup")
    }.createServerGroup.amiName == amiName

    where:
    amiName                    | cloudProvider
    "ami-name-from-find-image" | "aws"
    "ami-name-from-find-image" | null
  }
}
