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
import com.amazonaws.services.ecs.model.*
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.model.GetRoleResult
import com.amazonaws.services.identitymanagement.model.Role
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.AssumeRoleAmazonCredentials
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CreateServerGroupDescription
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.IamPolicyReader
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.IamTrustRelationship
import com.netflix.spinnaker.clouddriver.ecs.services.EcsCloudMetricService
import com.netflix.spinnaker.clouddriver.ecs.services.SecurityGroupSelector
import com.netflix.spinnaker.clouddriver.ecs.services.SubnetSelector
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.fiat.model.resources.Permissions

import static com.netflix.spinnaker.clouddriver.ecs.deploy.ops.CreateServerGroupAtomicOperation.DOCKER_LABEL_KEY_SERVERGROUP
import static com.netflix.spinnaker.clouddriver.ecs.deploy.ops.CreateServerGroupAtomicOperation.NO_IAM_ROLE

class CreateServerGroupAtomicOperationSpec extends CommonAtomicOperation {
  def iamClient = Mock(AmazonIdentityManagement)
  def iamPolicyReader = Mock(IamPolicyReader)
  def loadBalancingV2 = Mock(AmazonElasticLoadBalancing)
  def autoScalingClient = Mock(AWSApplicationAutoScaling)
  def subnetSelector = Mock(SubnetSelector)
  def securityGroupSelector = Mock(SecurityGroupSelector)

  def applicationName = 'myapp'
  def stack = 'kcats'
  def detail = 'liated'
  def serviceName = "${applicationName}-${stack}-${detail}"

  def trustRelationships = [new IamTrustRelationship(type: 'Service', value: 'ecs-tasks.amazonaws.com'),
                            new IamTrustRelationship(type: 'Service', value: 'ecs.amazonaws.com')]

  def role = new Role(assumeRolePolicyDocument: "json-encoded-string-here")

  def creds = new AssumeRoleAmazonCredentials("test", "test", "test", "test", "test",
    [new AmazonCredentials.AWSRegion('us-west-1', ['us-west-1a', 'us-west-1b'])],
    [], [], Permissions.factory([:]), [], false, 'test-role', "test")

  def taskDefinition = new TaskDefinition().withTaskDefinitionArn("task-def-arn")

  def targetGroup = new TargetGroup().withLoadBalancerArns("loadbalancer-arn").withTargetGroupArn('target-group-arn')

  def service = new Service(serviceName: "${serviceName}")

  def 'should create a service'() {
    given:
    def description = new CreateServerGroupDescription(
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
      autoscalingPolicies: [],
      placementStrategySequence: []
    )

    def operation = new CreateServerGroupAtomicOperation(description)

    operation.amazonClientProvider = amazonClientProvider
    operation.ecsCloudMetricService = Mock(EcsCloudMetricService)
    operation.iamPolicyReader = iamPolicyReader
    operation.accountCredentialsProvider = accountCredentialsProvider
    operation.containerInformationService = containerInformationService

    amazonClientProvider.getAmazonEcs(_, _, _) >> ecs
    amazonClientProvider.getAmazonIdentityManagement(_, _, _) >> iamClient
    amazonClientProvider.getAmazonElasticLoadBalancingV2(_, _, _) >> loadBalancingV2
    amazonClientProvider.getAmazonApplicationAutoScaling(_, _, _) >> autoScalingClient
    containerInformationService.getClusterName(_, _, _) >> 'cluster-name'
    accountCredentialsProvider.getCredentials(_) >> creds

    when:
    def result = operation.operate([])

    then:
    1 * ecs.listServices(_) >> new ListServicesResult().withServiceArns("${serviceName}-v007")

    1 * ecs.registerTaskDefinition({RegisterTaskDefinitionRequest request ->
      request.containerDefinitions.size() == 1
      request.containerDefinitions.get(0).memoryReservation == 9002
      request.containerDefinitions.get(0).cpu == 9001
      request.containerDefinitions.get(0).portMappings.size() == 1
      request.containerDefinitions.get(0).portMappings.get(0).containerPort == 1337
      request.containerDefinitions.get(0).portMappings.get(0).hostPort == 0
      request.containerDefinitions.get(0).portMappings.get(0).protocol == 'tcp'
      request.containerDefinitions.get(0).image == 'docker-image-url'
      request.taskRoleArn == 'test-role'
    }) >> new RegisterTaskDefinitionResult().withTaskDefinition(taskDefinition)

    1 * iamClient.getRole(_) >> new GetRoleResult().withRole(role)
    1 * iamPolicyReader.getTrustedEntities(_) >> trustRelationships
    1 * loadBalancingV2.describeTargetGroups(_) >> new DescribeTargetGroupsResult().withTargetGroups(targetGroup)

    1 * ecs.createService({ CreateServiceRequest request ->
      request.serviceName.startsWith(serviceName)
      request.desiredCount == 1
      request.cluster = 'test-cluster'
      request.loadBalancers.size() == 1
      request.loadBalancers.get(0).containerPort == 1337
      request.loadBalancers.get(0).targetGroupArn == 'target-group-arn'
      request.taskDefinition == 'task-def-arn'
      request.networkConfiguration == null
      request.role == 'arn:aws:iam::test:test-role'
    } as CreateServiceRequest) >> new CreateServiceResult().withService(service)

    result.getServerGroupNames().size() == 1
    result.getServerGroupNameByRegion().size() == 1
    result.getServerGroupNames().contains("us-west-1:" + serviceName)
    result.getServerGroupNameByRegion().containsKey('us-west-1')
    result.getServerGroupNameByRegion().get('us-west-1').contains(serviceName)
  }

  def 'should create a service using VPC and Fargate mode'() {
    given:
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
      autoscalingPolicies: [],
      placementStrategySequence: [],
      launchType: 'FARGATE',
      networkMode: 'awsvpc',
      subnetType: 'public',
      securityGroupNames: ['helloworld'],
      associatePublicIpAddress: true
    )

    def operation = new CreateServerGroupAtomicOperation(description)

    operation.amazonClientProvider = amazonClientProvider
    operation.ecsCloudMetricService = Mock(EcsCloudMetricService)
    operation.iamPolicyReader = iamPolicyReader
    operation.accountCredentialsProvider = accountCredentialsProvider
    operation.containerInformationService = containerInformationService
    operation.subnetSelector = subnetSelector
    operation.securityGroupSelector = securityGroupSelector

    amazonClientProvider.getAmazonEcs(_, _, _) >> ecs
    amazonClientProvider.getAmazonIdentityManagement(_, _, _) >> iamClient
    amazonClientProvider.getAmazonElasticLoadBalancingV2(_, _, _) >> loadBalancingV2
    amazonClientProvider.getAmazonApplicationAutoScaling(_, _, _) >> autoScalingClient
    containerInformationService.getClusterName(_, _, _) >> 'cluster-name'
    accountCredentialsProvider.getCredentials(_) >> creds

    subnetSelector.resolveSubnetsIds(_, _, _) >> ['subnet-12345']
    subnetSelector.getSubnetVpcIds(_, _, _) >> ['vpc-123']
    securityGroupSelector.resolveSecurityGroupNames(_, _, _, _) >> ['sg-12345']

    when:
    def result = operation.operate([])

    then:
    1 * ecs.listServices(_) >> new ListServicesResult().withServiceArns("${serviceName}-v007")

    1 * ecs.registerTaskDefinition({RegisterTaskDefinitionRequest request ->
      request.networkMode == 'awsvpc'
      request.containerDefinitions.size() == 1
      request.containerDefinitions.get(0).portMappings.size() == 1
      request.containerDefinitions.get(0).portMappings.get(0).containerPort == 1337
      request.containerDefinitions.get(0).portMappings.get(0).hostPort == 0
      request.containerDefinitions.get(0).portMappings.get(0).protocol == 'tcp'
      request.requiresCompatibilities.size() == 1
      request.requiresCompatibilities.get(0) == 'FARGATE'
      request.memory == 9001
      request.cpu == 9002
      request.executionRoleArn == 'arn:aws:iam::test:test-role'
    }) >> new RegisterTaskDefinitionResult().withTaskDefinition(taskDefinition)

    1 * iamClient.getRole(_) >> new GetRoleResult().withRole(role)
    1 * iamPolicyReader.getTrustedEntities(_) >> trustRelationships
    1 * loadBalancingV2.describeTargetGroups(_) >> new DescribeTargetGroupsResult().withTargetGroups(targetGroup)

    1 * ecs.createService({ CreateServiceRequest request ->
      request.networkConfiguration.awsvpcConfiguration.subnets == ['subnet-12345']
      request.networkConfiguration.awsvpcConfiguration.securityGroups == ['sg-12345']
      request.networkConfiguration.awsvpcConfiguration.assignPublicIp == 'ENABLED'
      request.role == null
      request.launchType == 'FARGATE'
    } as CreateServiceRequest) >> new CreateServiceResult().withService(service)

    result.getServerGroupNames().size() == 1
    result.getServerGroupNameByRegion().size() == 1
    result.getServerGroupNames().contains("us-west-1:" + serviceName)
    result.getServerGroupNameByRegion().containsKey('us-west-1')
    result.getServerGroupNameByRegion().get('us-west-1').contains(serviceName)
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
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'v0011')

    then:
    def labels = request.getContainerDefinitions().get(0).getDockerLabels()
    labels.get(DOCKER_LABEL_KEY_SERVERGROUP) == 'mygreatapp-stack1-details2-v0011'
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
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'v0011')

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
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'v0011')

    then:
    def labels = request.getContainerDefinitions().get(0).getDockerLabels()
    labels.get(DOCKER_LABEL_KEY_SERVERGROUP) == 'mygreatapp-stack1-details2-v0011'
    labels.get(DOCKER_LABEL_KEY_SERVERGROUP) != 'some-value-we-dont-want-to-see'
  }


  def 'should allow selecting the logDriver'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getLogDriver() >> 'some-log-driver'
    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'v0011')

    then:
    request.getContainerDefinitions().get(0).getLogConfiguration().getLogDriver() == 'some-log-driver'
  }

  def 'should allow empty logOptions'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getLogDriver() >> 'some-log-driver'
    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'v0011')

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
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'v0011')

    then:
    request.getContainerDefinitions().get(0).getLogConfiguration().getOptions() == logOptions
  }

  def 'should allow using secret credentials for the docker image'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getDockerImageCredentialsSecret() >> 'my-secret'

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'v0011')

    then:
    request.getContainerDefinitions().get(0).getRepositoryCredentials().getCredentialsParameter() == 'my-secret'
  }

  def 'should allow not specifying secret credentials for the docker image'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'v0011')

    then:
    request.getContainerDefinitions().get(0).getRepositoryCredentials() == null
  }

  def 'should generate a RegisterTaskDefinitionRequest object'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getApplication() >> 'v1'
    description.getStack() >> 'kcats'
    description.getFreeFormDetails() >> 'liated'
    description.ecsClusterName = 'test-cluster'
    description.iamRole = 'None (No IAM role)'
    description.getContainerPort() >> 1337
    description.targetGroup = 'target-group-arn'
    description.getPortProtocol() >> 'tcp'
    description.getComputeUnits() >> 9001
    description.getReservedMemory() >> 9001
    description.getDockerImageAddress() >> 'docker-image-url'
    description.capacity = new ServerGroup.Capacity(1, 1, 1)
    description.availabilityZones = ['us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c']]
    description.autoscalingPolicies = []
    description.placementStrategySequence = []

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    RegisterTaskDefinitionRequest result = operation.makeTaskDefinitionRequest("test-role", "v1")

    then:
    result.getTaskRoleArn() == null
    result.getFamily() == "v1-kcats-liated"

    result.getContainerDefinitions().size() == 1
    def containerDefinition = result.getContainerDefinitions().first()
    containerDefinition.name == 'v1'
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
    environments.get("SERVER_GROUP") == "v1"
    environments.get("CLOUD_STACK") == "kcats"
    environments.get("CLOUD_DETAIL") == "liated"
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
    RegisterTaskDefinitionRequest result = operation.makeTaskDefinitionRequest("test-role", "v1")

    then:
    result.getContainerDefinitions().size() == 1
    def containerDefinition = result.getContainerDefinitions().first()
    containerDefinition.environment.size() == 5
    def environments = [:]
    for(elem in containerDefinition.environment){
      environments.put(elem.getName(), elem.getValue())
    }
    environments.get("SERVER_GROUP") == "v1"
    environments.get("CLOUD_STACK") == "kcats"
    environments.get("CLOUD_DETAIL") == "liated"
    environments.get("ENVIRONMENT_1") == "test1"
    environments.get("ENVIRONMENT_2") == "test2"
  }
}
