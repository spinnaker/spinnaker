/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops

import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider
import com.netflix.spinnaker.clouddriver.ecs.EcsNativeServiceTag
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CreateServerGroupDescription
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeCreateServerGroupDescription
import com.netflix.spinnaker.clouddriver.ecs.names.EcsDefaultNamer
import com.netflix.spinnaker.clouddriver.ecs.names.EcsResource
import com.netflix.spinnaker.clouddriver.ecs.names.EcsServerGroupName
import com.netflix.spinnaker.clouddriver.ecs.names.EcsTagNamer
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import software.amazon.awssdk.services.ecs.model.CapacityProviderStrategyItem
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest
import software.amazon.awssdk.services.ecs.model.DeploymentConfiguration
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse
import software.amazon.awssdk.services.ecs.model.LoadBalancer
import software.amazon.awssdk.services.ecs.model.NetworkConfiguration
import software.amazon.awssdk.services.ecs.model.PlacementConstraint
import software.amazon.awssdk.services.ecs.model.PlacementStrategy
import software.amazon.awssdk.services.ecs.model.Service
import software.amazon.awssdk.services.ecs.model.ServiceRegistry
import software.amazon.awssdk.services.ecs.model.TaskDefinition
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup
import spock.lang.Unroll

class EcsNativeCreateServerGroupAtomicOperationSpec extends CommonAtomicOperation {

  def 'should apply configurable deployment configuration and circuit-breaker rollback'() {
    given:
    def description = Mock(EcsNativeCreateServerGroupDescription)
    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTargetGroup() >> null
    description.getMinimumHealthyPercent() >> 50
    description.getMaximumPercent() >> 150
    description.getEnableDeploymentCircuitBreaker() >> true
    description.getDeploymentCircuitBreakerRollback() >> true

    def operation = new EcsNativeCreateServerGroupAtomicOperation(description)

    when:
    CreateServiceRequest request = operation.makeServiceRequest('task-def-arn',
        new EcsServerGroupName('mygreatapp-stack1-details2-v011'),
        1, new EcsDefaultNamer(), true)

    then:
    request.deploymentConfiguration().minimumHealthyPercent() == 50
    request.deploymentConfiguration().maximumPercent() == 150
    request.deploymentConfiguration().deploymentCircuitBreaker().enable() == true
    request.deploymentConfiguration().deploymentCircuitBreaker().rollback() == true
    request.tags().any { it.key() == EcsNativeServiceTag.KEY && it.value() == EcsNativeServiceTag.VALUE }
  }

  def 'a supplied conflicting ownership tag is replaced by exactly one canonical tag and unrelated tags survive'() {
    given:
    def description = Mock(EcsNativeCreateServerGroupDescription)
    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTargetGroup() >> null
    description.getTags() >> [(EcsNativeServiceTag.KEY): 'external', 'team': 'x']

    def operation = new EcsNativeCreateServerGroupAtomicOperation(description)

    when:
    CreateServiceRequest request = operation.makeServiceRequest('task-def-arn',
        new EcsServerGroupName('mygreatapp-stack1-details2-v011'),
        1, new EcsDefaultNamer(), true)

    then:
    request.tags().findAll { it.key() == EcsNativeServiceTag.KEY }*.value() == [EcsNativeServiceTag.VALUE]
    request.tags().any { it.key() == 'team' && it.value() == 'x' }
  }

  def 'should preserve the original ecs defaults when native fields are unset'() {
    given:
    def description = Mock(EcsNativeCreateServerGroupDescription)
    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTargetGroup() >> null
    description.getMinimumHealthyPercent() >> null
    description.getMaximumPercent() >> null
    description.getEnableDeploymentCircuitBreaker() >> false
    description.getDeploymentCircuitBreakerRollback() >> false

    def operation = new EcsNativeCreateServerGroupAtomicOperation(description)

    when:
    CreateServiceRequest request = operation.makeServiceRequest('task-def-arn',
        new EcsServerGroupName('mygreatapp-stack1-details2-v011'),
        1, new EcsDefaultNamer(), true)

    then:
    request.deploymentConfiguration().minimumHealthyPercent() == 100
    request.deploymentConfiguration().maximumPercent() == 200
    request.deploymentConfiguration().deploymentCircuitBreaker().enable() == false
    request.deploymentConfiguration().deploymentCircuitBreaker().rollback() == false
  }

  def 'should send deployment alarms when alarm names are configured'() {
    given:
    def description = Mock(EcsNativeCreateServerGroupDescription)
    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTargetGroup() >> null
    description.getAlarmNames() >> ['myapp-high-error-rate']
    description.isDeploymentAlarmsRollback() >> true

    def operation = new EcsNativeCreateServerGroupAtomicOperation(description)

    when:
    CreateServiceRequest request = operation.makeServiceRequest('task-def-arn',
        new EcsServerGroupName('mygreatapp-stack1-details2-v011'),
        1, new EcsDefaultNamer(), true)

    then:
    request.deploymentConfiguration().alarms().alarmNames() == ['myapp-high-error-rate']
    request.deploymentConfiguration().alarms().enable() == true
    request.deploymentConfiguration().alarms().rollback() == true
  }

  def 'should not send deployment alarms when none are configured'() {
    given:
    def description = Mock(EcsNativeCreateServerGroupDescription)
    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTargetGroup() >> null

    def operation = new EcsNativeCreateServerGroupAtomicOperation(description)

    when:
    CreateServiceRequest request = operation.makeServiceRequest('task-def-arn',
        new EcsServerGroupName('mygreatapp-stack1-details2-v011'),
        1, new EcsDefaultNamer(), true)

    then:
    request.deploymentConfiguration().alarms() == null
  }

  def 'should apply blue/green strategy and bake time to deployment configuration'() {
    given:
    def description = Mock(EcsNativeCreateServerGroupDescription)
    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTargetGroup() >> null
    description.getDeploymentStrategy() >> 'BLUE_GREEN'
    description.getBakeTimeInMinutes() >> 15

    def operation = new EcsNativeCreateServerGroupAtomicOperation(description)

    when:
    CreateServiceRequest request = operation.makeServiceRequest('task-def-arn',
        new EcsServerGroupName('mygreatapp-stack1-details2-v011'),
        1, new EcsDefaultNamer(), true)

    then:
    request.deploymentConfiguration().strategyAsString() == 'BLUE_GREEN'
    request.deploymentConfiguration().bakeTimeInMinutes() == 15
  }

  def 'should attach the blue/green ALB traffic-shift config to the single target-group mapping'() {
    given:
    def loadBalancingV2 = Mock(ElasticLoadBalancingV2Client)
    loadBalancingV2.describeTargetGroups(_) >> DescribeTargetGroupsResponse.builder()
        .targetGroups(TargetGroup.builder().targetGroupArn('arn:target-group').build())
        .build()

    def description = Mock(EcsNativeCreateServerGroupDescription)
    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTargetGroup() >> 'my-target-group'
    description.getContainerPort() >> 80
    description.getAvailabilityZones() >> ['us-west-1': ['us-west-1a']]
    description.getAlternateTargetGroupArn() >> 'arn:alternate-target-group'
    description.getProductionListenerRule() >> 'arn:production-rule'
    description.getTestListenerRule() >> 'arn:test-rule'
    description.getBlueGreenRoleArn() >> 'arn:aws:iam::123456789012:role/ecsBlueGreenRole'

    def operation = new EcsNativeCreateServerGroupAtomicOperation(description)
    operation.amazonClientProvider = amazonClientProvider
    amazonClientProvider.getElasticLoadBalancingV2Client(_, _) >> loadBalancingV2

    when:
    CreateServiceRequest request = operation.makeServiceRequest('task-def-arn',
        new EcsServerGroupName('mygreatapp-stack1-details2-v011'),
        1, new EcsDefaultNamer(), true)

    then:
    request.loadBalancers().size() == 1
    def advancedConfig = request.loadBalancers().get(0).advancedConfiguration()
    advancedConfig.alternateTargetGroupArn() == 'arn:alternate-target-group'
    advancedConfig.productionListenerRule() == 'arn:production-rule'
    advancedConfig.testListenerRule() == 'arn:test-rule'
    advancedConfig.roleArn() == 'arn:aws:iam::123456789012:role/ecsBlueGreenRole'
  }

  def 'should reject a partially configured blue/green ALB traffic shift'() {
    given:
    def description = Mock(EcsNativeCreateServerGroupDescription)
    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTargetGroup() >> null
    description.getAlternateTargetGroupArn() >> 'arn:alternate-target-group'

    def operation = new EcsNativeCreateServerGroupAtomicOperation(description)

    when:
    operation.makeServiceRequest('task-def-arn',
        new EcsServerGroupName('mygreatapp-stack1-details2-v011'),
        1, new EcsDefaultNamer(), true)

    then:
    thrown(IllegalArgumentException)
  }

  def 'should reject a blue/green ALB traffic shift with no single target-group mapping'() {
    given:
    def description = Mock(EcsNativeCreateServerGroupDescription)
    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTargetGroup() >> null
    description.getAlternateTargetGroupArn() >> 'arn:alternate-target-group'
    description.getProductionListenerRule() >> 'arn:production-rule'
    description.getTestListenerRule() >> 'arn:test-rule'
    description.getBlueGreenRoleArn() >> 'arn:aws:iam::123456789012:role/ecsBlueGreenRole'

    def operation = new EcsNativeCreateServerGroupAtomicOperation(description)

    when:
    operation.makeServiceRequest('task-def-arn',
        new EcsServerGroupName('mygreatapp-stack1-details2-v011'),
        1, new EcsDefaultNamer(), true)

    then:
    thrown(IllegalArgumentException)
  }

  def 'should reject blue/green on a load-balanced create when the ALB traffic-shift fields are missing'() {
    given:
    // BLUE_GREEN + a target group but none of the four ALB fields set. AWS ECS would reject this
    // with a 400 ("advancedConfiguration field is required for all loadBalancers ..."); we fail
    // fast with an actionable message instead.
    def loadBalancingV2 = Mock(ElasticLoadBalancingV2Client)
    loadBalancingV2.describeTargetGroups(_) >> DescribeTargetGroupsResponse.builder()
        .targetGroups(TargetGroup.builder().targetGroupArn('arn:target-group').build())
        .build()

    def description = Mock(EcsNativeCreateServerGroupDescription)
    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTargetGroup() >> 'my-target-group'
    description.getContainerPort() >> 80
    description.getAvailabilityZones() >> ['us-west-1': ['us-west-1a']]
    description.getDeploymentStrategy() >> 'BLUE_GREEN'

    def operation = new EcsNativeCreateServerGroupAtomicOperation(description)
    operation.amazonClientProvider = amazonClientProvider
    amazonClientProvider.getElasticLoadBalancingV2Client(_, _) >> loadBalancingV2

    when:
    operation.makeServiceRequest('task-def-arn',
        new EcsServerGroupName('mygreatapp-stack1-details2-v011'),
        1, new EcsDefaultNamer(), true)

    then:
    def e = thrown(IllegalArgumentException)
    e.message.contains('Blue/Green')
    e.message.contains('alternateTargetGroupArn')
  }

  def 'should allow blue/green on a create with no load balancer (no ALB traffic-shift required)'() {
    given:
    // No target group -> BLUE_GREEN needs no advancedConfiguration, so this must not be rejected.
    def description = Mock(EcsNativeCreateServerGroupDescription)
    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTargetGroup() >> null
    description.getDeploymentStrategy() >> 'BLUE_GREEN'

    def operation = new EcsNativeCreateServerGroupAtomicOperation(description)

    when:
    CreateServiceRequest request = operation.makeServiceRequest('task-def-arn',
        new EcsServerGroupName('mygreatapp-stack1-details2-v011'),
        1, new EcsDefaultNamer(), true)

    then:
    request.deploymentConfiguration().strategyAsString() == 'BLUE_GREEN'
    request.loadBalancers().isEmpty()
  }

  def 'rejects incomplete blue/green ALB traffic-shift configuration'() {
    given:
    // The common ecs-native path: the durable service already exists, so operate() rolls it in
    // place via UpdateService. BLUE_GREEN + a declared target group but no ALB fields must fail
    // before the UpdateService call rather than surfacing as an opaque AWS 400.
    def serviceName = 'mygreatapp-stack1-details2'
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'mygreatapp', stack: 'stack1', freeFormDetails: 'details2',
        ecsClusterName: 'my-cluster',
        deploymentStrategy: 'BLUE_GREEN',
        targetGroup: 'my-target-group',
        availabilityZones: ['us-west-1': ['us-west-1a']])

    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    operation.getAmazonEcsClient() >> ecs
    operation.getCredentials() >> Mock(AmazonCredentials)
    operation.resolveTaskRoleArn(_) >> 'arn:aws:iam::123456789012:role/ecsRole'
    ecs.describeServices(_ as DescribeServicesRequest) >> DescribeServicesResponse.builder()
        .services(Service.builder().serviceName(serviceName).status('ACTIVE').tags(EcsNativeServiceTag.tag()).build())
        .build()

    when:
    operation.operate([])

    then:
    def e = thrown(IllegalArgumentException)
    e.message.contains('advancedConfiguration')
    // Rejected from the description alone: no task-definition revision is registered and no
    // service call is made, so a rejected deploy leaves nothing behind in the account.
    0 * operation.registerTaskDefinition(_, _, _)
    0 * ecs.updateService(_)
  }

  def 'rejects a partial blue/green ALB traffic shift on a redeploy before registering a task definition'() {
    given:
    // A single traffic-shift field with a rolling strategy and no target group: the description is
    // inconsistent regardless of strategy, so it must fail before any AWS write.
    def serviceName = 'mygreatapp-stack1-details2'
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'mygreatapp', stack: 'stack1', freeFormDetails: 'details2',
        ecsClusterName: 'my-cluster',
        deploymentStrategy: 'ROLLING',
        alternateTargetGroupArn: 'arn:alternate-target-group',
        availabilityZones: ['us-west-1': ['us-west-1a']])

    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    operation.getAmazonEcsClient() >> ecs
    operation.getCredentials() >> Mock(AmazonCredentials)
    operation.resolveTaskRoleArn(_) >> 'arn:aws:iam::123456789012:role/ecsRole'
    ecs.describeServices(_ as DescribeServicesRequest) >> DescribeServicesResponse.builder()
        .services(Service.builder().serviceName(serviceName).status('ACTIVE').tags(EcsNativeServiceTag.tag()).build())
        .build()

    when:
    operation.operate([])

    then:
    def e = thrown(IllegalArgumentException)
    e.message.contains('all be set together')
    0 * operation.registerTaskDefinition(_, _, _)
    0 * ecs.updateService(_)
  }

  def 'rejects a complete blue/green ALB traffic shift with two target-group mappings before registering a task definition'() {
    given:
    // ECS allows advancedConfiguration on exactly one load balancer; the count is known from the
    // description, so the deploy must fail before registerTaskDefinition rather than at AWS.
    def serviceName = 'mygreatapp-stack1-details2'
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'mygreatapp', stack: 'stack1', freeFormDetails: 'details2',
        ecsClusterName: 'my-cluster',
        deploymentStrategy: 'BLUE_GREEN',
        targetGroupMappings: [
          new CreateServerGroupDescription.TargetGroupProperties(containerName: 'web', containerPort: 80, targetGroup: 'tg-one'),
          new CreateServerGroupDescription.TargetGroupProperties(containerName: 'web', containerPort: 8080, targetGroup: 'tg-two'),
        ] as Set,
        alternateTargetGroupArn: 'arn:alternate-target-group',
        productionListenerRule: 'arn:production-rule',
        testListenerRule: 'arn:test-rule',
        blueGreenRoleArn: 'arn:aws:iam::123456789012:role/ecsBlueGreenRole',
        availabilityZones: ['us-west-1': ['us-west-1a']])

    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    operation.getAmazonEcsClient() >> ecs
    operation.getCredentials() >> Mock(AmazonCredentials)
    operation.resolveTaskRoleArn(_) >> 'arn:aws:iam::123456789012:role/ecsRole'
    ecs.describeServices(_ as DescribeServicesRequest) >> DescribeServicesResponse.builder()
        .services(Service.builder().serviceName(serviceName).status('ACTIVE').tags(EcsNativeServiceTag.tag()).build())
        .build()

    when:
    operation.operate([])

    then:
    def e = thrown(IllegalArgumentException)
    e.message.contains('exactly one target-group mapping')
    e.message.contains('found 2')
    0 * operation.registerTaskDefinition(_, _, _)
    0 * ecs.updateService(_)
  }

  def 'refuses the initial create before registering a task definition when service tagging is disabled'() {
    given:
    // No existing service -> create path. The ownership tag needs account-level service tagging;
    // without it the deploy must stop before CreateService and before registerTaskDefinition.
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'mygreatapp', stack: 'stack1', freeFormDetails: 'details2',
        account: 'test-account',
        ecsClusterName: 'my-cluster',
        availabilityZones: ['us-west-1': ['us-west-1a']])

    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    operation.getAmazonEcsClient() >> ecs
    operation.getCredentials() >> Mock(AmazonCredentials)
    operation.isTaggingEnabled(ecs) >> false
    ecs.describeServices(_ as DescribeServicesRequest) >> DescribeServicesResponse.builder().build()

    when:
    operation.operate([])

    then:
    def e = thrown(IllegalArgumentException)
    e.message.contains('serviceLongArnFormat')
    e.message.contains('test-account')
    0 * operation.registerTaskDefinition(_, _, _)
    0 * ecs.createService(_)
  }

  def 'allows native blue/green lifecycle controls to reach UpdateService'() {
    given:
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'mygreatapp', stack: 'stack1', freeFormDetails: 'details2',
        ecsClusterName: 'my-cluster', deploymentStrategy: 'BLUE_GREEN',
        availabilityZones: ['us-west-1': ['us-west-1a']])
    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    operation.getAmazonEcsClient() >> ecs
    ecs.describeServices(_ as DescribeServicesRequest) >> DescribeServicesResponse.builder()
        .services(Service.builder().serviceName('mygreatapp-stack1-details2').status('ACTIVE').tags(EcsNativeServiceTag.tag()).build())
        .build()
    operation.getCredentials() >> Mock(AmazonCredentials)
    operation.resolveTaskRoleArn(_) >> 'arn:aws:iam::123456789012:role/ecsRole'
    operation.registerTaskDefinition(ecs, _, _) >> TaskDefinition.builder().taskDefinitionArn('new-task-def-arn').build()
    ecs.updateService(_) >> UpdateServiceResponse.builder()
        .service(Service.builder().serviceName('mygreatapp-stack1-details2').currentServiceDeployment('arn:deployment').build())
        .build()

    when:
    operation.operate([])

    then:
    1 * ecs.updateService({ request ->
      request.deploymentConfiguration().strategyAsString() == 'BLUE_GREEN'
    } as UpdateServiceRequest) >> UpdateServiceResponse.builder()
        .service(Service.builder().serviceName('mygreatapp-stack1-details2').currentServiceDeployment('arn:deployment').build())
        .build()
  }

  def 'allows a stored blue/green strategy when redeploy omits the strategy'() {
    given:
    def serviceName = 'mygreatapp-stack1-details2'
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'mygreatapp', stack: 'stack1', freeFormDetails: 'details2',
        ecsClusterName: 'my-cluster', account: 'test',
        availabilityZones: ['us-west-1': ['us-west-1a']])
    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    operation.getAmazonEcsClient() >> ecs
    operation.getCredentials() >> Mock(AmazonCredentials)
    operation.resolveTaskRoleArn(_) >> 'arn:aws:iam::123456789012:role/ecsRole'
    operation.registerTaskDefinition(ecs, _, _) >> TaskDefinition.builder().taskDefinitionArn('new-task-def-arn').build()
    ecs.describeServices(_ as DescribeServicesRequest) >> DescribeServicesResponse.builder()
        .services(Service.builder().serviceName(serviceName).status('ACTIVE').tags(EcsNativeServiceTag.tag())
            .deploymentConfiguration(DeploymentConfiguration.builder().strategy('BLUE_GREEN').build()).build())
        .build()

    when:
    operation.operate([])

    then:
    // The stored strategy must survive: either no deploymentConfiguration is sent (ECS keeps it),
    // or the one sent is derived from the stored BLUE_GREEN configuration.
    1 * ecs.updateService({ UpdateServiceRequest request ->
      request.deploymentConfiguration() == null ||
          request.deploymentConfiguration().strategyAsString() == 'BLUE_GREEN'
    } as UpdateServiceRequest) >> UpdateServiceResponse.builder()
        .service(Service.builder().serviceName(serviceName).currentServiceDeployment('arn:deployment').build()).build()
  }

  def 'explicitly disables ECS Exec on an existing enabled service'() {
    given:
    def serviceName = 'mygreatapp-stack1-details2'
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'mygreatapp', stack: 'stack1', freeFormDetails: 'details2',
        ecsClusterName: 'my-cluster',
        account: 'test',
        enableExecuteCommand: false,
        availabilityZones: ['us-west-1': ['us-west-1a']])
    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    operation.getAmazonEcsClient() >> ecs
    operation.getCredentials() >> Mock(AmazonCredentials)
    operation.resolveTaskRoleArn(_) >> 'arn:aws:iam::123456789012:role/ecsRole'
    operation.registerTaskDefinition(ecs, _, _) >> TaskDefinition.builder().taskDefinitionArn('new-task-def-arn').build()
    ecs.describeServices(_ as DescribeServicesRequest) >> DescribeServicesResponse.builder()
        .services(Service.builder()
            .serviceName(serviceName)
            .status('ACTIVE')
            .tags(EcsNativeServiceTag.tag())
            .enableExecuteCommand(true)
            .build())
        .build()

    when:
    operation.operate([])

    then:
    1 * ecs.updateService({ UpdateServiceRequest req ->
      req.enableExecuteCommand() == false
    } as UpdateServiceRequest) >> UpdateServiceResponse.builder()
        .service(Service.builder().serviceName(serviceName).build())
        .build()
  }

  def 'in-place rolling update leaves the UpdateService load balancers untouched'() {
    given:
    // A plain rolling in-place update must not (re)send loadBalancers -- it only changes the task
    // definition and deployment config, not the service's load-balancer wiring.
    def serviceName = 'mygreatapp-stack1-details2'
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'mygreatapp', stack: 'stack1', freeFormDetails: 'details2',
        ecsClusterName: 'my-cluster',
        deploymentStrategy: 'ROLLING',
        targetGroup: 'my-target-group',
        availabilityZones: ['us-west-1': ['us-west-1a']])

    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    operation.amazonClientProvider = amazonClientProvider
    operation.getAmazonEcsClient() >> ecs
    operation.getCredentials() >> Mock(AmazonCredentials)
    operation.resolveTaskRoleArn(_) >> 'arn:aws:iam::123456789012:role/ecsRole'
    operation.registerTaskDefinition(ecs, _, _) >> TaskDefinition.builder().taskDefinitionArn('new-task-def-arn').build()
    ecs.describeServices(_ as DescribeServicesRequest) >> DescribeServicesResponse.builder()
        .services(Service.builder().serviceName(serviceName).status('ACTIVE').tags(EcsNativeServiceTag.tag()).build())
        .build()

    when:
    operation.operate([])

    then:
    1 * ecs.updateService({ UpdateServiceRequest req ->
      req.service() == serviceName && !req.hasLoadBalancers()
    } as UpdateServiceRequest) >> UpdateServiceResponse.builder()
        .service(Service.builder().serviceName(serviceName).build())
        .build()
    // No ELB lookup happens for a rolling in-place update.
    0 * amazonClientProvider.getElasticLoadBalancingV2Client(_, _)
  }

  def 'resolveExistingServiceName returns the fixed service name when that ECS service already exists'() {
    given:
    // ecs-native computes the fixed (unversioned) name and asks ECS whether it exists.
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'myapp', stack: 'stack', freeFormDetails: 'web', ecsClusterName: 'my-cluster')
    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    operation.getAmazonEcsClient() >> ecs

    when:
    def resolved = operation.resolveExistingServiceName()

    then:
    1 * ecs.describeServices({ DescribeServicesRequest req ->
      req.cluster() == 'my-cluster' && req.services() == ['myapp-stack-web']
    } as DescribeServicesRequest) >> DescribeServicesResponse.builder()
        .services(Service.builder().serviceName('myapp-stack-web').status('ACTIVE').tags(EcsNativeServiceTag.tag()).build())
        .build()
    resolved == 'myapp-stack-web'
  }

  def 'resolveExistingServiceName returns null when the service does not exist (first deploy) or is INACTIVE'() {
    given:
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'myapp', stack: 'stack', freeFormDetails: 'web', ecsClusterName: 'my-cluster')
    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    operation.getAmazonEcsClient() >> ecs

    when:
    def resolved = operation.resolveExistingServiceName()

    then:
    1 * ecs.describeServices(_ as DescribeServicesRequest) >> DescribeServicesResponse.builder()
        .services(services)
        .build()
    resolved == expected

    where:
    services                                                                              || expected
    []                                                                                    || null
    [Service.builder().serviceName('myapp-stack-web').status('INACTIVE').build()]         || null
    [Service.builder().serviceName('myapp-stack-web').status('ACTIVE').tags(EcsNativeServiceTag.tag()).build()]           || 'myapp-stack-web'
  }

  def 'refuses to adopt an untagged fixed-name service'() {
    given:
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'myapp', stack: 'stack', freeFormDetails: 'web', ecsClusterName: 'my-cluster')
    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    operation.getAmazonEcsClient() >> ecs
    ecs.describeServices(_ as DescribeServicesRequest) >> DescribeServicesResponse.builder()
        .services(Service.builder().serviceName('myapp-stack-web').status('ACTIVE').build())
        .build()

    when:
    operation.resolveExistingServiceName()

    then:
    def exception = thrown(IllegalStateException)
    exception.message.contains('not marked as owned by ecs-native')
  }

  @Unroll
  def 'a DRAINING fixed-name service fails the deploy with the reason before any AWS write (tagged=#tagged)'() {
    given:
    // ECS is still deleting the service: it occupies the fixed name but rejects UpdateService.
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'myapp', stack: 'stack', freeFormDetails: 'web', ecsClusterName: 'my-cluster',
        availabilityZones: ['us-west-1': ['us-west-1a']])
    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    operation.getAmazonEcsClient() >> ecs
    operation.getCredentials() >> Mock(AmazonCredentials)
    def draining = Service.builder().serviceName('myapp-stack-web').status('DRAINING')
    if (tagged) {
      draining.tags(EcsNativeServiceTag.tag())
    }
    ecs.describeServices(_ as DescribeServicesRequest) >> DescribeServicesResponse.builder()
        .services(draining.build())
        .build()

    when:
    operation.operate([])

    then:
    def e = thrown(IllegalStateException)
    e.message.contains('myapp-stack-web')
    e.message.contains('my-cluster')
    e.message.contains('DRAINING')
    e.message.contains('wait until ECS has removed it')
    0 * operation.registerTaskDefinition(*_)
    0 * ecs.updateService(_)
    0 * ecs.createService(_)

    where:
    tagged << [true, false]
  }

  def 'operate always rolls the existing durable service in place via UpdateService (no CreateService)'() {
    given:
    def serviceName = 'mygreatapp-stack1-details2'
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'mygreatapp', stack: 'stack1', freeFormDetails: 'details2',
        ecsClusterName: 'my-cluster',
        minimumHealthyPercent: 50,
        maximumPercent: 150,
        enableDeploymentCircuitBreaker: true,
        deploymentCircuitBreakerRollback: true,
        availabilityZones: ['us-west-1': ['us-west-1a']])

    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    operation.getAmazonEcsClient() >> ecs
    operation.getCredentials() >> Mock(AmazonCredentials)
    operation.resolveTaskRoleArn(_) >> 'arn:aws:iam::123456789012:role/ecsRole'

    when:
    def result = operation.operate([])

    then: 'the fixed-name service is looked up first (it already exists -> in-place update)'
    1 * ecs.describeServices(_ as DescribeServicesRequest) >> DescribeServicesResponse.builder()
        .services(Service.builder().serviceName(serviceName).status('ACTIVE').tags(EcsNativeServiceTag.tag()).build())
        .build()

    then: 'only then is a task-definition revision registered'
    1 * operation.registerTaskDefinition(ecs, _, _) >> TaskDefinition.builder().taskDefinitionArn('new-task-def-arn').build()

    then: 'and the service is rolled in place with it'
    1 * ecs.updateService({ UpdateServiceRequest req ->
      req.cluster() == 'my-cluster' &&
          req.service() == serviceName &&
          req.taskDefinition() == 'new-task-def-arn' &&
          req.forceNewDeployment() == true &&
          req.deploymentConfiguration().minimumHealthyPercent() == 50 &&
          req.deploymentConfiguration().maximumPercent() == 150 &&
          req.deploymentConfiguration().deploymentCircuitBreaker().enable() == true &&
          req.deploymentConfiguration().deploymentCircuitBreaker().rollback() == true
    } as UpdateServiceRequest) >> UpdateServiceResponse.builder()
        .service(Service.builder().serviceName(serviceName).build())
        .build()
    0 * ecs.createService(_)
    result.serverGroupNameByRegion == ['us-west-1': serviceName]
  }

  def 'preserves existing service shape on a sparse in-place redeploy'() {
    given:
    def serviceName = 'mygreatapp-stack1-details2'
    def existingNetwork = NetworkConfiguration.builder().build()
    def existingRegistries = [ServiceRegistry.builder().registryArn('arn:registry').build()]
    def existingConstraints = [PlacementConstraint.builder().type('distinctInstance').build()]
    def existingStrategy = [PlacementStrategy.builder().type('spread').field('instanceId').build()]
    def existingCapacityProviders = [CapacityProviderStrategyItem.builder().capacityProvider('FARGATE').weight(1).build()]
    def existingLoadBalancers = [LoadBalancer.builder().targetGroupArn('arn:target-group').containerName('app').containerPort(8080).build()]
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'mygreatapp', stack: 'stack1', freeFormDetails: 'details2',
        account: 'test',
        ecsClusterName: 'my-cluster',
        availabilityZones: ['us-west-1': ['us-west-1a']],
        capacity: new ServerGroup.Capacity(1, 2, 3))
    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    def updateRequest
    operation.getAmazonEcsClient() >> ecs
    operation.getCredentials() >> Mock(AmazonCredentials)
    operation.resolveTaskRoleArn(_) >> 'arn:aws:iam::123456789012:role/ecsRole'
    operation.registerTaskDefinition(ecs, _, _) >> TaskDefinition.builder().taskDefinitionArn('new-task-def-arn').build()
    operation.registerAutoScalingGroup(_, _, _) >> 'service/my-cluster/mygreatapp-stack1-details2'
    ecs.describeServices(_ as DescribeServicesRequest) >> DescribeServicesResponse.builder()
        .services(Service.builder()
            .serviceName(serviceName)
            .status('ACTIVE')
            .tags(EcsNativeServiceTag.tag())
            .desiredCount(7)
            .networkConfiguration(existingNetwork)
            .serviceRegistries(existingRegistries)
            .placementConstraints(existingConstraints)
            .placementStrategy(existingStrategy)
            .capacityProviderStrategy(existingCapacityProviders)
            .platformVersion('1.4.0')
            .healthCheckGracePeriodSeconds(60)
            .enableExecuteCommand(true)
            .loadBalancers(existingLoadBalancers)
            .build())
        .build()
    ecs.updateService(_ as UpdateServiceRequest) >> { UpdateServiceRequest request ->
      updateRequest = request
      UpdateServiceResponse.builder()
          .service(Service.builder().serviceName(serviceName).taskDefinition('new-task-def-arn').build())
          .build()
    }

    when:
    operation.operate([])

    then:
    updateRequest.service() == serviceName
    updateRequest.desiredCount() != null
    updateRequest.desiredCount() == 3
    updateRequest.networkConfiguration() == existingNetwork
    updateRequest.serviceRegistries() == existingRegistries
    updateRequest.placementConstraints() == existingConstraints
    updateRequest.placementStrategy() == existingStrategy
    updateRequest.capacityProviderStrategy() == existingCapacityProviders
    updateRequest.platformVersion() == '1.4.0'
    updateRequest.healthCheckGracePeriodSeconds() == 60
    updateRequest.enableExecuteCommand() == true
    updateRequest.loadBalancers() == existingLoadBalancers
  }


  @Unroll
  def 'copy existing capacity (#flagForm) leaves desired count and the scalable target untouched'() {
    given:
    def serviceName = 'mygreatapp-stack1-details2'
    def existingNetwork = NetworkConfiguration.builder().build()
    def existingLoadBalancers = [LoadBalancer.builder().targetGroupArn('arn:target-group').containerName('app').containerPort(8080).build()]
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'mygreatapp', stack: 'stack1', freeFormDetails: 'details2',
        account: 'test',
        ecsClusterName: 'my-cluster',
        availabilityZones: ['us-west-1': ['us-west-1a']],
        // Deck's wizard always sends a capacity; the flag must win over it.
        capacity: new ServerGroup.Capacity(1, 2, 3))
    useSourceCapacity(description)
    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    def updateRequest
    operation.getAmazonEcsClient() >> ecs
    operation.getCredentials() >> Mock(AmazonCredentials)
    operation.resolveTaskRoleArn(_) >> 'arn:aws:iam::123456789012:role/ecsRole'
    operation.registerTaskDefinition(ecs, _, _) >> TaskDefinition.builder().taskDefinitionArn('new-task-def-arn').build()
    ecs.describeServices(_ as DescribeServicesRequest) >> DescribeServicesResponse.builder()
        .services(Service.builder()
            .serviceName(serviceName).status('ACTIVE').tags(EcsNativeServiceTag.tag())
            .desiredCount(7).networkConfiguration(existingNetwork).loadBalancers(existingLoadBalancers)
            .build())
        .build()
    ecs.updateService(_ as UpdateServiceRequest) >> { UpdateServiceRequest request ->
      updateRequest = request
      UpdateServiceResponse.builder()
          .service(Service.builder().serviceName(serviceName).taskDefinition('new-task-def-arn').build())
          .build()
    }

    when:
    operation.operate([])

    then:
    updateRequest.taskDefinition() == 'new-task-def-arn'
    updateRequest.desiredCount() == null
    updateRequest.networkConfiguration() == existingNetwork
    updateRequest.loadBalancers() == existingLoadBalancers
    0 * operation.registerAutoScalingGroup(_, _, _)

    where:
    flagForm                         | useSourceCapacity
    'top-level useSourceCapacity'    | { EcsNativeCreateServerGroupDescription d -> d.useSourceCapacity = true }
    'source.useSourceCapacity'       | { EcsNativeCreateServerGroupDescription d ->
      d.source = new CreateServerGroupDescription.Source(useSourceCapacity: true) }
  }

  def 'copy existing capacity only keeps capacity; stage service settings still apply'() {
    given: 'a live service whose settings differ from the stage'
    def serviceName = 'mygreatapp-stack1-details2'
    def stageConstraints = [PlacementConstraint.builder().type('distinctInstance').build()]
    def stageStrategy = [PlacementStrategy.builder().type('spread').field('attribute:ecs.availability-zone').build()]
    def stageCapacityProviders = [CapacityProviderStrategyItem.builder().capacityProvider('FARGATE_SPOT').weight(1).build()]
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'mygreatapp', stack: 'stack1', freeFormDetails: 'details2',
        account: 'test',
        ecsClusterName: 'my-cluster',
        availabilityZones: ['us-west-1': ['us-west-1a']],
        useSourceCapacity: true,
        capacity: new ServerGroup.Capacity(1, 2, 3),
        placementConstraints: stageConstraints,
        placementStrategySequence: stageStrategy,
        capacityProviderStrategy: stageCapacityProviders,
        platformVersion: '1.4.0',
        healthCheckGracePeriodSeconds: 120)
    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    def updateRequest
    operation.getAmazonEcsClient() >> ecs
    operation.getCredentials() >> Mock(AmazonCredentials)
    operation.resolveTaskRoleArn(_) >> 'arn:aws:iam::123456789012:role/ecsRole'
    operation.registerTaskDefinition(ecs, _, _) >> TaskDefinition.builder().taskDefinitionArn('new-task-def-arn').build()
    ecs.describeServices(_ as DescribeServicesRequest) >> DescribeServicesResponse.builder()
        .services(Service.builder()
            .serviceName(serviceName).status('ACTIVE').tags(EcsNativeServiceTag.tag())
            .desiredCount(7)
            .placementConstraints(PlacementConstraint.builder().type('memberOf').expression('attribute:x == y').build())
            .placementStrategy(PlacementStrategy.builder().type('binpack').field('memory').build())
            .capacityProviderStrategy(CapacityProviderStrategyItem.builder().capacityProvider('FARGATE').weight(1).build())
            .platformVersion('LATEST')
            .healthCheckGracePeriodSeconds(30)
            .build())
        .build()
    ecs.updateService(_ as UpdateServiceRequest) >> { UpdateServiceRequest request ->
      updateRequest = request
      UpdateServiceResponse.builder()
          .service(Service.builder().serviceName(serviceName).taskDefinition('new-task-def-arn').build())
          .build()
    }

    when:
    operation.operate([])

    then: 'capacity is left to autoscaling'
    updateRequest.desiredCount() == null
    0 * operation.registerAutoScalingGroup(_, _, _)

    and: 'service settings come from the stage, as in classic ecs'
    updateRequest.taskDefinition() == 'new-task-def-arn'
    updateRequest.placementConstraints() == stageConstraints
    updateRequest.placementStrategy() == stageStrategy
    updateRequest.capacityProviderStrategy() == stageCapacityProviders
    updateRequest.platformVersion() == '1.4.0'
    updateRequest.healthCheckGracePeriodSeconds() == 120
  }

  def 'without copy existing capacity the stage capacity stays authoritative'() {
    given:
    def serviceName = 'mygreatapp-stack1-details2'
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'mygreatapp', stack: 'stack1', freeFormDetails: 'details2',
        account: 'test',
        ecsClusterName: 'my-cluster',
        availabilityZones: ['us-west-1': ['us-west-1a']],
        useSourceCapacity: false,
        capacity: new ServerGroup.Capacity(1, 2, 3))
    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    def updateRequest
    operation.getAmazonEcsClient() >> ecs
    operation.getCredentials() >> Mock(AmazonCredentials)
    operation.resolveTaskRoleArn(_) >> 'arn:aws:iam::123456789012:role/ecsRole'
    operation.registerTaskDefinition(ecs, _, _) >> TaskDefinition.builder().taskDefinitionArn('new-task-def-arn').build()
    ecs.describeServices(_ as DescribeServicesRequest) >> DescribeServicesResponse.builder()
        .services(Service.builder()
            .serviceName(serviceName).status('ACTIVE').tags(EcsNativeServiceTag.tag()).desiredCount(7).build())
        .build()
    ecs.updateService(_ as UpdateServiceRequest) >> { UpdateServiceRequest request ->
      updateRequest = request
      UpdateServiceResponse.builder()
          .service(Service.builder().serviceName(serviceName).taskDefinition('new-task-def-arn').build())
          .build()
    }

    when:
    operation.operate([])

    then:
    updateRequest.desiredCount() == 3
    1 * operation.registerAutoScalingGroup(_, _, _) >> 'service/my-cluster/mygreatapp-stack1-details2'
  }

  def 'targets the existing fixed-name service when the ecs tags naming strategy is active'() {
    given:
    def serviceName = 'mygreatapp-stack1-details2'
    def account = 'tag-namer-account'
    NamerRegistry.lookup()
        .withProvider(EcsCloudProvider.ID)
        .withAccount(account)
        .setNamer(EcsResource, new EcsTagNamer())
    def description = new EcsNativeCreateServerGroupDescription(
        application: 'mygreatapp', stack: 'stack1', freeFormDetails: 'details2',
        account: account,
        ecsClusterName: 'my-cluster',
        availabilityZones: ['us-west-1': ['us-west-1a']],
        useSourceCapacity: false,
        capacity: new ServerGroup.Capacity(1, 2, 3))
    def operation = Spy(EcsNativeCreateServerGroupAtomicOperation, constructorArgs: [description])
    def updateRequest
    operation.getAmazonEcsClient() >> ecs
    operation.getCredentials() >> Mock(AmazonCredentials)
    operation.resolveTaskRoleArn(_) >> 'arn:aws:iam::123456789012:role/ecsRole'
    operation.registerTaskDefinition(ecs, _, _) >> TaskDefinition.builder().taskDefinitionArn('new-task-def-arn').build()
    operation.registerAutoScalingGroup(_, _, _) >> 'service/my-cluster/mygreatapp-stack1-details2'
    ecs.describeServices(_ as DescribeServicesRequest) >> DescribeServicesResponse.builder()
        .services(Service.builder()
            .serviceName(serviceName).status('ACTIVE').tags(EcsNativeServiceTag.tag()).desiredCount(7).build())
        .build()
    ecs.updateService(_ as UpdateServiceRequest) >> { UpdateServiceRequest request ->
      updateRequest = request
      UpdateServiceResponse.builder()
          .service(Service.builder().serviceName(serviceName).taskDefinition('new-task-def-arn').build())
          .build()
    }

    when:
    operation.operate([])

    then:
    // Unlike the default (Frigga) namer, EcsTagNamer does not rewrite the service name, so the
    // request must already carry the fixed name rather than the versioned "family-family" form.
    updateRequest.service() == serviceName
    updateRequest.desiredCount() == 3

    cleanup:
    NamerRegistry.lookup()
        .withProvider(EcsCloudProvider.ID)
        .withAccount(account)
        .setNamer(EcsResource, new EcsDefaultNamer())
  }
}
