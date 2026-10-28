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

import com.netflix.spinnaker.clouddriver.ecs.EcsNativeServiceTag
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeUpdateServiceDescription
import software.amazon.awssdk.services.ecs.model.AdvancedConfiguration
import software.amazon.awssdk.services.ecs.model.CapacityProviderStrategyItem
import software.amazon.awssdk.services.ecs.model.LoadBalancer
import software.amazon.awssdk.services.ecs.model.NetworkConfiguration
import software.amazon.awssdk.services.ecs.model.PlacementConstraint
import software.amazon.awssdk.services.ecs.model.PlacementStrategy
import software.amazon.awssdk.services.ecs.model.DeploymentCircuitBreaker
import software.amazon.awssdk.services.ecs.model.DeploymentConfiguration
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse
import software.amazon.awssdk.services.ecs.model.Service
import software.amazon.awssdk.services.ecs.model.ServiceRegistry
import software.amazon.awssdk.services.ecs.model.Tag
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse

class EcsNativeUpdateServiceAtomicOperationSpec extends CommonAtomicOperation {

  void 'should update the service in place with native deployment configuration'() {
    given:
    def serviceName = 'myapp-kcats-liated-v007'
    def credentials = TestCredential.named('test', [:])

    def operation = new EcsNativeUpdateServiceAtomicOperation(new EcsNativeUpdateServiceDescription(
      credentials: credentials,
      region: 'us-west-1',
      serverGroupName: serviceName,
      taskDefinition: 'task-def-arn',
      minimumHealthyPercent: 50,
      maximumPercent: 150,
      enableDeploymentCircuitBreaker: true,
      deploymentCircuitBreakerRollback: true,
      forceNewDeployment: true
    ))

    operation.amazonClientProvider = amazonClientProvider
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService

    amazonClientProvider.getAmazonEcsV2(_, _) >> ecs
    containerInformationService.getClusterName(_, _, _) >> 'my-cluster'
    credentialsRepository.getOne(_) >> credentials
    ecs.describeServices(_) >> DescribeServicesResponse.builder()
        .services(Service.builder().tags(EcsNativeServiceTag.tag()).build())
        .build()

    when:
    operation.operate([])

    then:
    1 * ecs.updateService({ UpdateServiceRequest req ->
      req.cluster() == 'my-cluster' &&
        req.service() == serviceName &&
        req.taskDefinition() == 'task-def-arn' &&
        req.forceNewDeployment() == true &&
        req.deploymentConfiguration().minimumHealthyPercent() == 50 &&
        req.deploymentConfiguration().maximumPercent() == 150 &&
        req.deploymentConfiguration().deploymentCircuitBreaker().enable() == true &&
        req.deploymentConfiguration().deploymentCircuitBreaker().rollback() == true
    } as UpdateServiceRequest) >> UpdateServiceResponse.builder()
        .service(Service.builder().serviceName(serviceName).build())
        .build()
  }

  void 'should not send a deployment configuration when none is specified'() {
    given:
    def serviceName = 'myapp-kcats-liated-v007'
    def credentials = TestCredential.named('test', [:])

    def operation = new EcsNativeUpdateServiceAtomicOperation(new EcsNativeUpdateServiceDescription(
      credentials: credentials,
      region: 'us-west-1',
      serverGroupName: serviceName,
      taskDefinition: 'task-def-arn'
    ))

    operation.amazonClientProvider = amazonClientProvider
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService

    amazonClientProvider.getAmazonEcsV2(_, _) >> ecs
    containerInformationService.getClusterName(_, _, _) >> 'my-cluster'
    credentialsRepository.getOne(_) >> credentials
    ecs.describeServices(_) >> DescribeServicesResponse.builder()
        .services(Service.builder().tags(EcsNativeServiceTag.tag()).build())
        .build()

    when:
    operation.operate([])

    then:
    1 * ecs.updateService({ UpdateServiceRequest req ->
      req.service() == serviceName &&
        req.taskDefinition() == 'task-def-arn' &&
        req.deploymentConfiguration() == null
    } as UpdateServiceRequest) >> UpdateServiceResponse.builder()
        .service(Service.builder().serviceName(serviceName).build())
        .build()
  }

  void 'allows blue/green and forwards deployment configuration'() {
    given:
    def credentials = TestCredential.named('test', [:])
    def operation = new EcsNativeUpdateServiceAtomicOperation(new EcsNativeUpdateServiceDescription(
      credentials: credentials,
      region: 'us-west-1',
      serverGroupName: 'myapp-stack-detail',
      taskDefinition: 'task-def-arn',
      deploymentStrategy: 'BLUE_GREEN'
    ))

    operation.amazonClientProvider = amazonClientProvider
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService

    amazonClientProvider.getAmazonEcsV2(_, _) >> ecs
    containerInformationService.getClusterName(_, _, _) >> 'my-cluster'
    credentialsRepository.getOne(_) >> credentials

    when:
    operation.operate([])

    then:
    1 * ecs.describeServices(_) >> DescribeServicesResponse.builder()
        .services(Service.builder().serviceName('myapp-stack-detail').tags(EcsNativeServiceTag.tag()).build())
        .build()
    1 * ecs.updateService({ UpdateServiceRequest request ->
      request.deploymentConfiguration().strategyAsString() == 'BLUE_GREEN'
    } as UpdateServiceRequest) >> UpdateServiceResponse.builder()
        .service(Service.builder().serviceName('myapp-stack-detail').build())
        .build()

  }

  void 'should send deployment alarms when alarm names are configured'() {
    given:
    def serviceName = 'myapp-kcats-liated-v007'
    def credentials = TestCredential.named('test', [:])

    def operation = new EcsNativeUpdateServiceAtomicOperation(new EcsNativeUpdateServiceDescription(
      credentials: credentials,
      region: 'us-west-1',
      serverGroupName: serviceName,
      taskDefinition: 'task-def-arn',
      alarmNames: ['myapp-high-error-rate'],
      deploymentAlarmsRollback: true
    ))

    operation.amazonClientProvider = amazonClientProvider
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService

    amazonClientProvider.getAmazonEcsV2(_, _) >> ecs
    containerInformationService.getClusterName(_, _, _) >> 'my-cluster'
    credentialsRepository.getOne(_) >> credentials
    ecs.describeServices(_) >> DescribeServicesResponse.builder()
        .services(Service.builder().tags(EcsNativeServiceTag.tag()).build())
        .build()

    when:
    operation.operate([])

    then:
    1 * ecs.updateService({ UpdateServiceRequest req ->
      req.deploymentConfiguration().alarms().alarmNames() == ['myapp-high-error-rate'] &&
        req.deploymentConfiguration().alarms().enable() == true &&
        req.deploymentConfiguration().alarms().rollback() == true
    } as UpdateServiceRequest) >> UpdateServiceResponse.builder()
        .service(Service.builder().serviceName(serviceName).build())
        .build()
  }


  void 'allows a stored blue/green strategy when update omits the strategy'() {
    given:
    def credentials = TestCredential.named('test', [:])
    def serviceName = 'myapp-stack-detail'
    def operation = new EcsNativeUpdateServiceAtomicOperation(new EcsNativeUpdateServiceDescription(
      credentials: credentials,
      region: 'us-west-1',
      serverGroupName: serviceName,
      taskDefinition: 'task-def-arn'
    ))

    operation.amazonClientProvider = amazonClientProvider
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService
    amazonClientProvider.getAmazonEcsV2(_, _) >> ecs
    credentialsRepository.getOne(_) >> credentials
    containerInformationService.getClusterName(_, _, _) >> 'my-cluster'
    ecs.describeServices(_) >> DescribeServicesResponse.builder()
      .services(Service.builder()
        .serviceName(serviceName)
        .tags(EcsNativeServiceTag.tag())
        .deploymentConfiguration(DeploymentConfiguration.builder().strategy('BLUE_GREEN').build())
        .build())
      .build()

    when:
    operation.operate([])

    then:
    1 * ecs.updateService({ UpdateServiceRequest request ->
      request.deploymentConfiguration() == null
    } as UpdateServiceRequest) >> UpdateServiceResponse.builder()
        .service(Service.builder().serviceName(serviceName).build())
        .build()
  }

  void 'keeps the blue/green ALB traffic-shift config on update (strategy: #requestedStrategy, resend load balancers: #resendLoadBalancers)'() {
    given: 'an owned BLUE_GREEN service whose load balancer carries advancedConfiguration'
    def credentials = TestCredential.named('test', [:])
    def serviceName = 'myapp-stack-detail'
    def advancedConfiguration = AdvancedConfiguration.builder()
      .alternateTargetGroupArn('arn:alternate-target-group')
      .productionListenerRule('arn:production-rule')
      .testListenerRule('arn:test-rule')
      .roleArn('arn:role')
      .build()
    def existingLoadBalancer = LoadBalancer.builder()
      .targetGroupArn('arn:target-group').containerName('app').containerPort(8080)
      .advancedConfiguration(advancedConfiguration)
      .build()
    def requestedLoadBalancers = resendLoadBalancers
      ? [LoadBalancer.builder().targetGroupArn('arn:target-group').containerName('app').containerPort(8080).build()]
      : null
    def operation = new EcsNativeUpdateServiceAtomicOperation(new EcsNativeUpdateServiceDescription(
      credentials: credentials,
      region: 'us-west-1',
      serverGroupName: serviceName,
      taskDefinition: 'task-def-arn',
      deploymentStrategy: requestedStrategy,
      loadBalancers: requestedLoadBalancers,
      forceNewDeployment: true
    ))
    operation.amazonClientProvider = amazonClientProvider
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService
    amazonClientProvider.getAmazonEcsV2(_, _) >> ecs
    credentialsRepository.getOne(_) >> credentials
    containerInformationService.getClusterName(_, _, _) >> 'my-cluster'
    ecs.describeServices(_) >> DescribeServicesResponse.builder()
      .services(Service.builder()
        .serviceName(serviceName)
        .tags(EcsNativeServiceTag.tag())
        .deploymentConfiguration(DeploymentConfiguration.builder().strategy('BLUE_GREEN').build())
        .loadBalancers(existingLoadBalancer)
        .build())
      .build()

    when:
    operation.operate([])

    then: 'omitted load balancers are left to ECS untouched; resent ones inherit the existing config'
    1 * ecs.updateService({ UpdateServiceRequest request ->
      resendLoadBalancers
        ? request.loadBalancers() == [existingLoadBalancer]
        : !request.hasLoadBalancers()
    } as UpdateServiceRequest) >> UpdateServiceResponse.builder()
      .service(Service.builder().serviceName(serviceName).build())
      .build()

    where:
    requestedStrategy | resendLoadBalancers
    'BLUE_GREEN'      | true
    null              | true
    'BLUE_GREEN'      | false
    null              | false
  }

  void 'does not override advanced configuration that the update supplies itself'() {
    given:
    def credentials = TestCredential.named('test', [:])
    def serviceName = 'myapp-stack-detail'
    def existing = LoadBalancer.builder()
      .targetGroupArn('arn:target-group').containerName('app').containerPort(8080)
      .advancedConfiguration(AdvancedConfiguration.builder().alternateTargetGroupArn('arn:old-alternate').build())
      .build()
    def requested = LoadBalancer.builder()
      .targetGroupArn('arn:target-group').containerName('app').containerPort(8080)
      .advancedConfiguration(AdvancedConfiguration.builder().alternateTargetGroupArn('arn:new-alternate').build())
      .build()
    def operation = new EcsNativeUpdateServiceAtomicOperation(new EcsNativeUpdateServiceDescription(
      credentials: credentials,
      region: 'us-west-1',
      serverGroupName: serviceName,
      deploymentStrategy: 'BLUE_GREEN',
      loadBalancers: [requested]
    ))
    operation.amazonClientProvider = amazonClientProvider
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService
    amazonClientProvider.getAmazonEcsV2(_, _) >> ecs
    credentialsRepository.getOne(_) >> credentials
    containerInformationService.getClusterName(_, _, _) >> 'my-cluster'
    ecs.describeServices(_) >> DescribeServicesResponse.builder()
      .services(Service.builder().serviceName(serviceName).tags(EcsNativeServiceTag.tag()).loadBalancers(existing).build())
      .build()

    when:
    operation.operate([])

    then:
    1 * ecs.updateService({ UpdateServiceRequest request ->
      request.loadBalancers() == [requested]
    } as UpdateServiceRequest) >> UpdateServiceResponse.builder()
      .service(Service.builder().serviceName(serviceName).build())
      .build()
  }

  void 'explicitly disables ECS Exec on an existing enabled service'() {
    given:
    def credentials = TestCredential.named('test', [:])
    def serviceName = 'myapp-stack-detail'
    def operation = new EcsNativeUpdateServiceAtomicOperation(new EcsNativeUpdateServiceDescription(
      credentials: credentials,
      region: 'us-west-1',
      serverGroupName: serviceName,
      taskDefinition: 'task-def-arn',
      enableExecuteCommand: false
    ))

    operation.amazonClientProvider = amazonClientProvider
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService
    amazonClientProvider.getAmazonEcsV2(_, _) >> ecs
    credentialsRepository.getOne(_) >> credentials
    containerInformationService.getClusterName(_, _, _) >> 'my-cluster'
    ecs.describeServices(_) >> DescribeServicesResponse.builder()
      .services(Service.builder()
        .serviceName(serviceName)
        .tags(EcsNativeServiceTag.tag())
        .enableExecuteCommand(true)
        .build())
      .build()

    when:
    operation.operate([])

    then:
    1 * ecs.updateService({ UpdateServiceRequest request ->
      request.enableExecuteCommand() == false
    } as UpdateServiceRequest) >> UpdateServiceResponse.builder()
      .service(Service.builder().serviceName(serviceName).build())
      .build()
  }

  void 'should apply mutable service shape fields on update'() {
    given:
    def serviceName = 'myapp-stack-detail'
    def credentials = TestCredential.named('test', [:])
    def networkConfiguration = NetworkConfiguration.builder().build()
    def serviceRegistries = [ServiceRegistry.builder().registryArn('arn:registry').build()]
    def placementConstraints = [PlacementConstraint.builder().type('distinctInstance').build()]
    def placementStrategy = [PlacementStrategy.builder().type('spread').field('instanceId').build()]
    def capacityProviderStrategy = [CapacityProviderStrategyItem.builder().capacityProvider('FARGATE').weight(1).build()]
    def loadBalancers = [LoadBalancer.builder().targetGroupArn('arn:target-group').containerName('app').containerPort(8080).build()]

    def operation = new EcsNativeUpdateServiceAtomicOperation(new EcsNativeUpdateServiceDescription(
      credentials: credentials,
      region: 'us-west-1',
      serverGroupName: serviceName,
      taskDefinition: 'task-def-arn',
      desiredCount: 3,
      networkConfiguration: networkConfiguration,
      serviceRegistries: serviceRegistries,
      placementConstraints: placementConstraints,
      placementStrategy: placementStrategy,
      capacityProviderStrategy: capacityProviderStrategy,
      platformVersion: '1.4.0',
      healthCheckGracePeriodSeconds: 60,
      enableExecuteCommand: true,
      loadBalancers: loadBalancers,
      forceNewDeployment: true
    ))

    operation.amazonClientProvider = amazonClientProvider
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService

    amazonClientProvider.getAmazonEcsV2(_, _) >> ecs
    containerInformationService.getClusterName(_, _, _) >> 'my-cluster'
    credentialsRepository.getOne(_) >> credentials
    ecs.describeServices(_) >> DescribeServicesResponse.builder()
        .services(Service.builder().tags(EcsNativeServiceTag.tag()).build())
        .build()

    when:
    operation.operate([])

    then:
    1 * ecs.updateService({ UpdateServiceRequest req ->
      req.desiredCount() == 3 &&
        req.networkConfiguration() == networkConfiguration &&
        req.serviceRegistries() == serviceRegistries &&
        req.placementConstraints() == placementConstraints &&
        req.placementStrategy() == placementStrategy &&
        req.capacityProviderStrategy() == capacityProviderStrategy &&
        req.platformVersion() == '1.4.0' &&
        req.healthCheckGracePeriodSeconds() == 60 &&
        req.enableExecuteCommand() == true &&
        req.loadBalancers() == loadBalancers
    } as UpdateServiceRequest) >> UpdateServiceResponse.builder()
        .service(Service.builder().serviceName(serviceName).build())
        .build()
  }

  void 'preserves unspecified deployment configuration fields on partial update'() {
    given:
    def serviceName = 'myapp-stack-detail'
    def credentials = TestCredential.named('test', [:])
    def existingConfiguration = DeploymentConfiguration.builder()
        .minimumHealthyPercent(60)
        .maximumPercent(180)
        .deploymentCircuitBreaker(DeploymentCircuitBreaker.builder().enable(true).rollback(true).build())
        .strategy('ROLLING')
        .bakeTimeInMinutes(10)
        .build()
    ecs.describeServices(_) >> DescribeServicesResponse.builder()
        .services(Service.builder().serviceName(serviceName).deploymentConfiguration(existingConfiguration).tags(EcsNativeServiceTag.tag()).build())
        .build()

    def operation = new EcsNativeUpdateServiceAtomicOperation(new EcsNativeUpdateServiceDescription(
      credentials: credentials,
      region: 'us-west-1',
      serverGroupName: serviceName,
      taskDefinition: 'task-def-arn',
      bakeTimeInMinutes: 20
    ))
    operation.amazonClientProvider = amazonClientProvider
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService

    amazonClientProvider.getAmazonEcsV2(_, _) >> ecs
    credentialsRepository.getOne(_) >> credentials
    containerInformationService.getClusterName(_, _, _) >> 'my-cluster'

    def taskDefinition = 'arn:aws:ecs:us-west-1:123456789012:task-definition/myapp:42'

    when:
    def result = operation.operate([])

    then:
    1 * ecs.updateService({ UpdateServiceRequest req ->
      req.deploymentConfiguration().minimumHealthyPercent() == 60 &&
        req.deploymentConfiguration().maximumPercent() == 180 &&
        req.deploymentConfiguration().deploymentCircuitBreaker().enable() == true &&
        req.deploymentConfiguration().deploymentCircuitBreaker().rollback() == true &&
        req.deploymentConfiguration().strategyAsString() == 'ROLLING' &&
        req.deploymentConfiguration().bakeTimeInMinutes() == 20
    } as UpdateServiceRequest) >> UpdateServiceResponse.builder()
        .service(Service.builder().serviceName(serviceName).taskDefinition(taskDefinition).currentServiceDeployment('service-deployment-1').tags(EcsNativeServiceTag.tag()).build())
        .build()
    result.serverGroupNames == ["us-west-1:${serviceName}"]
    result.serverGroupNameByRegion == ['us-west-1': serviceName]
    result.deployments.first().cloudProvider == 'ecs-native'
    result.deployments.first().account == 'test'
    result.deployments.first().location == 'us-west-1'
    result.deployments.first().serverGroupName == serviceName
    result.deployments.first().metadata.ecsNativeExpectedTaskDefinition == taskDefinition
    result.deployments.first().metadata.ecsNativeExpectedServiceDeploymentArn == 'service-deployment-1'
  }

  void 'clears an existing circuit breaker when update explicitly disables it'() {
    given:
    def credentials = TestCredential.named('test', [:])
    def operation = new EcsNativeUpdateServiceAtomicOperation(new EcsNativeUpdateServiceDescription(
      credentials: credentials,
      region: 'us-west-1',
      serverGroupName: 'myapp-stack-detail',
      taskDefinition: 'task-def-arn',
      enableDeploymentCircuitBreaker: false
    ))
    operation.amazonClientProvider = amazonClientProvider
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService
    amazonClientProvider.getAmazonEcsV2(_, _) >> ecs
    credentialsRepository.getOne(_) >> credentials
    containerInformationService.getClusterName(_, _, _) >> 'my-cluster'
    ecs.describeServices(_) >> DescribeServicesResponse.builder()
      .services(Service.builder()
        .tags(EcsNativeServiceTag.tag())
        .deploymentConfiguration(DeploymentConfiguration.builder()
          .deploymentCircuitBreaker(DeploymentCircuitBreaker.builder().enable(true).rollback(true).build())
          .build())
        .build())
      .build()

    when:
    operation.operate([])

    then:
    1 * ecs.updateService({ UpdateServiceRequest request ->
      request.deploymentConfiguration().deploymentCircuitBreaker().enable() == false &&
        request.deploymentConfiguration().deploymentCircuitBreaker().rollback() == false
    } as UpdateServiceRequest) >> UpdateServiceResponse.builder()
      .service(Service.builder().serviceName('myapp-stack-detail').build())
      .build()
  }

  void 'refuses to update an unowned service before any AWS write'() {
    given:
    def credentials = TestCredential.named('test', [:])
    def operation = new EcsNativeUpdateServiceAtomicOperation(new EcsNativeUpdateServiceDescription(
      credentials: credentials,
      region: 'us-west-1',
      serverGroupName: 'external-service',
      taskDefinition: 'task-def-arn'
    ))
    operation.amazonClientProvider = amazonClientProvider
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService

    amazonClientProvider.getAmazonEcsV2(_, _) >> ecs
    credentialsRepository.getOne(_) >> credentials
    containerInformationService.getClusterName(_, _, _) >> 'my-cluster'
    ecs.describeServices(_) >> DescribeServicesResponse.builder()
      .services(Service.builder().tags(Tag.builder().key('owner').value('external').build()).build())
      .build()

    when:
    operation.operate([])

    then:
    thrown(IllegalStateException)
    0 * ecs.updateService(_)
  }
}