/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.validators

import com.amazonaws.services.ecs.model.PlacementStrategy
import com.amazonaws.services.ecs.model.PlacementStrategyType
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.AbstractECSDescription
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CreateServerGroupDescription
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import org.springframework.validation.Errors

class EcsCreateServergroupDescriptionValidatorSpec extends AbstractValidatorSpec {

  void 'should fail when the capacity is null'() {
    given:
    def description = (CreateServerGroupDescription) getDescription()
    description.capacity = null
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity', "${getDescriptionName()}.capacity.not.nullable")
  }

  void 'should fail when desired is greater than max'() {
    given:
    def description = (CreateServerGroupDescription) getDescription()
    description.capacity.setDesired(9001)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity.desired', "${getDescriptionName()}.capacity.desired.exceeds.max")
  }

  void 'should fail when desired is less than min'() {
    given:
    def description = (CreateServerGroupDescription) getDescription()
    description.capacity.setDesired(0)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity.desired', "${getDescriptionName()}.capacity.desired.less.than.min")
  }

  void 'should fail when more than one availability zones is present'() {
    given:
    def description = (CreateServerGroupDescription) getDescription()
    description.availabilityZones = ['us-west-1': ['us-west-1a'], 'us-west-2': ['us-west-2a']]
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('availabilityZones', "${getDescriptionName()}.availabilityZones.must.have.only.one")
  }

  void 'should fail when environment variables contain reserved key'() {
    given:
    def description = (CreateServerGroupDescription) getDescription()
    description.environmentVariables = ['SERVER_GROUP':'invalid', 'tag_1':'valid_tag']
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('environmentVariables', "${getDescriptionName()}.environmentVariables.invalid")
  }

  void 'should pass with correct environment variables'() {
    given:
    def description = getDescription()
    description.environmentVariables = ['TAG_1':'valid_tag_1', 'TAG_2':'valid_tag_2']
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }


  @Override
  AbstractECSDescription getNulledDescription() {
    def description = (CreateServerGroupDescription) getDescription()
    description.placementStrategySequence = null
    description.availabilityZones = null
    description.autoscalingPolicies = null
    description.application = null
    description.ecsClusterName = null
    description.dockerImageAddress = null
    description.credentials = null
    description.containerPort = null
    description.computeUnits = null
    description.reservedMemory = null
    description.capacity.setDesired(null)
    description.capacity.setMin(null)
    description.capacity.setMax(null)
    return description
  }

  @Override
  Set<String> notNullableProperties() {
    ['placementStrategySequence', 'availabilityZones', 'autoscalingPolicies', 'application',
     'ecsClusterName', 'dockerImageAddress', 'credentials', 'containerPort', 'computeUnits',
     'reservedMemory', 'capacity.desired', 'capacity.min', 'capacity.max']
  }

  @Override
  AbstractECSDescription getInvalidDescription() {
    def description = (CreateServerGroupDescription) getDescription()
    description.reservedMemory = -1
    description.computeUnits = -1
    description.containerPort = -1
    description.getCapacity().setDesired(-1)
    description.getCapacity().setMax(-2)
    description.getCapacity().setMin(-1)
    description.placementStrategySequence = [
      new PlacementStrategy().withType("invalid-type"),
      new PlacementStrategy().withType(PlacementStrategyType.Binpack).withField("invalid"),
      new PlacementStrategy().withType(PlacementStrategyType.Spread).withField("invalid")
    ]
    return description
  }

  @Override
  Set<String> invalidProperties() {
    ['reservedMemory', 'computeUnits', 'containerPort', 'placementStrategySequence.binpack',
     'placementStrategySequence.type', 'capacity.desired', 'placementStrategySequence.spread',
     'capacity.min', 'capacity.max', 'capacity.min.max.range']
  }

  @Override
  DescriptionValidator getDescriptionValidator() {
    new EcsCreateServerGroupDescriptionValidator()
  }

  @Override
  String getDescriptionName() {
    'createServerGroupDescription'
  }

  @Override
  AbstractECSDescription getDescription() {
    def description = new CreateServerGroupDescription()
    description.credentials = TestCredential.named('test')
    description.region = 'us-west-1'

    description.application = 'my-app'
    description.ecsClusterName = 'mycluster'
    description.iamRole = 'iam-role-arn'
    description.containerPort = 1337
    description.targetGroup = 'target-group-arn'
    description.securityGroupNames = ['sg-deadbeef']
    description.portProtocol = 'tcp'
    description.computeUnits = 256
    description.reservedMemory = 512
    description.dockerImageAddress = 'docker-image-url'
    description.capacity = new ServerGroup.Capacity(1, 2, 1)
    description.availabilityZones = ['us-west-1': ['us-west-1a']]
    description.autoscalingPolicies = []
    description.placementStrategySequence = [new PlacementStrategy().withType(PlacementStrategyType.Random)]

    description
  }

  @Override
  def setTestRegion() {
    //Region testing is not done in the same way as normal description.
    testRegion = false
  }
}
