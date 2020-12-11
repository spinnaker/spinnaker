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

import com.amazonaws.services.ecs.model.CapacityProviderStrategyItem
import com.amazonaws.services.ecs.model.PlacementStrategy
import com.amazonaws.services.ecs.model.PlacementStrategyType
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.AbstractECSDescription
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CreateServerGroupDescription
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.moniker.Moniker

class EcsCreateServergroupDescriptionValidatorSpec extends AbstractValidatorSpec {

  void 'should fail when the capacity is null'() {
    given:
    def description = (CreateServerGroupDescription) getDescription()
    description.capacity = null
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity', "${getDescriptionName()}.capacity.not.nullable")
  }

  void 'should fail when desired is greater than max'() {
    given:
    def description = (CreateServerGroupDescription) getDescription()
    description.capacity.setDesired(9001)
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity.desired', "${getDescriptionName()}.capacity.desired.exceeds.max")
  }

  void 'should fail when desired is less than min'() {
    given:
    def description = (CreateServerGroupDescription) getDescription()
    description.capacity.setDesired(0)
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity.desired', "${getDescriptionName()}.capacity.desired.less.than.min")
  }

  void 'should fail when more than one region is present'() {
    given:
    def description = (CreateServerGroupDescription) getDescription()
    description.availabilityZones = ['us-west-1': ['us-west-1a'], 'us-west-2': ['us-west-2a']]
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('availabilityZones', "${getDescriptionName()}.availabilityZones.must.have.only.one")
  }

  void 'should fail when no availability zones are present'() {
    given:
    def description = (CreateServerGroupDescription) getDescription()
    description.availabilityZones = ['us-west-1': []]
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('availabilityZones.zones', "${getDescriptionName()}.availabilityZones.zones.not.nullable")
  }

  void 'should fail when environment variables contain reserved key'() {
    given:
    def description = (CreateServerGroupDescription) getDescription()
    description.environmentVariables = ['SERVER_GROUP':'invalid', 'tag_1':'valid_tag']
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('environmentVariables', "${getDescriptionName()}.environmentVariables.invalid")
  }

  void 'should pass with correct environment variables'() {
    given:
    def description = getDescription()
    description.environmentVariables = ['TAG_1':'valid_tag_1', 'TAG_2':'valid_tag_2']
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  void 'should pass without load balancer'() {
    given:
    def description = getDescription()
    description.containerPort = null
    description.targetGroup = null
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  void '(with artifact) should fail when load balancer specified but loadBalanced container missing'() {
    given:
    def description = getDescription()
    description.useTaskDefinitionArtifact = true
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('loadBalancedContainer', "${getDescriptionName()}.loadBalancedContainer.not.nullable")
  }

  void '(with artifact) should fail when load balanced container is specified but load balancer is missing'() {
    given:
    def description = getDescription()
    description.targetGroup = null
    description.loadBalancedContainer = 'load-balanced-container'
    description.useTaskDefinitionArtifact = true
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('targetGroup', "${getDescriptionName()}.targetGroup.not.nullable")
  }

  void 'target group mappings should fail when load balancer specified but container name is missing'() {
    given:
    def targetGroupMappings = new CreateServerGroupDescription.TargetGroupProperties(
      containerName: null,
      containerPort: 1337,
      targetGroup: 'target-group-arn'
    )
    def description = getDescription()
    description.targetGroup = null
    description.containerPort = null
    description.dockerImageAddress = null
    description.useTaskDefinitionArtifact = true
    description.targetGroupMappings = [targetGroupMappings]
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('targetGroupMappings.containerName', "${getDescriptionName()}.targetGroupMappings.containerName.not.nullable")
  }

  void 'should fail when launch type and capacity provider strategy are both defined'() {
    given:
    def capacityProviderStrategy = new CapacityProviderStrategyItem(
      capacityProvider: 'FARGATE',
      weight: 1
    )
    def description = getDescription()
    description.capacityProviderStrategy = [capacityProviderStrategy]
    description.launchType = 'FARGATE'
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('launchType', 'createServerGroupDescription.launchType.invalid', 'LaunchType cannot be specified when CapacityProviderStrategy are specified.')
  }

  void 'should fail when neither launch type or capacity provider strategy are defined'() {
    given:
    def description = getDescription()
    description.capacityProviderStrategy = null
    description.launchType = null
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('launchType', 'createServerGroupDescription.launchType.invalid', 'LaunchType or CapacityProviderStrategy must be specified.')
  }

  void 'target group mappings should fail when container name is specified but load balancer is missing'() {
    given:
    def targetGroupMappings = new CreateServerGroupDescription.TargetGroupProperties(
      containerName: 'test-container',
      containerPort: 1337,
      targetGroup: null
    )
    def description = getDescription()
    description.targetGroup = null
    description.containerPort = null
    description.dockerImageAddress = null
    description.useTaskDefinitionArtifact = true
    description.targetGroupMappings = [targetGroupMappings]
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('targetGroupMappings.targetGroup', "${getDescriptionName()}.targetGroupMappings.targetGroup.not.nullable")
  }

  void 'target group mappings should fail when container port is invalid'() {
    given:
    def targetGroupMappings = new CreateServerGroupDescription.TargetGroupProperties(
      containerName: null,
      containerPort: -1,
      targetGroup: 'target-group-arn'
    )
    def description = getDescription()
    description.targetGroup = null
    description.containerPort = null
    description.targetGroupMappings = [targetGroupMappings]
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('targetGroupMappings.containerPort', "${getDescriptionName()}.targetGroupMappings.containerPort.invalid")
  }

  void 'target group mappings should fail when container port is missing'() {
    given:
    def targetGroupMappings = new CreateServerGroupDescription.TargetGroupProperties(
      containerName: null,
      containerPort: null,
      targetGroup: 'target-group-arn'
    )
    def description = getDescription()
    description.targetGroup = null
    description.containerPort = null
    description.targetGroupMappings = [targetGroupMappings]
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('targetGroupMappings.containerPort', "${getDescriptionName()}.targetGroupMappings.containerPort.not.nullable")
  }

  void 'target group mappings should pass without load balancer if using container inputs'() {
    given:
    def targetGroupMappings = new CreateServerGroupDescription.TargetGroupProperties(
      containerName: null,
      containerPort: 1337,
      targetGroup: 'target-group-arn'
    )
    def description = getDescription()
    description.targetGroup = null
    description.containerPort = null
    description.targetGroupMappings = [targetGroupMappings]
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  void 'application must be set if moniker is null'() {
    given:
    def description = getDescription()
    description.application = null
    description.moniker = null
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('application', "${getDescriptionName()}.application.not.nullable")
  }

  void 'moniker application cannot be null'() {
    given:
    def description = getDescription()
    description.application = "foo"
    description.moniker.app = null
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('moniker.app', "${getDescriptionName()}.moniker.app.not.nullable")
  }

  void 'application can be null if moniker is set'() {
    given:
    def description = getDescription()
    description.application = null
    description.moniker.app = "foo"
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  void 'moniker can be null if application is set'() {
    given:
    def description = getDescription()
    description.application = "foo"
    description.moniker = null
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  void 'both app and moniker should match if both are set'() {
    given:
    def description = getDescription()
    description.application = "foo"
    description.freeFormDetails = "detail"
    description.stack = "stack"
    description.moniker.app = "bar"
    description.moniker.detail = "wrongdetail"
    description.moniker.stack = "wrongstack"
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('moniker.app', "${getDescriptionName()}.moniker.app.invalid")
    1 * errors.rejectValue('moniker.detail', "${getDescriptionName()}.moniker.detail.invalid")
    1 * errors.rejectValue('moniker.stack', "${getDescriptionName()}.moniker.stack.invalid")
  }

  @Override
  AbstractECSDescription getNulledDescription() {
    def description = (CreateServerGroupDescription) getDescription()
    description.placementStrategySequence = null
    description.availabilityZones = null
    description.ecsClusterName = null
    description.dockerImageAddress = null
    description.credentials = null
    description.containerPort = null
    description.computeUnits = null
    description.reservedMemory = null
    description.capacity.setDesired(null)
    description.capacity.setMin(null)
    description.capacity.setMax(null)
    description.moniker.app = null;
    return description
  }

  @Override
  Set<String> notNullableProperties() {
    ['placementStrategySequence', 'availabilityZones', 'moniker.app',
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

    description.moniker = Moniker.builder().app('my-app').build();
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
    description.placementStrategySequence = [new PlacementStrategy().withType(PlacementStrategyType.Random)]

    description
  }

  @Override
  def setTestRegion() {
    //Region testing is not done in the same way as normal description.
    testRegion = false
  }
}
