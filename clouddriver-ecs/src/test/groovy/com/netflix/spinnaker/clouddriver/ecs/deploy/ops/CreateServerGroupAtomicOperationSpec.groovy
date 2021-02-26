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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops

import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling
import com.amazonaws.services.applicationautoscaling.model.*
import com.amazonaws.services.ecs.model.*
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.model.GetRoleResult
import com.amazonaws.services.identitymanagement.model.Role
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CreateServerGroupDescription
import com.netflix.spinnaker.clouddriver.ecs.names.EcsDefaultNamer
import com.netflix.spinnaker.clouddriver.ecs.names.EcsServerGroupName
import com.netflix.spinnaker.clouddriver.ecs.names.EcsResource
import com.netflix.spinnaker.clouddriver.ecs.names.EcsTagNamer
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.IamPolicyReader
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.IamTrustRelationship
import com.netflix.spinnaker.clouddriver.ecs.services.EcsCloudMetricService
import com.netflix.spinnaker.clouddriver.ecs.services.SecurityGroupSelector
import com.netflix.spinnaker.clouddriver.ecs.services.SubnetSelector
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.kork.artifacts.model.Artifact

import static com.netflix.spinnaker.clouddriver.ecs.deploy.ops.CreateServerGroupAtomicOperation.DOCKER_LABEL_KEY_SERVERGROUP

class CreateServerGroupAtomicOperationSpec extends CommonAtomicOperation {
  def iamClient = Mock(AmazonIdentityManagement)
  def iamPolicyReader = Mock(IamPolicyReader)
  def loadBalancingV2 = Mock(AmazonElasticLoadBalancing)
  def autoScalingClient = Mock(AWSApplicationAutoScaling)
  def subnetSelector = Mock(SubnetSelector)
  def securityGroupSelector = Mock(SecurityGroupSelector)
  def objectMapper = Mock(ObjectMapper)
  def artifactDownloader = Mock(ArtifactDownloader)

  def applicationName = 'myapp'
  def stack = 'kcats'
  def detail = 'liated'
  def serviceName = "${applicationName}-${stack}-${detail}"

  def trustRelationships = [new IamTrustRelationship(type: 'Service', value: 'ecs-tasks.amazonaws.com'),
                            new IamTrustRelationship(type: 'Service', value: 'ecs.amazonaws.com')]

  def role = new Role(assumeRolePolicyDocument: "json-encoded-string-here")

  def creds = Mock(NetflixAssumeRoleAmazonCredentials) {
    getName() >> { "test" }
    getRegions() >> { [new AmazonCredentials.AWSRegion('us-west-1', ['us-west-1a', 'us-west-1b'])] }
    getAssumeRole() >> { 'test-role' }
    getAccountId() >> { 'test' }
  }

  def taskDefinition = new TaskDefinition().withTaskDefinitionArn("task-def-arn")

  def targetGroup = new TargetGroup().withLoadBalancerArns("loadbalancer-arn").withTargetGroupArn('target-group-arn')

  def service = new Service(serviceName: "${serviceName}-v008")

  def source = new CreateServerGroupDescription.Source()

  def setup() {
    source.account = "test"
    source.region = "us-west-1"
    source.asgName = "${serviceName}-v007"
    source.useSourceCapacity = true

    amazonClientProvider.getAmazonEcs(_, _, _) >> ecs
    amazonClientProvider.getAmazonIdentityManagement(_, _, _) >> iamClient
    amazonClientProvider.getAmazonElasticLoadBalancingV2(_, _, _) >> loadBalancingV2
    amazonClientProvider.getAmazonApplicationAutoScaling(_, _, _) >> autoScalingClient
    containerInformationService.getClusterName(_, _, _) >> 'cluster-name'
    credentialsRepository.getOne(_) >> creds
  }

  def 'should create a service'() {
    given:
    def placementConstraint = new PlacementConstraint(type: 'memberOf', expression: 'attribute:ecs.instance-type =~ t2.*')

    def placementStrategy = new PlacementStrategy(type: 'spread', field: 'attribute:ecs.availability-zone')

    def description = new CreateServerGroupDescription(
      credentials: TestCredential.named('Test', [:]),
      application: applicationName,
      stack: stack,
      freeFormDetails: detail,
      ecsClusterName: 'test-cluster',
      iamRole: 'test-role',
      containerPort: 1337,
      targetGroup: 'target-group-arn',
      portProtocol: 'tcp',
      computeUnits: 9001,
      tags: ['label1': 'value1', 'fruit': 'tomato'],
      reservedMemory: 9002,
      dockerImageAddress: 'docker-image-url',
      capacity: new ServerGroup.Capacity(1, 1, 1),
      availabilityZones: ['us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c']],
      placementStrategySequence: [placementStrategy],
      placementConstraints: [placementConstraint],
      source: source
    )

    def operation = new CreateServerGroupAtomicOperation(description)

    operation.amazonClientProvider = amazonClientProvider
    operation.ecsCloudMetricService = Mock(EcsCloudMetricService)
    operation.iamPolicyReader = iamPolicyReader
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService

    when:
    def result = operation.operate([])

    then:
    ecs.listAccountSettings(_) >> new ListAccountSettingsResult().withSettings(
      new Setting(name: SettingName.TaskLongArnFormat, value: "enabled"),
      new Setting(name: SettingName.ServiceLongArnFormat, value: "enabled")
    )
    ecs.listServices(_) >> new ListServicesResult().withServiceArns("${serviceName}-v007")
    ecs.describeServices({DescribeServicesRequest request ->
      request.cluster == 'test-cluster'
      request.services == ["${serviceName}-v007"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "${serviceName}-v007", createdAt: new Date(), desiredCount: 3))
    ecs.describeServices(_) >> new DescribeServicesResult()

    ecs.registerTaskDefinition(_) >> new RegisterTaskDefinitionResult().withTaskDefinition(taskDefinition)
    iamClient.getRole(_) >> new GetRoleResult().withRole(role)
    iamPolicyReader.getTrustedEntities(_) >> trustRelationships
    loadBalancingV2.describeTargetGroups(_) >> new DescribeTargetGroupsResult().withTargetGroups(targetGroup)
    ecs.createService({ CreateServiceRequest request ->
      request.cluster == 'test-cluster'
      request.serviceName == 'myapp-kcats-liated-v008'
      request.taskDefinition == 'task-def-arn'
      request.loadBalancers.size() == 1
      request.loadBalancers.get(0).targetGroupArn == 'target-group-arn'
      request.loadBalancers.get(0).containerName == 'v008'
      request.loadBalancers.get(0).containerPort == 1337
      request.serviceRegistries == []
      request.desiredCount == 3
      request.role == null
      request.placementConstraints.size() == 1
      request.placementConstraints.get(0).type == 'memberOf'
      request.placementConstraints.get(0).expression == 'attribute:ecs.instance-type =~ t2.*'
      request.placementStrategy.size() == 1
      request.placementStrategy.get(0).type == 'spread'
      request.placementStrategy.get(0).field == 'attribute:ecs.availability-zone'
      request.networkConfiguration == null
      request.healthCheckGracePeriodSeconds == null
      request.enableECSManagedTags == true
      request.propagateTags == PropagateTags.SERVICE.toString()
      request.tags.size() == 2
      request.tags.get(0).key == 'label1'
      request.tags.get(0).value == 'value1'
      request.tags.get(1).key == 'fruit'
      request.tags.get(1).value == 'tomato'
      request.launchType == null
      request.platformVersion == null
    }) >> new CreateServiceResult().withService(service)

    result.getServerGroupNames().size() == 1
    result.getServerGroupNameByRegion().size() == 1
    result.getServerGroupNames().contains("us-west-1:" + serviceName + "-v008")
    result.getServerGroupNameByRegion().containsKey('us-west-1')
    result.getServerGroupNameByRegion().get('us-west-1').contains(serviceName + "-v008")

    1 * autoScalingClient.registerScalableTarget(_) >> { arguments ->
      RegisterScalableTargetRequest request = arguments.get(0)
      assert request.serviceNamespace == ServiceNamespace.Ecs.toString()
      assert request.scalableDimension == ScalableDimension.EcsServiceDesiredCount.toString()
      assert request.resourceId == "service/test-cluster/${serviceName}-v008"
      assert request.roleARN == null
      assert request.minCapacity == 2
      assert request.maxCapacity == 4
    }

    autoScalingClient.describeScalableTargets(_) >> new DescribeScalableTargetsResult()
      .withScalableTargets(new ScalableTarget()
      .withResourceId("service/test-cluster/${serviceName}-v007")
      .withMinCapacity(2)
      .withMaxCapacity(4))

    1 * operation.ecsCloudMetricService.copyScalingPolicies(
      "Test",
      "us-west-1",
      "${serviceName}-v008",
      "service/test-cluster/${serviceName}-v008",
      "test",
      "us-west-1",
      "${serviceName}-v007",
      "service/test-cluster/${serviceName}-v007",
      "test-cluster"
    )
  }

  def 'should create a service using VPC and Fargate mode'() {
    given:
    def serviceRegistry = new CreateServerGroupDescription.ServiceDiscoveryAssociation(
      registry: new CreateServerGroupDescription.ServiceRegistry(arn: 'srv-registry-arn'),
      containerPort: 9090
    )
    def description = new CreateServerGroupDescription(
      credentials: TestCredential.named('Test', [:]),
      application: applicationName,
      stack: stack,
      freeFormDetails: detail,
      ecsClusterName: 'test-cluster',
      iamRole: 'test-role',
      containerPort: 1337,
      targetGroup: 'target-group-arn',
      portProtocol: 'tcp',
      computeUnits: 9001,
      reservedMemory: 9002,
      dockerImageAddress: 'docker-image-url',
      capacity: new ServerGroup.Capacity(1, 1, 1),
      availabilityZones: ['us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c']],
      placementStrategySequence: [],
      launchType: 'FARGATE',
      platformVersion: '1.0.0',
      networkMode: 'awsvpc',
      subnetType: 'public',
      securityGroupNames: ['helloworld'],
      associatePublicIpAddress: true,
      serviceDiscoveryAssociations: [serviceRegistry]
    )

    def operation = new CreateServerGroupAtomicOperation(description)

    operation.amazonClientProvider = amazonClientProvider
    operation.ecsCloudMetricService = Mock(EcsCloudMetricService)
    operation.iamPolicyReader = iamPolicyReader
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService
    operation.subnetSelector = subnetSelector
    operation.securityGroupSelector = securityGroupSelector

    subnetSelector.resolveSubnetsIdsForMultipleSubnetTypes(_, _, _, _) >> ['subnet-12345']
    subnetSelector.getSubnetVpcIds(_, _, _) >> ['vpc-123']
    securityGroupSelector.resolveSecurityGroupNames(_, _, _, _) >> ['sg-12345']

    when:
    def result = operation.operate([])

    then:
    ecs.listAccountSettings(_) >> new ListAccountSettingsResult()
    ecs.listServices(_) >> new ListServicesResult().withServiceArns("${serviceName}-v007")
    ecs.describeServices({DescribeServicesRequest request ->
      request.cluster == 'test-cluster'
      request.services == ["${serviceName}-v007"]
      request.include == ["TAGS"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "${serviceName}-v007", createdAt: new Date(), desiredCount: 3))
    ecs.describeServices(_) >> new DescribeServicesResult()

    ecs.registerTaskDefinition(_) >> new RegisterTaskDefinitionResult().withTaskDefinition(taskDefinition)

    iamClient.getRole(_) >> new GetRoleResult().withRole(role)
    iamPolicyReader.getTrustedEntities(_) >> trustRelationships
    loadBalancingV2.describeTargetGroups(_) >> new DescribeTargetGroupsResult().withTargetGroups(targetGroup)

    ecs.createService({ CreateServiceRequest request ->
      request.cluster == 'test-cluster'
      request.serviceName == 'myapp-kcats-liated-v008'
      request.taskDefinition == 'task-def-arn'
      request.loadBalancers.size() == 1
      request.loadBalancers.get(0).targetGroupArn == 'target-group-arn'
      request.loadBalancers.get(0).containerName == 'v008'
      request.loadBalancers.get(0).containerPort == 1337
      request.serviceRegistries.size() == 1
      request.serviceRegistries.get(0) == new ServiceRegistry(
        registryArn: 'srv-registry-arn',
        containerPort: 9090,
        containerName: 'v008'
      )
      request.desiredCount == 1
      request.role == null
      request.placementStrategy == []
      request.placementConstraints == []
      request.networkConfiguration.awsvpcConfiguration.subnets == ['subnet-12345']
      request.networkConfiguration.awsvpcConfiguration.securityGroups == ['sg-12345']
      request.networkConfiguration.awsvpcConfiguration.assignPublicIp == 'ENABLED'
      request.healthCheckGracePeriodSeconds == null
      request.enableECSManagedTags == null
      request.propagateTags == null
      request.tags == []
      request.launchType == 'FARGATE'
      request.platformVersion == '1.0.0'
    } as CreateServiceRequest) >> new CreateServiceResult().withService(service)

    result.getServerGroupNames().size() == 1
    result.getServerGroupNameByRegion().size() == 1
    result.getServerGroupNames().contains("us-west-1:" + serviceName + "-v008")
    result.getServerGroupNameByRegion().containsKey('us-west-1')
    result.getServerGroupNameByRegion().get('us-west-1').contains(serviceName + "-v008")
  }

  def 'should create a service using VPC and FARGATE Capacity Provider Strategy'() {
    given:
    def serviceRegistry = new CreateServerGroupDescription.ServiceDiscoveryAssociation(
      registry: new CreateServerGroupDescription.ServiceRegistry(arn: 'srv-registry-arn'),
      containerPort: 9090
    )
    def capacityProviderStrategy = new CapacityProviderStrategyItem(
      capacityProvider: 'FARGATE',
      weight: 1
    )
    def description = new CreateServerGroupDescription(
      credentials: TestCredential.named('Test', [:]),
      application: applicationName,
      stack: stack,
      freeFormDetails: detail,
      ecsClusterName: 'test-cluster',
      iamRole: 'test-role',
      containerPort: 1337,
      targetGroup: 'target-group-arn',
      portProtocol: 'tcp',
      computeUnits: 9001,
      reservedMemory: 9002,
      dockerImageAddress: 'docker-image-url',
      capacity: new ServerGroup.Capacity(1, 1, 1),
      availabilityZones: ['us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c']],
      placementStrategySequence: [],
      capacityProviderStrategy: [capacityProviderStrategy],
      platformVersion: '1.0.0',
      networkMode: 'awsvpc',
      subnetType: 'public',
      securityGroupNames: ['helloworld'],
      associatePublicIpAddress: true,
      serviceDiscoveryAssociations: [serviceRegistry]
    )

    def operation = new CreateServerGroupAtomicOperation(description)

    operation.amazonClientProvider = amazonClientProvider
    operation.ecsCloudMetricService = Mock(EcsCloudMetricService)
    operation.iamPolicyReader = iamPolicyReader
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService
    operation.subnetSelector = subnetSelector
    operation.securityGroupSelector = securityGroupSelector

    subnetSelector.resolveSubnetsIdsForMultipleSubnetTypes(_, _, _, _) >> ['subnet-12345']
    subnetSelector.getSubnetVpcIds(_, _, _) >> ['vpc-123']
    securityGroupSelector.resolveSecurityGroupNames(_, _, _, _) >> ['sg-12345']

    when:
    def result = operation.operate([])

    then:
    ecs.listAccountSettings(_) >> new ListAccountSettingsResult()
    ecs.listServices(_) >> new ListServicesResult().withServiceArns("${serviceName}-v007")
    ecs.describeServices({DescribeServicesRequest request ->
      request.cluster == 'test-cluster'
      request.services == ["${serviceName}-v007"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "${serviceName}-v007", createdAt: new Date(), desiredCount: 3))
    ecs.describeServices(_) >> new DescribeServicesResult()

    ecs.registerTaskDefinition(_) >> new RegisterTaskDefinitionResult().withTaskDefinition(taskDefinition)

    iamClient.getRole(_) >> new GetRoleResult().withRole(role)
    iamPolicyReader.getTrustedEntities(_) >> trustRelationships
    loadBalancingV2.describeTargetGroups(_) >> new DescribeTargetGroupsResult().withTargetGroups(targetGroup)

    ecs.createService({ CreateServiceRequest request ->
      request.cluster == 'test-cluster'
      request.serviceName == 'myapp-kcats-liated-v008'
      request.taskDefinition == 'task-def-arn'
      request.loadBalancers.size() == 1
      request.loadBalancers.get(0).targetGroupArn == 'target-group-arn'
      request.loadBalancers.get(0).containerName == 'v008'
      request.loadBalancers.get(0).containerPort == 1337
      request.serviceRegistries.size() == 1
      request.serviceRegistries.get(0) == new ServiceRegistry(
        registryArn: 'srv-registry-arn',
        containerPort: 9090,
        containerName: 'v008'
      )
      request.desiredCount == 1
      request.role == null
      request.placementStrategy == []
      request.placementConstraints == []
      request.networkConfiguration.awsvpcConfiguration.subnets == ['subnet-12345']
      request.networkConfiguration.awsvpcConfiguration.securityGroups == ['sg-12345']
      request.networkConfiguration.awsvpcConfiguration.assignPublicIp == 'ENABLED'
      request.healthCheckGracePeriodSeconds == null
      request.enableECSManagedTags == null
      request.propagateTags == null
      request.tags == []
      request.capacityProviderStrategy == [capacityProviderStrategy]
      request.platformVersion == '1.0.0'
    } as CreateServiceRequest) >> new CreateServiceResult().withService(service)

    result.getServerGroupNames().size() == 1
    result.getServerGroupNameByRegion().size() == 1
    result.getServerGroupNames().contains("us-west-1:" + serviceName + "-v008")
    result.getServerGroupNameByRegion().containsKey('us-west-1')
    result.getServerGroupNameByRegion().get('us-west-1').contains(serviceName + "-v008")
  }

  def 'should create a service using multiple subnet types'() {
    given:
    def serviceRegistry = new CreateServerGroupDescription.ServiceDiscoveryAssociation(
      registry: new CreateServerGroupDescription.ServiceRegistry(arn: 'srv-registry-arn'),
      containerPort: 9090
    )
    def description = new CreateServerGroupDescription(
      credentials: TestCredential.named('Test', [:]),
      application: applicationName,
      stack: stack,
      freeFormDetails: detail,
      ecsClusterName: 'test-cluster',
      iamRole: 'test-role',
      containerPort: 1337,
      targetGroup: 'target-group-arn',
      portProtocol: 'tcp',
      computeUnits: 9001,
      reservedMemory: 9002,
      dockerImageAddress: 'docker-image-url',
      capacity: new ServerGroup.Capacity(1, 1, 1),
      availabilityZones: ['us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c']],
      placementStrategySequence: [],
      launchType: 'FARGATE',
      platformVersion: '1.0.0',
      networkMode: 'awsvpc',
      subnetTypes: ['public-az1', 'public-az2'],
      securityGroupNames: ['helloworld'],
      associatePublicIpAddress: true,
      serviceDiscoveryAssociations: [serviceRegistry]
    )

    def operation = new CreateServerGroupAtomicOperation(description)

    operation.amazonClientProvider = amazonClientProvider
    operation.ecsCloudMetricService = Mock(EcsCloudMetricService)
    operation.iamPolicyReader = iamPolicyReader
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService
    operation.subnetSelector = subnetSelector
    operation.securityGroupSelector = securityGroupSelector

    subnetSelector.resolveSubnetsIdsForMultipleSubnetTypes(_, _, _, _) >> ['subnet-12345', 'subnet-23456']
    subnetSelector.getSubnetVpcIds(_, _, _) >> ['vpc-123']
    securityGroupSelector.resolveSecurityGroupNames(_, _, _, _) >> ['sg-12345']

    when:
    def result = operation.operate([])

    then:
    ecs.listAccountSettings(_) >> new ListAccountSettingsResult()
    ecs.listServices(_) >> new ListServicesResult().withServiceArns("${serviceName}-v007")
    ecs.describeServices({DescribeServicesRequest request ->
      request.cluster == 'test-cluster'
      request.services == ["${serviceName}-v007"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "${serviceName}-v007", createdAt: new Date(), desiredCount: 3))
    ecs.describeServices(_) >> new DescribeServicesResult()

    ecs.registerTaskDefinition(_) >> new RegisterTaskDefinitionResult().withTaskDefinition(taskDefinition)

    iamClient.getRole(_) >> new GetRoleResult().withRole(role)
    iamPolicyReader.getTrustedEntities(_) >> trustRelationships
    loadBalancingV2.describeTargetGroups(_) >> new DescribeTargetGroupsResult().withTargetGroups(targetGroup)

    ecs.createService({ CreateServiceRequest request ->
      request.cluster == 'test-cluster'
      request.serviceName == 'myapp-kcats-liated-v008'
      request.taskDefinition == 'task-def-arn'
      request.loadBalancers.size() == 1
      request.loadBalancers.get(0).targetGroupArn == 'target-group-arn'
      request.loadBalancers.get(0).containerName == 'v008'
      request.loadBalancers.get(0).containerPort == 1337
      request.serviceRegistries.size() == 1
      request.serviceRegistries.get(0) == new ServiceRegistry(
        registryArn: 'srv-registry-arn',
        containerPort: 9090,
        containerName: 'v008'
      )
      request.desiredCount == 1
      request.role == null
      request.placementStrategy == []
      request.placementConstraints == []
      request.networkConfiguration.awsvpcConfiguration.subnets == ['subnet-12345', 'subnet-23456']
      request.networkConfiguration.awsvpcConfiguration.securityGroups == ['sg-12345']
      request.networkConfiguration.awsvpcConfiguration.assignPublicIp == 'ENABLED'
      request.healthCheckGracePeriodSeconds == null
      request.enableECSManagedTags == null
      request.propagateTags == null
      request.tags == []
      request.launchType == 'FARGATE'
      request.platformVersion == '1.0.0'
    } as CreateServiceRequest) >> new CreateServiceResult().withService(service)

    result.getServerGroupNames().size() == 1
    result.getServerGroupNameByRegion().size() == 1
    result.getServerGroupNames().contains("us-west-1:" + serviceName + "-v008")
    result.getServerGroupNameByRegion().containsKey('us-west-1')
    result.getServerGroupNameByRegion().get('us-west-1').contains(serviceName + "-v008")
  }

  def 'should create services without load balancers'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTargetGroup() >> null

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeServiceRequest('task-def-arn',
            new EcsServerGroupName('mygreatapp-stack1-details2-v011'),
            1, new EcsDefaultNamer(), false)

    then:
    request.getLoadBalancers() == []
    request.getRole() == null
  }

  def 'should create default Docker labels'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getDockerLabels() >> null

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', new EcsServerGroupName('mygreatapp-stack1-details2-v011'))

    then:
    def labels = request.getContainerDefinitions().get(0).getDockerLabels()
    labels.get(DOCKER_LABEL_KEY_SERVERGROUP) == 'mygreatapp-stack1-details2-v011'
    labels.get(CreateServerGroupAtomicOperation.DOCKER_LABEL_KEY_STACK) == 'stack1'
    labels.get(CreateServerGroupAtomicOperation.DOCKER_LABEL_KEY_DETAIL) == 'details2'
  }

  def 'should create custom Docker labels'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getDockerLabels() >> ['label1': 'value1', 'fruit':'tomato']

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', new EcsServerGroupName('mygreatapp-stack1-details2-v011'))

    then:
    def labels = request.getContainerDefinitions().get(0).getDockerLabels()
    labels.get('label1') == 'value1'
    labels.get('fruit') == 'tomato'
  }

  def 'should not allow overwriting Spinnaker Docker labels'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    def dockerLabels = [:]
    dockerLabels.put(DOCKER_LABEL_KEY_SERVERGROUP, 'some-value-we-dont-want-to-see')

    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getDockerLabels() >> dockerLabels

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', new EcsServerGroupName('mygreatapp-stack1-details2-v011'))

    then:
    def labels = request.getContainerDefinitions().get(0).getDockerLabels()
    labels.get(DOCKER_LABEL_KEY_SERVERGROUP) == 'mygreatapp-stack1-details2-v011'
    labels.get(DOCKER_LABEL_KEY_SERVERGROUP) != 'some-value-we-dont-want-to-see'
  }

  def 'should allow selecting the logDriver'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getLogDriver() >> 'some-log-driver'
    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', new EcsServerGroupName('mygreatapp-stack1-details2-v011'))

    then:
    request.getContainerDefinitions().get(0).getLogConfiguration().getLogDriver() == 'some-log-driver'
  }

  def 'should allow empty logOptions'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getLogDriver() >> 'some-log-driver'
    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', new EcsServerGroupName('mygreatapp-stack1-details2-v011'))

    then:
    request.getContainerDefinitions().get(0).getLogConfiguration().getOptions() == null
  }

  def 'should allow registering logOptions'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getLogDriver() >> 'some-log-driver'
    def logOptions = ['key1': '1value', 'key2': 'value2']
    description.getLogOptions() >> logOptions

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', new EcsServerGroupName('mygreatapp-stack1-details2-v011'))

    then:
    request.getContainerDefinitions().get(0).getLogConfiguration().getOptions() == logOptions
  }

  def 'should allow no port mappings'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getContainerPort() >> null
    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', new EcsServerGroupName('mygreatapp-stack1-details2-v011'))

    then:
    request.getContainerDefinitions().get(0).getPortMappings().isEmpty()
  }

  def 'should allow using secret credentials for the docker image'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getDockerImageCredentialsSecret() >> 'my-secret'

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', new EcsServerGroupName('mygreatapp-stack1-details2-v011'))

    then:
    request.getContainerDefinitions().get(0).getRepositoryCredentials().getCredentialsParameter() == 'my-secret'
  }

  def 'should allow not specifying secret credentials for the docker image'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', new EcsServerGroupName('mygreatapp-stack1-details2-v011'))

    then:
    request.getContainerDefinitions().get(0).getRepositoryCredentials() == null
  }

  def 'should generate a RegisterTaskDefinitionRequest object'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getApplication() >> 'v1'
    description.getStack() >> 'kcats'
    description.getFreeFormDetails() >> 'liated'
    description.getEcsClusterName() >> 'test-cluster'
    description.getIamRole() >> 'None (No IAM role)'
    description.getContainerPort() >> 1337
    description.getTargetGroup() >> 'target-group-arn'
    description.getPortProtocol() >> 'tcp'
    description.getComputeUnits() >> 9001
    description.getReservedMemory() >> 9001
    description.getDockerImageAddress() >> 'docker-image-url'
    description.capacity = new ServerGroup.Capacity(1, 1, 1)
    description.availabilityZones = ['us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c']]
    description.placementStrategySequence = []

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    RegisterTaskDefinitionRequest result = operation.makeTaskDefinitionRequest("test-role", new EcsServerGroupName('v1-kcats-liated-v001'))

    then:
    result.getTaskRoleArn() == null
    result.getFamily() == "v1-kcats-liated"

    result.getContainerDefinitions().size() == 1
    def containerDefinition = result.getContainerDefinitions().first()
    containerDefinition.name == 'v001'
    containerDefinition.image == 'docker-image-url'
    containerDefinition.cpu == 9001
    containerDefinition.memoryReservation == 9001

    containerDefinition.portMappings.size() == 1
    def portMapping = containerDefinition.portMappings.first()
    portMapping.getHostPort() == 0
    portMapping.getContainerPort() == 1337
    portMapping.getProtocol() == 'tcp'

    containerDefinition.environment.size() == 3
    def environments = [:]
    for(elem in containerDefinition.environment){
      environments.put(elem.getName(), elem.getValue())
    }
    environments.get("SERVER_GROUP") == "v1-kcats-liated-v001"
    environments.get("CLOUD_STACK") == "kcats"
    environments.get("CLOUD_DETAIL") == "liated"
  }

  def 'should generate a RegisterTaskDefinitionRequest object from artifact'() {
    given:
    def resolvedArtifact = Artifact.builder()
      .name("taskdef.json")
      .reference("fake.github.com/repos/org/repo/taskdef.json")
      .artifactAccount("my-github-acct")
      .type("github/file")
      .build()
    def containerDef1 =
      new ContainerDefinition()
        .withName("web")
        .withImage("PLACEHOLDER")
        .withMemoryReservation(512)
    def containerDef2 =
      new ContainerDefinition()
        .withName("logs")
        .withImage("PLACEHOLDER")
        .withMemoryReservation(1024)
    def registerTaskDefRequest =
      new RegisterTaskDefinitionRequest()
        .withContainerDefinitions([containerDef1, containerDef2])
        .withExecutionRoleArn("arn:aws:role/myExecutionRole")
    def description = Mock(CreateServerGroupDescription)
    description.getApplication() >> 'v1'
    description.getStack() >> 'ecs'
    description.getFreeFormDetails() >> 'test'
    description.getEcsClusterName() >> 'test-cluster'
    description.getIamRole() >> 'None (No IAM role)'
    description.getResolvedTaskDefinitionArtifact() >> resolvedArtifact
    description.getContainerToImageMap() >> [
      web: "docker-image-url/one",
      logs: "docker-image-url/two"
    ]

    def operation = new CreateServerGroupAtomicOperation(description)
    operation.artifactDownloader = artifactDownloader
    operation.mapper = objectMapper

    artifactDownloader.download(_) >> new ByteArrayInputStream()
    objectMapper.readValue(_,_) >> registerTaskDefRequest

    when:
    RegisterTaskDefinitionRequest result =
      operation.makeTaskDefinitionRequestFromArtifact("test-role", new EcsServerGroupName("v1-ecs-test-v001"))

    then:
    result.getTaskRoleArn() == null
    result.getFamily() == "v1-ecs-test"
    result.getExecutionRoleArn() == "arn:aws:role/myExecutionRole"

    result.getContainerDefinitions().size() == 2

    def webContainer = result.getContainerDefinitions().find {it.getName() == "web"}
    assert webContainer != null
    webContainer.image == "docker-image-url/one"
    webContainer.memoryReservation == 512

    def logsContainer = result.getContainerDefinitions().find {it.getName() == "logs"}
    assert logsContainer != null
    logsContainer.image == "docker-image-url/two"
    logsContainer.memoryReservation == 1024

    result.getContainerDefinitions().forEach({
      it.environment.size() == 3

      def environments = [:]
      for(elem in it.environment){
        environments.put(elem.getName(), elem.getValue())
      }
      environments.get("SERVER_GROUP") == "v1-ecs-test-v001"
      environments.get("CLOUD_STACK") == "ecs"
      environments.get("CLOUD_DETAIL") == "test"
    })
  }

  def 'should set spinnaker role on LaunchType FARGATE RegisterTaskDefinitionRequest if none in artifact'() {
    given:
    def resolvedArtifact = Artifact.builder()
      .name("taskdef.json")
      .reference("fake.github.com/repos/org/repo/taskdef.json")
      .artifactAccount("my-github-acct")
      .type("github/file")
      .build()
    def containerDef =
      new ContainerDefinition()
        .withName("web")
        .withImage("PLACEHOLDER")
        .withMemoryReservation(512)
    def registerTaskDefRequest =
      new RegisterTaskDefinitionRequest().withContainerDefinitions([containerDef])
    def description = Mock(CreateServerGroupDescription)
    description.getApplication() >> 'v1'
    description.getStack() >> 'ecs'
    description.getFreeFormDetails() >> 'test'
    description.getEcsClusterName() >> 'test-cluster'
    description.getIamRole() >> 'None (No IAM role)'
    description.getLaunchType() >> 'FARGATE'
    description.getResolvedTaskDefinitionArtifact() >> resolvedArtifact
    description.getContainerToImageMap() >> [
      web: "docker-image-url"
    ]

    def operation = new CreateServerGroupAtomicOperation(description)
    operation.artifactDownloader = artifactDownloader
    operation.mapper = objectMapper

    artifactDownloader.download(_) >> new ByteArrayInputStream()
    objectMapper.readValue(_,_) >> registerTaskDefRequest

    when:
    RegisterTaskDefinitionRequest result =
      operation.makeTaskDefinitionRequestFromArtifact("test-role", new EcsServerGroupName('v1-ecs-test-v001'))

    then:
    result.getTaskRoleArn() == null
    result.getFamily() == "v1-ecs-test"
    result.getExecutionRoleArn() == "test-role"

    result.getContainerDefinitions().size() == 1
    def containerDefinition = result.getContainerDefinitions().first()
    containerDefinition.name == "web"
    containerDefinition.image == "docker-image-url"
    containerDefinition.memoryReservation == 512
  }

  def 'should set spinnaker role on CapacityProvider FARGATE RegisterTaskDefinitionRequest if none in artifact'() {
    given:
    def resolvedArtifact = Artifact.builder()
      .name("taskdef.json")
      .reference("fake.github.com/repos/org/repo/taskdef.json")
      .artifactAccount("my-github-acct")
      .type("github/file")
      .build()
    def containerDef =
      new ContainerDefinition()
        .withName("web")
        .withImage("PLACEHOLDER")
        .withMemoryReservation(512)
    def capacityProviderStrategy = new CapacityProviderStrategyItem(
      capacityProvider: 'FARGATE',
      weight: 1
    )
    def registerTaskDefRequest =
      new RegisterTaskDefinitionRequest().withContainerDefinitions([containerDef])
    def description = Mock(CreateServerGroupDescription)
    description.getApplication() >> 'v1'
    description.getStack() >> 'ecs'
    description.getFreeFormDetails() >> 'test'
    description.getEcsClusterName() >> 'test-cluster'
    description.getIamRole() >> 'None (No IAM role)'
    description.getCapacityProviderStrategy() >> [capacityProviderStrategy]
    description.getResolvedTaskDefinitionArtifact() >> resolvedArtifact
    description.getContainerToImageMap() >> [
      web: "docker-image-url"
    ]

    def operation = new CreateServerGroupAtomicOperation(description)
    operation.artifactDownloader = artifactDownloader
    operation.mapper = objectMapper

    artifactDownloader.download(_) >> new ByteArrayInputStream()
    objectMapper.readValue(_,_) >> registerTaskDefRequest

    when:
    RegisterTaskDefinitionRequest result =
      operation.makeTaskDefinitionRequestFromArtifact("test-role", new EcsServerGroupName('v1-ecs-test-v001'))

    then:
    result.getTaskRoleArn() == null
    result.getFamily() == "v1-ecs-test"
    result.getExecutionRoleArn() == "test-role"

    result.getContainerDefinitions().size() == 1
    def containerDefinition = result.getContainerDefinitions().first()
    containerDefinition.name == "web"
    containerDefinition.image == "docker-image-url"
    containerDefinition.memoryReservation == 512
  }

  def 'should fail if network mode in artifact does not match description'() {
    given:
    def resolvedArtifact = Artifact.builder()
      .name("taskdef.json")
      .reference("fake.github.com/repos/org/repo/taskdef.json")
      .artifactAccount("my-github-acct")
      .type("github/file")
      .build()
    def registerTaskDefRequest =
      new RegisterTaskDefinitionRequest()
        .withContainerDefinitions([new ContainerDefinition()])
        .withNetworkMode("bridge")
    def description = Mock(CreateServerGroupDescription)
    description.getApplication() >> 'v1'
    description.getStack() >> 'ecs'
    description.getFreeFormDetails() >> 'test'
    description.getEcsClusterName() >> 'test-cluster'
    description.getLaunchType() >> 'FARGATE'
    description.getNetworkMode() >> 'awsvpc'
    description.getResolvedTaskDefinitionArtifact() >> resolvedArtifact

    def operation = new CreateServerGroupAtomicOperation(description)
    operation.artifactDownloader = artifactDownloader
    operation.mapper = objectMapper

    artifactDownloader.download(_) >> new ByteArrayInputStream()
    objectMapper.readValue(_,_) >> registerTaskDefRequest

    when:
    operation.makeTaskDefinitionRequestFromArtifact("test-role", new EcsServerGroupName('v1-ecs-test-v001'))

    then:
    IllegalArgumentException exception = thrown()
    exception.message ==
      "Task definition networkMode does not match server group value. Found 'bridge' but expected 'awsvpc'"
  }

  def 'should set additional environment variables'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getApplication() >> 'v1'
    description.getStack() >> 'kcats'
    description.getFreeFormDetails() >> 'liated'
    description.getEnvironmentVariables() >> ["ENVIRONMENT_1" : "test1", "ENVIRONMENT_2" : "test2"]
    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    RegisterTaskDefinitionRequest result = operation.makeTaskDefinitionRequest("test-role", new EcsServerGroupName('v1-kcats-liated-v001'))

    then:
    result.getContainerDefinitions().size() == 1
    def containerDefinition = result.getContainerDefinitions().first()
    containerDefinition.environment.size() == 5
    def environments = [:]
    for(elem in containerDefinition.environment){
      environments.put(elem.getName(), elem.getValue())
    }
    environments.get("SERVER_GROUP") == "v1-kcats-liated-v001"
    environments.get("CLOUD_STACK") == "kcats"
    environments.get("CLOUD_DETAIL") == "liated"
    environments.get("ENVIRONMENT_1") == "test1"
    environments.get("ENVIRONMENT_2") == "test2"
  }

  def 'should use same port for host and container in host mode'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getTargetGroup() >> 'target-group-arn'
    description.getContainerPort() >> 10000
    description.getNetworkMode() >> 'host'
    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', new EcsServerGroupName('mygreatapp-stack1-details2-v011'))

    then:
    def portMapping = request.getContainerDefinitions().get(0).getPortMappings().get(0)
    portMapping.getHostPort() == 10000
    portMapping.getContainerPort() == 10000
    portMapping.getProtocol() == 'tcp'
  }

  def 'create a service with the same TargetGroupMappings and deprecated target group properties'() {
    given:
    def targetGroupProperty = new CreateServerGroupDescription.TargetGroupProperties(
      containerPort: 1337,
      containerName: 'v008',
      targetGroup: 'target-group-arn'
    )

    def placementConstraint = new PlacementConstraint(type: 'memberOf', expression: 'attribute:ecs.instance-type =~ t2.*')

    def placementStrategy = new PlacementStrategy(type: 'spread', field: 'attribute:ecs.availability-zone')

    def description = new CreateServerGroupDescription(
      credentials: TestCredential.named('Test', [:]),
      application: applicationName,
      stack: stack,
      freeFormDetails: detail,
      ecsClusterName: 'test-cluster',
      iamRole: 'test-role',
      containerPort: 1337,
      targetGroup: 'target-group-arn',
      targetGroupMappings: [targetGroupProperty],
      portProtocol: 'tcp',
      computeUnits: 9001,
      tags: ['label1': 'value1', 'fruit': 'tomato'],
      reservedMemory: 9002,
      dockerImageAddress: 'docker-image-url',
      capacity: new ServerGroup.Capacity(1, 1, 1),
      availabilityZones: ['us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c']],
      placementStrategySequence: [placementStrategy],
      placementConstraints: [placementConstraint],
      source: source
    )

    def operation = new CreateServerGroupAtomicOperation(description)

    operation.amazonClientProvider = amazonClientProvider
    operation.ecsCloudMetricService = Mock(EcsCloudMetricService)
    operation.iamPolicyReader = iamPolicyReader
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService

    when:
    def result = operation.operate([])

    then:
    ecs.listAccountSettings(_) >> new ListAccountSettingsResult().withSettings(
      new Setting(name: SettingName.TaskLongArnFormat, value: "enabled"),
      new Setting(name: SettingName.ServiceLongArnFormat, value: "enabled")
    )
    ecs.listServices(_) >> new ListServicesResult().withServiceArns("${serviceName}-v007")
    ecs.describeServices({DescribeServicesRequest request ->
      request.cluster == 'test-cluster'
      request.services == ["${serviceName}-v007"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "${serviceName}-v007", createdAt: new Date(), desiredCount: 3))
    ecs.describeServices(_) >> new DescribeServicesResult()

    ecs.registerTaskDefinition(_) >> new RegisterTaskDefinitionResult().withTaskDefinition(taskDefinition)
    iamClient.getRole(_) >> new GetRoleResult().withRole(role)
    iamPolicyReader.getTrustedEntities(_) >> trustRelationships
    loadBalancingV2.describeTargetGroups(_) >> new DescribeTargetGroupsResult().withTargetGroups(targetGroup)
    ecs.createService({ CreateServiceRequest request ->
      request.cluster == 'test-cluster'
      request.serviceName == 'myapp-kcats-liated-v008'
      request.taskDefinition == 'task-def-arn'
      request.loadBalancers.size() == 1
      request.loadBalancers.get(0).targetGroupArn == 'target-group-arn'
      request.loadBalancers.get(0).containerName == 'v008'
      request.loadBalancers.get(0).containerPort == 1337
      request.serviceRegistries == []
      request.desiredCount == 3
      request.role == null
      request.placementConstraints.size() == 1
      request.placementConstraints.get(0).type == 'memberOf'
      request.placementConstraints.get(0).expression == 'attribute:ecs.instance-type =~ t2.*'
      request.placementStrategy.size() == 1
      request.placementStrategy.get(0).type == 'spread'
      request.placementStrategy.get(0).field == 'attribute:ecs.availability-zone'
      request.networkConfiguration == null
      request.healthCheckGracePeriodSeconds == null
      request.enableECSManagedTags == true
      request.propagateTags == 'SERVICE'
      request.tags.size() == 2
      request.tags.get(0).key == 'label1'
      request.tags.get(0).value == 'value1'
      request.tags.get(1).key == 'fruit'
      request.tags.get(1).value == 'tomato'
      request.launchType == null
      request.platformVersion == null
    }) >> new CreateServiceResult().withService(service)

    result.getServerGroupNames().size() == 1
    result.getServerGroupNameByRegion().size() == 1
    result.getServerGroupNames().contains("us-west-1:" + serviceName + "-v008")
    result.getServerGroupNameByRegion().containsKey('us-west-1')
    result.getServerGroupNameByRegion().get('us-west-1').contains(serviceName + "-v008")

    1 * autoScalingClient.registerScalableTarget(_) >> { arguments ->
      RegisterScalableTargetRequest request = arguments.get(0)
      assert request.serviceNamespace == ServiceNamespace.Ecs.toString()
      assert request.scalableDimension == ScalableDimension.EcsServiceDesiredCount.toString()
      assert request.resourceId == "service/test-cluster/${serviceName}-v008"
      assert request.roleARN == null
      assert request.minCapacity == 2
      assert request.maxCapacity == 4
    }

    autoScalingClient.describeScalableTargets(_) >> new DescribeScalableTargetsResult()
      .withScalableTargets(new ScalableTarget()
      .withResourceId("service/test-cluster/${serviceName}-v007")
      .withMinCapacity(2)
      .withMaxCapacity(4))

    1 * operation.ecsCloudMetricService.copyScalingPolicies(
      "Test",
      "us-west-1",
      "${serviceName}-v008",
      "service/test-cluster/${serviceName}-v008",
      "test",
      "us-west-1",
      "${serviceName}-v007",
      "service/test-cluster/${serviceName}-v007",
      "test-cluster"
    )
  }

  def 'create a service with different TargetGroupMappings and deprecated target group properties'() {
    given:
    def targetGroupProperty = new CreateServerGroupDescription.TargetGroupProperties(
      containerPort: 80,
      containerName: 'v009',
      targetGroup: 'target-group-arn'
    )

    def placementConstraint = new PlacementConstraint(type: 'memberOf', expression: 'attribute:ecs.instance-type =~ t2.*')

    def placementStrategy = new PlacementStrategy(type: 'spread', field: 'attribute:ecs.availability-zone')

    def description = new CreateServerGroupDescription(
      credentials: TestCredential.named('Test', [:]),
      application: applicationName,
      stack: stack,
      freeFormDetails: detail,
      ecsClusterName: 'test-cluster',
      iamRole: 'test-role',
      containerPort: 1337,
      targetGroup: 'target-group-arn',
      targetGroupMappings: [targetGroupProperty],
      portProtocol: 'tcp',
      computeUnits: 9001,
      tags: ['label1': 'value1', 'fruit': 'tomato'],
      reservedMemory: 9002,
      dockerImageAddress: 'docker-image-url',
      capacity: new ServerGroup.Capacity(1, 1, 1),
      availabilityZones: ['us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c']],
      placementStrategySequence: [placementStrategy],
      placementConstraints: [placementConstraint],
      source: source
    )

    def operation = new CreateServerGroupAtomicOperation(description)

    operation.amazonClientProvider = amazonClientProvider
    operation.ecsCloudMetricService = Mock(EcsCloudMetricService)
    operation.iamPolicyReader = iamPolicyReader
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService

    when:
    def result = operation.operate([])

    then:
    ecs.listAccountSettings(_) >> new ListAccountSettingsResult().withSettings(
      new Setting(name: SettingName.TaskLongArnFormat, value: "enabled"),
      new Setting(name: SettingName.ServiceLongArnFormat, value: "enabled")
    )
    ecs.listServices(_) >> new ListServicesResult().withServiceArns("${serviceName}-v007")
    ecs.describeServices({DescribeServicesRequest request ->
      request.cluster == 'test-cluster'
      request.services == ["${serviceName}-v007"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "${serviceName}-v007", createdAt: new Date(), desiredCount: 3))
    ecs.describeServices(_) >> new DescribeServicesResult()

    ecs.registerTaskDefinition(_) >> new RegisterTaskDefinitionResult().withTaskDefinition(taskDefinition)
    iamClient.getRole(_) >> new GetRoleResult().withRole(role)
    iamPolicyReader.getTrustedEntities(_) >> trustRelationships
    loadBalancingV2.describeTargetGroups(_) >> new DescribeTargetGroupsResult().withTargetGroups(targetGroup)
    ecs.createService({ CreateServiceRequest request ->
      request.cluster == 'test-cluster'
      request.serviceName == 'myapp-kcats-liated-v008'
      request.taskDefinition == 'task-def-arn'
      request.loadBalancers.size() == 2
      request.loadBalancers.get(0).targetGroupArn == 'target-group-arn'
      request.loadBalancers.get(0).containerName == 'v008'
      request.loadBalancers.get(0).containerPort == 1337
      request.loadBalancers.get(1).targetGroupArn == 'target-group-arn'
      request.loadBalancers.get(1).containerName == 'v009'
      request.loadBalancers.get(1).containerPort == 80
      request.serviceRegistries == []
      request.desiredCount == 3
      request.role == null
      request.placementConstraints.size() == 1
      request.placementConstraints.get(0).type == 'memberOf'
      request.placementConstraints.get(0).expression == 'attribute:ecs.instance-type =~ t2.*'
      request.placementStrategy.size() == 1
      request.placementStrategy.get(0).type == 'spread'
      request.placementStrategy.get(0).field == 'attribute:ecs.availability-zone'
      request.networkConfiguration == null
      request.healthCheckGracePeriodSeconds == null
      request.enableECSManagedTags == true
      request.propagateTags == 'SERVICE'
      request.tags.size() == 2
      request.tags.get(0).key == 'label1'
      request.tags.get(0).value == 'value1'
      request.tags.get(1).key == 'fruit'
      request.tags.get(1).value == 'tomato'
      request.launchType == null
      request.platformVersion == null
    }) >> new CreateServiceResult().withService(service)

    result.getServerGroupNames().size() == 1
    result.getServerGroupNameByRegion().size() == 1
    result.getServerGroupNames().contains("us-west-1:" + serviceName + "-v008")
    result.getServerGroupNameByRegion().containsKey('us-west-1')
    result.getServerGroupNameByRegion().get('us-west-1').contains(serviceName + "-v008")

    1 * autoScalingClient.registerScalableTarget(_) >> { arguments ->
      RegisterScalableTargetRequest request = arguments.get(0)
      assert request.serviceNamespace == ServiceNamespace.Ecs.toString()
      assert request.scalableDimension == ScalableDimension.EcsServiceDesiredCount.toString()
      assert request.resourceId == "service/test-cluster/${serviceName}-v008"
      assert request.roleARN == null
      assert request.minCapacity == 2
      assert request.maxCapacity == 4
    }

    autoScalingClient.describeScalableTargets(_) >> new DescribeScalableTargetsResult()
      .withScalableTargets(new ScalableTarget()
      .withResourceId("service/test-cluster/${serviceName}-v007")
      .withMinCapacity(2)
      .withMaxCapacity(4))

    1 * operation.ecsCloudMetricService.copyScalingPolicies(
      "Test",
      "us-west-1",
      "${serviceName}-v008",
      "service/test-cluster/${serviceName}-v008",
      "test",
      "us-west-1",
      "${serviceName}-v007",
      "service/test-cluster/${serviceName}-v007",
      "test-cluster"
    )
  }

  def 'create a service with TargetGroupMappings'() {
    given:
    def targetGroupProperty = new CreateServerGroupDescription.TargetGroupProperties(
      containerPort: 80,
      containerName: 'v009',
      targetGroup: 'target-group-arn'
    )

    def placementConstraint = new PlacementConstraint(type: 'memberOf', expression: 'attribute:ecs.instance-type =~ t2.*')

    def placementStrategy = new PlacementStrategy(type: 'spread', field: 'attribute:ecs.availability-zone')

    def description = new CreateServerGroupDescription(
      credentials: TestCredential.named('Test', [:]),
      application: applicationName,
      stack: stack,
      freeFormDetails: detail,
      ecsClusterName: 'test-cluster',
      iamRole: 'test-role',
      targetGroupMappings: [targetGroupProperty],
      portProtocol: 'tcp',
      computeUnits: 9001,
      tags: ['label1': 'value1', 'fruit': 'tomato'],
      reservedMemory: 9002,
      dockerImageAddress: 'docker-image-url',
      capacity: new ServerGroup.Capacity(1, 1, 1),
      availabilityZones: ['us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c']],
      placementStrategySequence: [placementStrategy],
      placementConstraints: [placementConstraint],
      source: source
    )

    def operation = new CreateServerGroupAtomicOperation(description)

    operation.amazonClientProvider = amazonClientProvider
    operation.ecsCloudMetricService = Mock(EcsCloudMetricService)
    operation.iamPolicyReader = iamPolicyReader
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService

    when:
    def result = operation.operate([])

    then:
    ecs.listAccountSettings(_) >> new ListAccountSettingsResult().withSettings(
      new Setting(name: SettingName.TaskLongArnFormat, value: "enabled"),
      new Setting(name: SettingName.ServiceLongArnFormat, value: "enabled")
    )
    ecs.listServices(_) >> new ListServicesResult().withServiceArns("${serviceName}-v007")
    ecs.describeServices({DescribeServicesRequest request ->
      request.cluster == 'test-cluster'
      request.services == ["${serviceName}-v007"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "${serviceName}-v007", createdAt: new Date(), desiredCount: 3))
    ecs.describeServices(_) >> new DescribeServicesResult()

    1 * ecs.registerTaskDefinition(_) >> { arguments ->
      RegisterTaskDefinitionRequest request = arguments.get(0)
      assert request.getTaskRoleArn() == "arn:aws:iam::test:path/test-role"
      new RegisterTaskDefinitionResult().withTaskDefinition(taskDefinition)
    }

    iamClient.getRole(_) >> new GetRoleResult().withRole(role.withArn("arn:aws:iam::test:path/test-role"))
    iamPolicyReader.getTrustedEntities(_) >> trustRelationships
    loadBalancingV2.describeTargetGroups(_) >> new DescribeTargetGroupsResult().withTargetGroups(targetGroup)
    ecs.createService({ CreateServiceRequest request ->
      request.cluster == 'test-cluster'
      request.serviceName == 'myapp-kcats-liated-v008'
      request.taskDefinition == 'task-def-arn'
      request.loadBalancers.size() == 1
      request.loadBalancers.get(0).targetGroupArn == 'target-group-arn'
      request.loadBalancers.get(0).containerName == 'v009'
      request.loadBalancers.get(0).containerPort == 80
      request.serviceRegistries == []
      request.desiredCount == 3
      request.role == null
      request.placementConstraints.size() == 1
      request.placementConstraints.get(0).type == 'memberOf'
      request.placementConstraints.get(0).expression == 'attribute:ecs.instance-type =~ t2.*'
      request.placementStrategy.size() == 1
      request.placementStrategy.get(0).type == 'spread'
      request.placementStrategy.get(0).field == 'attribute:ecs.availability-zone'
      request.networkConfiguration == null
      request.healthCheckGracePeriodSeconds == null
      request.enableECSManagedTags == true
      request.propagateTags == 'SERVICE'
      request.tags.size() == 2
      request.tags.get(0).key == 'label1'
      request.tags.get(0).value == 'value1'
      request.tags.get(1).key == 'fruit'
      request.tags.get(1).value == 'tomato'
      request.launchType == null
      request.platformVersion == null
    }) >> new CreateServiceResult().withService(service)

    result.getServerGroupNames().size() == 1
    result.getServerGroupNameByRegion().size() == 1
    result.getServerGroupNames().contains("us-west-1:" + serviceName + "-v008")
    result.getServerGroupNameByRegion().containsKey('us-west-1')
    result.getServerGroupNameByRegion().get('us-west-1').contains(serviceName + "-v008")

    1 * autoScalingClient.registerScalableTarget(_) >> { arguments ->
      RegisterScalableTargetRequest request = arguments.get(0)
      assert request.serviceNamespace == ServiceNamespace.Ecs.toString()
      assert request.scalableDimension == ScalableDimension.EcsServiceDesiredCount.toString()
      assert request.resourceId == "service/test-cluster/${serviceName}-v008"
      assert request.roleARN == null
      assert request.minCapacity == 2
      assert request.maxCapacity == 4
    }

    autoScalingClient.describeScalableTargets(_) >> new DescribeScalableTargetsResult()
      .withScalableTargets(new ScalableTarget()
      .withResourceId("service/test-cluster/${serviceName}-v007")
      .withMinCapacity(2)
      .withMaxCapacity(4))

    1 * operation.ecsCloudMetricService.copyScalingPolicies(
      "Test",
      "us-west-1",
      "${serviceName}-v008",
      "service/test-cluster/${serviceName}-v008",
      "test",
      "us-west-1",
      "${serviceName}-v007",
      "service/test-cluster/${serviceName}-v007",
      "test-cluster"
    )
  }

  def 'create a service with multiple TargetGroupMappings'() {
    given:
    def originalTargetGroupProperty = new CreateServerGroupDescription.TargetGroupProperties(
      containerPort: 80,
      containerName: 'v009',
      targetGroup: 'target-group-arn'
    )

    def newTargetGroupProperty = new CreateServerGroupDescription.TargetGroupProperties(
      containerPort: 1337,
      containerName: 'v008',
      targetGroup: 'target-group-arn'
    )

    def placementConstraint = new PlacementConstraint(type: 'memberOf', expression: 'attribute:ecs.instance-type =~ t2.*')

    def placementStrategy = new PlacementStrategy(type: 'spread', field: 'attribute:ecs.availability-zone')

    def description = new CreateServerGroupDescription(
      credentials: TestCredential.named('Test', [:]),
      application: applicationName,
      stack: stack,
      freeFormDetails: detail,
      ecsClusterName: 'test-cluster',
      iamRole: 'test-role',
      targetGroupMappings: [originalTargetGroupProperty, newTargetGroupProperty],
      portProtocol: 'tcp',
      computeUnits: 9001,
      tags: ['label1': 'value1', 'fruit': 'tomato'],
      reservedMemory: 9002,
      dockerImageAddress: 'docker-image-url',
      capacity: new ServerGroup.Capacity(1, 1, 1),
      availabilityZones: ['us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c']],
      placementStrategySequence: [placementStrategy],
      placementConstraints: [placementConstraint],
      source: source
    )

    def operation = new CreateServerGroupAtomicOperation(description)

    operation.amazonClientProvider = amazonClientProvider
    operation.ecsCloudMetricService = Mock(EcsCloudMetricService)
    operation.iamPolicyReader = iamPolicyReader
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService

    when:
    def result = operation.operate([])

    then:
    ecs.listAccountSettings(_) >> new ListAccountSettingsResult().withSettings(
      new Setting(name: SettingName.TaskLongArnFormat, value: "enabled"),
      new Setting(name: SettingName.ServiceLongArnFormat, value: "enabled")
    )
    ecs.listServices(_) >> new ListServicesResult().withServiceArns("${serviceName}-v007")
    ecs.describeServices({DescribeServicesRequest request ->
      request.cluster == 'test-cluster'
      request.services == ["${serviceName}-v007"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "${serviceName}-v007", createdAt: new Date(), desiredCount: 3))
    ecs.describeServices(_) >> new DescribeServicesResult()

    ecs.registerTaskDefinition(_) >> new RegisterTaskDefinitionResult().withTaskDefinition(taskDefinition)
    iamClient.getRole(_) >> new GetRoleResult().withRole(role)
    iamPolicyReader.getTrustedEntities(_) >> trustRelationships
    loadBalancingV2.describeTargetGroups(_) >> new DescribeTargetGroupsResult().withTargetGroups(targetGroup)
    ecs.createService({ CreateServiceRequest request ->
      request.cluster == 'test-cluster'
      request.serviceName == 'myapp-kcats-liated-v008'
      request.taskDefinition == 'task-def-arn'
      request.loadBalancers.size() == 2
      request.loadBalancers.get(0).targetGroupArn == 'target-group-arn'
      request.loadBalancers.get(0).containerName == 'v008'
      request.loadBalancers.get(0).containerPort == 1337
      request.loadBalancers.get(1).targetGroupArn == 'target-group-arn'
      request.loadBalancers.get(1).containerName == 'v009'
      request.loadBalancers.get(1).containerPort == 80
      request.serviceRegistries == []
      request.desiredCount == 3
      request.role == null
      request.placementConstraints.size() == 1
      request.placementConstraints.get(0).type == 'memberOf'
      request.placementConstraints.get(0).expression == 'attribute:ecs.instance-type =~ t2.*'
      request.placementStrategy.size() == 1
      request.placementStrategy.get(0).type == 'spread'
      request.placementStrategy.get(0).field == 'attribute:ecs.availability-zone'
      request.networkConfiguration == null
      request.healthCheckGracePeriodSeconds == null
      request.enableECSManagedTags == true
      request.propagateTags == 'SERVICE'
      request.tags.size() == 2
      request.tags.get(0).key == 'label1'
      request.tags.get(0).value == 'value1'
      request.tags.get(1).key == 'fruit'
      request.tags.get(1).value == 'tomato'
      request.launchType == null
      request.platformVersion == null
    }) >> new CreateServiceResult().withService(service)

    result.getServerGroupNames().size() == 1
    result.getServerGroupNameByRegion().size() == 1
    result.getServerGroupNames().contains("us-west-1:" + serviceName + "-v008")
    result.getServerGroupNameByRegion().containsKey('us-west-1')
    result.getServerGroupNameByRegion().get('us-west-1').contains(serviceName + "-v008")

    1 * autoScalingClient.registerScalableTarget(_) >> { arguments ->
      RegisterScalableTargetRequest request = arguments.get(0)
      assert request.serviceNamespace == ServiceNamespace.Ecs.toString()
      assert request.scalableDimension == ScalableDimension.EcsServiceDesiredCount.toString()
      assert request.resourceId == "service/test-cluster/${serviceName}-v008"
      assert request.roleARN == null
      assert request.minCapacity == 2
      assert request.maxCapacity == 4
    }

    autoScalingClient.describeScalableTargets(_) >> new DescribeScalableTargetsResult()
      .withScalableTargets(new ScalableTarget()
      .withResourceId("service/test-cluster/${serviceName}-v007")
      .withMinCapacity(2)
      .withMaxCapacity(4))

    1 * operation.ecsCloudMetricService.copyScalingPolicies(
      "Test",
      "us-west-1",
      "${serviceName}-v008",
      "service/test-cluster/${serviceName}-v008",
      "test",
      "us-west-1",
      "${serviceName}-v007",
      "service/test-cluster/${serviceName}-v007",
      "test-cluster"
    )
  }

  def 'should create no tags by default'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTags() >> null

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeServiceRequest("taskDefArn", new EcsServerGroupName('mygreatapp-stack1-details2-v011'), 1,  new EcsDefaultNamer(), false)

    then:
    assert request.enableECSManagedTags == null
    assert request.propagateTags == null
    def tags = request.getTags()
    assert tags.isEmpty()
  }

  def 'should create moniker tags if enabled and no other tags'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTags() >> null

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeServiceRequest("taskDefArn", new EcsServerGroupName('mygreatapp-stack1-details2-v011'), 1, new EcsTagNamer(), true)

    then:
    assert request.enableECSManagedTags == true
    assert request.propagateTags == PropagateTags.SERVICE.toString()
    def tags = request.getTags()
    assert tags.size() == 5
    tags.contains(new Tag(key: EcsTagNamer.APPLICATION, value: 'mygreatapp'))
    tags.contains(new Tag(key: EcsTagNamer.CLUSTER, value: 'mygreatapp-stack1-details2'))
    tags.contains(new Tag(key: EcsTagNamer.STACK, value: 'stack1'))
    tags.contains(new Tag(key: EcsTagNamer.DETAIL, value: 'details2'))
    tags.contains(new Tag(key: EcsTagNamer.SEQUENCE, value: '11'))
  }

  def 'should create custom tags if moniker not enabled'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTags() >> ['label1': 'value1', 'fruit':'tomato']

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeServiceRequest("taskDefArn", new EcsServerGroupName('mygreatapp-stack1-details2-v011'), 1,  new EcsDefaultNamer(), true)

    then:
    assert request.enableECSManagedTags == true
    assert request.propagateTags == 'SERVICE'
    def tags = request.getTags()
    assert tags.size() == 2
    tags.contains(new Tag(key: 'label1', value: 'value1'))
    tags.contains(new Tag(key: 'fruit', value: 'tomato'))
  }

  def 'should create custom tags and moniker tags if moniker enabled'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTags() >> ['label1': 'value1', 'fruit':'tomato']

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeServiceRequest("taskDefArn", new EcsServerGroupName('mygreatapp-stack1-details2-v011'), 1, new EcsTagNamer(), true)

    then:
    assert request.enableECSManagedTags == true
    assert request.propagateTags == 'SERVICE'
    def tags = request.getTags()
    assert tags.size() == 7
    tags.contains(new Tag(key: 'label1', value: 'value1'))
    tags.contains(new Tag(key: 'fruit', value: 'tomato'))
    tags.contains(new Tag(key: EcsTagNamer.APPLICATION, value: 'mygreatapp'))
    tags.contains(new Tag(key: EcsTagNamer.CLUSTER, value: 'mygreatapp-stack1-details2'))
    tags.contains(new Tag(key: EcsTagNamer.STACK, value: 'stack1'))
    tags.contains(new Tag(key: EcsTagNamer.DETAIL, value: 'details2'))
    tags.contains(new Tag(key: EcsTagNamer.SEQUENCE, value: '11'))
  }

  def 'should fail to create service with custom tags and moniker tags if tags disabled'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTags() >> ['label1': 'value1', 'fruit':'tomato']

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    operation.makeServiceRequest("taskDefArn", new EcsServerGroupName('mygreatapp-stack1-details2-v011'), 1, new EcsTagNamer(), false)

    then:
    IllegalArgumentException ex = thrown()
    ex.message == "ECS account settings for account null do not allow tagging as `serviceLongArnFormat` and `taskLongArnFormat` are not enabled."
  }

  def 'should fail to create service with custom tags and no moniker tags if tags disabled'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTags() >> ['label1': 'value1', 'fruit':'tomato']

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    operation.makeServiceRequest("taskDefArn", new EcsServerGroupName('mygreatapp-stack1-details2-v011'), 1, new EcsDefaultNamer(), false)

    then:
    IllegalArgumentException ex = thrown()
    ex.message == "ECS account settings for account null do not allow tagging as `serviceLongArnFormat` and `taskLongArnFormat` are not enabled."
  }

  def 'should not create tags with duplicate keys'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    description.getApplication() >> 'mygreatapp'
    description.getTags() >> ['label1': 'value2', 'label1': 'value1']

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeServiceRequest("taskDefArn", new EcsServerGroupName('mygreatapp-v011'), 1, new EcsDefaultNamer(), true)

    then:
    assert request.enableECSManagedTags == true
    assert request.propagateTags == 'SERVICE'
    def tags = request.getTags()
    assert tags.size() == 1
    tags.contains(new Tag(key: 'label1', value: 'value1'))
  }

  def 'should fail to create service with tags if task ARN format is not updated '() {
    given:
    def description = new CreateServerGroupDescription(
      credentials: TestCredential.named('Test', [:]),
      application: applicationName,
      stack: stack,
      freeFormDetails: detail,
      ecsClusterName: 'test-cluster',
      tags: ['label1': 'value1', 'fruit': 'tomato'],
      dockerImageAddress: 'docker-image-url',
      capacity: new ServerGroup.Capacity(1, 1, 1),
      availabilityZones: ['us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c']],
    )

    def operation = new CreateServerGroupAtomicOperation(description)

    operation.amazonClientProvider = amazonClientProvider
    operation.ecsCloudMetricService = Mock(EcsCloudMetricService)
    operation.iamPolicyReader = iamPolicyReader
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService

    when:
    def result = operation.operate([])

    then:
    ecs.listAccountSettings(_) >> new ListAccountSettingsResult().withSettings(
      new Setting(name: SettingName.ServiceLongArnFormat, value: "enabled")
    )
    ecs.listServices(_) >> new ListServicesResult().withServiceArns("${serviceName}-v007")
    ecs.describeServices({DescribeServicesRequest request ->
      request.cluster == 'test-cluster'
      request.services == ["${serviceName}-v007"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "${serviceName}-v007", createdAt: new Date(), desiredCount: 3))
    ecs.describeServices(_) >> new DescribeServicesResult()
    ecs.registerTaskDefinition(_) >> new RegisterTaskDefinitionResult().withTaskDefinition(taskDefinition)

    IllegalArgumentException ex = thrown()
    ex.message == "ECS account settings for account Test do not allow tagging as `serviceLongArnFormat` and `taskLongArnFormat` are not enabled."
  }

  def 'should fail to create service with tags if service ARN format is not updated '() {
    given:
    def description = new CreateServerGroupDescription(
      credentials: TestCredential.named('Test', [:]),
      application: applicationName,
      stack: stack,
      freeFormDetails: detail,
      ecsClusterName: 'test-cluster',
      tags: ['label1': 'value1', 'fruit': 'tomato'],
      dockerImageAddress: 'docker-image-url',
      capacity: new ServerGroup.Capacity(1, 1, 1),
      availabilityZones: ['us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c']],
    )

    def operation = new CreateServerGroupAtomicOperation(description)

    operation.amazonClientProvider = amazonClientProvider
    operation.ecsCloudMetricService = Mock(EcsCloudMetricService)
    operation.iamPolicyReader = iamPolicyReader
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService

    when:
    def result = operation.operate([])

    then:
    ecs.listAccountSettings(_) >> new ListAccountSettingsResult().withSettings(
      new Setting(name: SettingName.TaskLongArnFormat, value: "enabled")
    )
    ecs.listServices(_) >> new ListServicesResult().withServiceArns("${serviceName}-v007")
    ecs.describeServices({DescribeServicesRequest request ->
      request.cluster == 'test-cluster'
      request.services == ["${serviceName}-v007"]
    }) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "${serviceName}-v007", createdAt: new Date(), desiredCount: 3))
    ecs.describeServices(_) >> new DescribeServicesResult()
    ecs.registerTaskDefinition(_) >> new RegisterTaskDefinitionResult().withTaskDefinition(taskDefinition)

    IllegalArgumentException ex = thrown()
    ex.message == "ECS account settings for account Test do not allow tagging as `serviceLongArnFormat` and `taskLongArnFormat` are not enabled."
  }

  def 'should return valid task execution role arn for non-china and non-gov-cloud partition'() {
    given:
    def credentials = Mock(NetflixAssumeRoleAmazonCredentials) {
      getName() >> { "test" }
      getRegions() >> { [new AmazonCredentials.AWSRegion('us-west-1', ['us-west-1a', 'us-west-1b'])] }
      getAssumeRole() >> { 'role/test-role' }
      getAccountId() >> { 'test' }
    }
    def resolvedArtifact = createResolvedArtifact()
    def containerDef = createContainerDef()
    def registerTaskDefRequest = createRegisterTaskDefRequest(containerDef)
    def description = createDescription(resolvedArtifact);

    def operation = new CreateServerGroupAtomicOperation(description)
    operation.artifactDownloader = artifactDownloader
    operation.mapper = objectMapper

    artifactDownloader.download(_) >> new ByteArrayInputStream()
    objectMapper.readValue(_,_) >> registerTaskDefRequest

    when:
    String ecsServiceRole = operation.inferAssumedRoleArn(credentials);
    RegisterTaskDefinitionRequest result =
      operation.makeTaskDefinitionRequestFromArtifact(ecsServiceRole, new EcsServerGroupName('v1-ecs-test-v001'))

    then:
    result.getTaskRoleArn() == null
    result.getFamily() == "v1-ecs-test"
    result.getExecutionRoleArn() == "arn:aws:iam::test:role/test-role"
  }

  def 'should return valid task execution role arn for china partition'() {
    given:
    def credentials = Mock(NetflixAssumeRoleAmazonCredentials) {
      getName() >> { "test" }
      getRegions() >> { [new AmazonCredentials.AWSRegion('cn-north-1', ['cn-north-1a', 'cn-north-1b'])] }
      getAssumeRole() >> { 'arn:aws-cn:iam:123123123123:role/test-role' }
      getAccountId() >> { 'test' }
    }
    def resolvedArtifact = createResolvedArtifact()
    def containerDef = createContainerDef()
    def registerTaskDefRequest = createRegisterTaskDefRequest(containerDef)
    def description = createDescription(resolvedArtifact);

    def operation = new CreateServerGroupAtomicOperation(description)
    operation.artifactDownloader = artifactDownloader
    operation.mapper = objectMapper

    artifactDownloader.download(_) >> new ByteArrayInputStream()
    objectMapper.readValue(_,_) >> registerTaskDefRequest

    when:
    String ecsServiceRole = operation.inferAssumedRoleArn(credentials);
    RegisterTaskDefinitionRequest result =
      operation.makeTaskDefinitionRequestFromArtifact(ecsServiceRole, new EcsServerGroupName('v1-ecs-test-v001'))

    then:
    result.getTaskRoleArn() == null
    result.getFamily() == "v1-ecs-test"
    result.getExecutionRoleArn() == "arn:aws-cn:iam:123123123123:role/test-role"
  }

  def 'should return valid task execution role arn for gov-cloud partition'() {
    given:
    def credentials = Mock(NetflixAssumeRoleAmazonCredentials) {
      getName() >> { "test" }
      getRegions() >> { [new AmazonCredentials.AWSRegion('us-west-1', ['us-west-1a', 'us-west-1b'])] }
      getAssumeRole() >> { 'arn:aws-us-gov:iam:123123123123:role/test-role' }
      getAccountId() >> { 'test' }
    }
    def resolvedArtifact = createResolvedArtifact()
    def containerDef = createContainerDef()
    def registerTaskDefRequest = createRegisterTaskDefRequest(containerDef)
    def description = createDescription(resolvedArtifact);

    def operation = new CreateServerGroupAtomicOperation(description)
    operation.artifactDownloader = artifactDownloader
    operation.mapper = objectMapper

    artifactDownloader.download(_) >> new ByteArrayInputStream()
    objectMapper.readValue(_,_) >> registerTaskDefRequest

    when:
    String ecsServiceRole = operation.inferAssumedRoleArn(credentials);
    RegisterTaskDefinitionRequest result =
      operation.makeTaskDefinitionRequestFromArtifact(ecsServiceRole, new EcsServerGroupName('v1-ecs-test-v001'))

    then:
    result.getTaskRoleArn() == null
    result.getFamily() == "v1-ecs-test"
    result.getExecutionRoleArn() == "arn:aws-us-gov:iam:123123123123:role/test-role"
  }

  def createResolvedArtifact (){
    return  Artifact.builder()
      .name("taskdef.json")
      .reference("fake.github.com/repos/org/repo/taskdef.json")
      .artifactAccount("my-github-acct")
      .type("github/file")
      .build()
  }

  def createContainerDef(){
    return new ContainerDefinition()
      .withName("web")
      .withImage("PLACEHOLDER")
      .withMemoryReservation(512)
  }

  def createRegisterTaskDefRequest(ContainerDefinition containerDef){
    return new RegisterTaskDefinitionRequest().withContainerDefinitions([containerDef])
  }

  def createDescription(Artifact resolvedArtifact){
    def description = Mock(CreateServerGroupDescription)
    description.getApplication() >> 'v1'
    description.getStack() >> 'ecs'
    description.getFreeFormDetails() >> 'test'
    description.getEcsClusterName() >> 'test-cluster'
    description.getIamRole() >> 'None (No IAM role)'
    description.getLaunchType() >> 'FARGATE'
    description.getResolvedTaskDefinitionArtifact() >> resolvedArtifact
    description.getContainerToImageMap() >> [
      web: "docker-image-url"
    ]
    return description
  }
}
