/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.view

import software.amazon.awssdk.services.applicationautoscaling.model.ScalableTarget
import software.amazon.awssdk.services.ec2.model.GroupIdentifier
import software.amazon.awssdk.services.ec2.model.Instance
import software.amazon.awssdk.services.ec2.model.Placement
import software.amazon.awssdk.services.ecs.model.AwsVpcConfiguration
import software.amazon.awssdk.services.ecs.model.Deployment
import software.amazon.awssdk.services.ecs.model.DeploymentConfiguration
import software.amazon.awssdk.services.ecs.model.NetworkConfiguration
import java.time.Instant
import software.amazon.awssdk.services.ecs.model.TaskDefinition
import software.amazon.awssdk.services.ecs.model.ContainerDefinition
import software.amazon.awssdk.services.ecs.model.PortMapping
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys
import com.netflix.spinnaker.clouddriver.ecs.cache.client.*
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsLoadBalancerCache
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerCluster
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerGroup
import com.netflix.spinnaker.clouddriver.ecs.model.EcsTask
import com.netflix.spinnaker.clouddriver.ecs.model.EcsTaskDefinitionRevision
import com.netflix.spinnaker.clouddriver.ecs.EcsNativeServiceTag
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.ServiceCachingAgent
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TaskCachingAgent
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TestServiceCachingAgentFactory
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService
import com.netflix.spinnaker.clouddriver.ecs.services.EcsTaskDefinitionRevisionService
import com.netflix.spinnaker.clouddriver.ecs.services.SubnetSelector
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.credentials.CredentialsRepository
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.moniker.Moniker
import spock.lang.Specification
import spock.lang.Subject

class EcsServerClusterProviderSpec extends Specification {
  def cacheView = Mock(Cache)
  def objectMapper = new ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  def taskCacheClient = new TaskCacheClient(cacheView, objectMapper)
  def serviceCacheClient = new ServiceCacheClient(cacheView, objectMapper)
  def scalableTargetCacheClient = Mock(ScalableTargetCacheClient)
  def taskDefinitionCacheClient = Mock(TaskDefinitionCacheClient)
  def ecsLoadbalancerCacheClient = Mock(EcsLoadbalancerCacheClient)
  def ecsCloudWatchAlarmCacheClient = Mock(EcsCloudWatchAlarmCacheClient)
  def credentialsRepository = Mock(CredentialsRepository)
  def containerInformationService = Mock(ContainerInformationService)
  def subnetSelector =  Mock(SubnetSelector)
  def ecsTaskDefinitionRevisionService = Mock(EcsTaskDefinitionRevisionService)

  @Subject
  def provider = new EcsServerClusterProvider(credentialsRepository,
    containerInformationService,
    subnetSelector,
    taskCacheClient,
    serviceCacheClient,
    scalableTargetCacheClient,
    ecsLoadbalancerCacheClient,
    taskDefinitionCacheClient,
    ecsCloudWatchAlarmCacheClient,
    ecsTaskDefinitionRevisionService)

  software.amazon.awssdk.services.ecs.model.Service cachedService
  TaskDefinition cachedTaskDefinition
  Instance ec2Instance
  EcsServerCluster expectedCluster
  EcsServerCluster expectedCluster2

  private static final FAMILY_NAME = 'myapp-kcats-liated'
  private static final CREDS_NAME = 'test'
  private static final CREDS_NAME_2 = 'test2'
  private static final REQUESTED_ARN = 'arn:aws:ecs:us-east-1:123456789012:task-definition/myapp-kcats-liated:3'
  private static final SIBLING_ARN = 'arn:aws:ecs:us-east-1:123456789012:task-definition/myapp-sibling:5'

  def setup() {
    def taskId = 'task-id'
    def ip = '127.0.0.0'
    def region = 'us-west-1'
    def availabilityZone = "${region}a"
    def serviceName = "${FAMILY_NAME}-v007"
    def startedAt = Instant.now()

    def creds = Mock(NetflixECSCredentials)
    creds.getCloudProvider() >> 'ecs'
    creds.getName() >> CREDS_NAME
    creds.getRegions() >> [new AmazonCredentials.AWSRegion('us-east-1', ['us-east-1b', 'us-east-1c', 'us-east-1d']),
                           new AmazonCredentials.AWSRegion('us-west-1', ['us-west-1b', 'us-west-1c', 'us-west-1d'])]

    def creds2 = Mock(NetflixECSCredentials)
    creds2.getCloudProvider() >> 'ecs'
    creds2.getName() >> CREDS_NAME_2
    creds2.getRegions() >> [new AmazonCredentials.AWSRegion('us-east-1', ['us-east-1b', 'us-east-1c', 'us-east-1d']),
                           new AmazonCredentials.AWSRegion('us-west-1', ['us-west-1b', 'us-west-1c', 'us-west-1d'])]


    cachedService = software.amazon.awssdk.services.ecs.model.Service.builder()
      .serviceName(serviceName)
      .deploymentConfiguration(DeploymentConfiguration.builder().minimumHealthyPercent(0).maximumPercent(100).build())
      .createdAt(startedAt)
      .desiredCount(1)
      .build()

    def task = software.amazon.awssdk.services.ecs.model.Task.builder()
      .taskArn("task-arn/${taskId}")
      .clusterArn('cluster-arn')
      .containerInstanceArn('container-instance-arn')
      .group('service:' + serviceName)
      .lastStatus('RUNNING')
      .desiredStatus('RUNNING')
      .startedAt(startedAt)
      .containers([])
      .build()

    def loadbalancer = new EcsLoadBalancerCache()

    Map healthStatus = [
      instanceId: taskId,
      state     : 'RUNNING',
      type      : 'loadbalancer'
    ]

    ec2Instance = Instance.builder()
      .placement(Placement.builder().availabilityZone(availabilityZone).build())
      .vpcId('vpc-1234')
      .securityGroups(GroupIdentifier.builder().groupId('sg-1234').build())
      .build()

    cachedTaskDefinition = TaskDefinition.builder()
      .containerDefinitions(
        ContainerDefinition.builder()
          .image('my-image')
          .memoryReservation(256)
          .cpu(123)
          .environment([])
          .portMappings(PortMapping.builder().containerPort(1337).build())
          .build()
      )
      .build()

    def scalableTarget = ScalableTarget.builder()
      .minCapacity(1)
      .maxCapacity(2)
      .resourceId("service:/mycluster/${serviceName}")
      .build()

    def ecsServerGroupEast = makeEcsServerGroup(serviceName, 'us-east-1', startedAt.toEpochMilli(), taskId, healthStatus, ip)
    def ecsServerGroupWest = makeEcsServerGroup(serviceName, 'us-west-1', startedAt.toEpochMilli(), taskId, healthStatus, ip)

    expectedCluster = new EcsServerCluster()
    expectedCluster.setAccountName(creds.getName())
    expectedCluster.setName(FAMILY_NAME)
    expectedCluster.setServerGroups(new LinkedHashSet([ecsServerGroupEast, ecsServerGroupWest]))
    expectedCluster.setLoadBalancers(Collections.singleton(loadbalancer))

    expectedCluster2 = new EcsServerCluster()
    expectedCluster2.setAccountName(creds2.getName())
    expectedCluster2.setName(FAMILY_NAME)
    expectedCluster2.setServerGroups(new LinkedHashSet([ecsServerGroupEast, ecsServerGroupWest]))
    expectedCluster2.setLoadBalancers(Collections.singleton(loadbalancer))

    def serviceAttributes = TestServiceCachingAgentFactory.create(creds, creds.getRegions()[0].getName()).convertServiceToAttributes(cachedService)
    def serviceAttributes2 = TestServiceCachingAgentFactory.create(creds2, creds.getRegions()[0].getName()).convertServiceToAttributes(cachedService)
    def taskAttributes = TaskCachingAgent.convertTaskToAttributes(task)

    def serviceCacheData = new DefaultCacheData('', serviceAttributes, [:])
    def serviceCacheData2 = new DefaultCacheData('', serviceAttributes2, [:])
    def taskCacheData = new DefaultCacheData('', taskAttributes, [:])

    credentialsRepository.getAll() >> [creds, creds2]
    credentialsRepository.getOne(creds.getName()) >> creds
    credentialsRepository.getOne(creds.getName()) >> creds2
    ecsLoadbalancerCacheClient.find(_, _) >> [loadbalancer]
    containerInformationService.getTaskPrivateAddress(_, _, _) >> "${ip}:1337"
    containerInformationService.getHealthStatus(_, _, _, _) >> [healthStatus]
    containerInformationService.getEc2Instance(_, _, _) >> ec2Instance
    containerInformationService.getTaskZone(_, _, _) >> 'us-west-1a'
    taskDefinitionCacheClient.get(_) >> cachedTaskDefinition
    scalableTargetCacheClient.get(_) >> scalableTarget
    ecsCloudWatchAlarmCacheClient.getMetricAlarms(_, _,_ ,_) >> []
    subnetSelector.getSubnetVpcIds(_, _, _) >> ['vpc-1234']

    cacheView.filterIdentifiers(_, _) >> ['key']
    cacheView.getAll(Keys.Namespace.SERVICES.ns, _) >> [serviceCacheData, serviceCacheData2]
    cacheView.getAll(Keys.Namespace.TASKS.ns, _) >> [taskCacheData]
    cacheView.get(Keys.Namespace.TASKS.ns, _) >> taskCacheData
  }

  def 'should produce an ecs cluster'() {
    when:
    def retrievedCluster = provider.getCluster("myapp", CREDS_NAME, FAMILY_NAME)

    then:
    retrievedCluster == expectedCluster
  }

  def 'should produce an ecs cluster with VPC network configuration'() {
    given:
    def creds = Mock(NetflixECSCredentials)
    creds.getCloudProvider() >> 'ecs'
    creds.getName() >> CREDS_NAME
    creds.getRegions() >> [new AmazonCredentials.AWSRegion('us-east-1', ['us-east-1b', 'us-east-1c', 'us-east-1d']),
                           new AmazonCredentials.AWSRegion('us-west-1', ['us-west-1b', 'us-west-1c', 'us-west-1d'])]

    def serviceWithVpc = cachedService.toBuilder()
      .networkConfiguration(NetworkConfiguration.builder()
        .awsvpcConfiguration(AwsVpcConfiguration.builder()
          .subnets(['subnet-1234'])
          .securityGroups(['sg-1234'])
          .build())
        .build())
      .build()

    def serviceAttributes = TestServiceCachingAgentFactory.create(creds, creds.getRegions()[0].getName()).convertServiceToAttributes(serviceWithVpc)
    def serviceCacheData = new DefaultCacheData('', serviceAttributes, [:])

    ec2Instance = ec2Instance.toBuilder()
      .vpcId('vpc-wrong')
      .securityGroups(GroupIdentifier.builder().groupId('sg-wrong').build())
      .build()
    // setup() already stubbed getEc2Instance with the pre-rebuild instance; v2 objects are
    // immutable (unlike v1, mutating the shared field in place no longer updates what the mock
    // returns), so re-stub with the rebuilt instance.
    containerInformationService.getEc2Instance(_, _, _) >> ec2Instance

    when:
    def retrievedCluster = provider.getCluster("myapp", CREDS_NAME, FAMILY_NAME)

    then:
    cacheView.getAll(Keys.Namespace.SERVICES.ns, _) >> [serviceCacheData]
    retrievedCluster == expectedCluster
  }

  def 'should produce an ecs cluster using Fargate'() {
    given:
    def creds = Mock(NetflixECSCredentials)
    creds.getCloudProvider() >> 'ecs'
    creds.getName() >> CREDS_NAME
    creds.getRegions() >> [new AmazonCredentials.AWSRegion('us-east-1', ['us-east-1b', 'us-east-1c', 'us-east-1d']),
                           new AmazonCredentials.AWSRegion('us-west-1', ['us-west-1b', 'us-west-1c', 'us-west-1d'])]

    def serviceWithVpc = cachedService.toBuilder()
      .networkConfiguration(NetworkConfiguration.builder()
        .awsvpcConfiguration(AwsVpcConfiguration.builder()
          .subnets(['subnet-1234'])
          .securityGroups(['sg-1234'])
          .build())
        .build())
      .build()

    def serviceAttributes = TestServiceCachingAgentFactory.create(creds, creds.getRegions()[0].getName()).convertServiceToAttributes(serviceWithVpc)
    def serviceCacheData = new DefaultCacheData('', serviceAttributes, [:])

    when:
    def retrievedCluster = provider.getCluster("myapp", CREDS_NAME, FAMILY_NAME)

    then:
    cacheView.getAll(Keys.Namespace.SERVICES.ns, _) >> [serviceCacheData]
    containerInformationService.getEc2Instance(_, _, _) >> null
    retrievedCluster == expectedCluster
  }

  def 'should produce an ecs cluster with hard memory limit'() {
    given:
    cachedTaskDefinition = TaskDefinition.builder()
      .containerDefinitions(
        ContainerDefinition.builder()
          .image('my-image')
          .environment([])
          .portMappings(PortMapping.builder().containerPort(1337).build())
          .memory(256)
          .cpu(123)
          .build()
      )
      .build()
    for (serverGroup in expectedCluster.serverGroups) {
      EcsServerGroup ecsServerGroup = serverGroup
      ecsServerGroup.taskDefinition.memoryLimit = 256
      ecsServerGroup.taskDefinition.memoryReservation = 0
    }

    when:
    def retrievedCluster = provider.getCluster("myapp", CREDS_NAME, FAMILY_NAME)

    then:
    taskDefinitionCacheClient.get(_) >> cachedTaskDefinition
    retrievedCluster == expectedCluster
  }

  def 'should produce an ecs cluster with CPU and memory set at task level'() {
    given:
    cachedTaskDefinition = TaskDefinition.builder()
      .memory('256')
      .cpu('123')
      .containerDefinitions(
        ContainerDefinition.builder()
          .image('my-image')
          .environment([])
          .portMappings(PortMapping.builder().containerPort(1337).build())
          .build()
      )
      .build()
    for (serverGroup in expectedCluster.serverGroups) {
      EcsServerGroup ecsServerGroup = serverGroup
      ecsServerGroup.taskDefinition.memoryLimit = 256
      ecsServerGroup.taskDefinition.memoryReservation = 0
    }

    when:
    def retrievedCluster = provider.getCluster("myapp", CREDS_NAME, FAMILY_NAME)

    then:
    taskDefinitionCacheClient.get(_) >> cachedTaskDefinition
    retrievedCluster == expectedCluster
  }

  def 'should produce an ecs cluster with zero port mappings'() {
    given:
    cachedTaskDefinition = TaskDefinition.builder()
      .containerDefinitions(
        ContainerDefinition.builder()
          .image('my-image')
          .environment([])
          .memoryReservation(256)
          .cpu(123)
          .build()
      )
      .build()
    for (serverGroup in expectedCluster.serverGroups) {
      EcsServerGroup ecsServerGroup = serverGroup
      ecsServerGroup.taskDefinition.containerPort = 0
    }

    when:
    def retrievedCluster = provider.getCluster("myapp", CREDS_NAME, FAMILY_NAME)

    then:
    taskDefinitionCacheClient.get(_) >> cachedTaskDefinition
    retrievedCluster == expectedCluster
  }

  def 'should produce an ecs cluster with unknown VPC for missing EC2 instance'() {
    given:
    for (serverGroup in expectedCluster.serverGroups) {
      EcsServerGroup ecsServerGroup = serverGroup
      ecsServerGroup.vpcId = 'None'
      ecsServerGroup.securityGroups = []
    }

    when:
    def retrievedCluster = provider.getCluster("myapp", CREDS_NAME, FAMILY_NAME)

    then:
    containerInformationService.getEc2Instance(_, _, _) >> null
    retrievedCluster == expectedCluster
  }

  def 'should produce an ecs cluster with unknown VPC for EC2 instance missing its network config'() {
    given:
    for (serverGroup in expectedCluster.serverGroups) {
      EcsServerGroup ecsServerGroup = serverGroup
      ecsServerGroup.vpcId = 'None'
      ecsServerGroup.securityGroups = []
    }

    when:
    def retrievedCluster = provider.getCluster("myapp", CREDS_NAME, FAMILY_NAME)

    then:
    containerInformationService.getEc2Instance(_, _, _) >> Instance.builder().build()
    retrievedCluster == expectedCluster
  }

  def 'should produce ecs clusters'() {
    when:
    def retrievedClusters = provider.getClusterDetails("myapp").values().flatten()

    then:
    retrievedClusters.sort() == [expectedCluster, expectedCluster2].sort()
  }

  def 'groups a classic versioned service and the ecs-native fixed-name service into one Spinnaker cluster'() {
    given: 'both services cached for one account, with monikers derived from their names by the caching agent'
    def creds = Mock(NetflixECSCredentials)
    creds.getCloudProvider() >> 'ecs'
    creds.getName() >> CREDS_NAME
    creds.getRegions() >> [new AmazonCredentials.AWSRegion('us-west-1', ['us-west-1b', 'us-west-1c', 'us-west-1d'])]
    def agent = TestServiceCachingAgentFactory.create(creds, 'us-west-1')
    def classicService = cachedService                                          // myapp-kcats-liated-v007, untagged
    def nativeService = cachedService.toBuilder()
      .serviceName(FAMILY_NAME)                                                 // myapp-kcats-liated, no sequence
      .tags(EcsNativeServiceTag.tag())
      .build()
    def classicData = new DefaultCacheData('', agent.convertServiceToAttributes(classicService), [:])
    def nativeData = new DefaultCacheData('', agent.convertServiceToAttributes(nativeService), [:])

    when:
    def clusters = provider.getClusterDetails('myapp')

    then:
    credentialsRepository.getAll() >> [creds]
    cacheView.getAll(Keys.Namespace.SERVICES.ns, _) >> [classicData, nativeData]

    clusters.keySet() == ['myapp'] as Set
    def appClusters = clusters['myapp'] as Set<EcsServerCluster>
    appClusters.size() == 1
    def cluster = appClusters.first()
    cluster.name == FAMILY_NAME
    cluster.accountName == CREDS_NAME
    cluster.serverGroups*.name as Set == [FAMILY_NAME, "${FAMILY_NAME}-v007".toString()] as Set

    def nativeGroup = cluster.serverGroups.find { it.name == FAMILY_NAME } as EcsServerGroup
    nativeGroup.getIsNative() == true
    nativeGroup.moniker.cluster == FAMILY_NAME
    nativeGroup.moniker.sequence == null

    def classicGroup = cluster.serverGroups.find { it.name == "${FAMILY_NAME}-v007" } as EcsServerGroup
    classicGroup.getIsNative() == null
    classicGroup.moniker.cluster == FAMILY_NAME
    classicGroup.moniker.sequence == 7
  }

  def 'should surface the task definition revision and ECS rollout state on the server group'() {
    given: 'a cached service with a PRIMARY deployment and a task-definition ARN carrying a revision'
    def creds = Mock(NetflixECSCredentials)
    creds.getCloudProvider() >> 'ecs'
    creds.getName() >> CREDS_NAME
    creds.getRegions() >> [new AmazonCredentials.AWSRegion('us-east-1', ['us-east-1b', 'us-east-1c', 'us-east-1d']),
                           new AmazonCredentials.AWSRegion('us-west-1', ['us-west-1b', 'us-west-1c', 'us-west-1d'])]

    def serviceWithDeployment = cachedService.toBuilder()
      .deployments(
        Deployment.builder()
          .status('PRIMARY')
          .id('ecs-svc/9876543210')
          .rolloutState('IN_PROGRESS')
          .rolloutStateReason('ECS deployment is in progress.')
          .build())
      .build()

    def serviceAttributes = TestServiceCachingAgentFactory.create(creds, creds.getRegions()[0].getName()).convertServiceToAttributes(serviceWithDeployment)
    def serviceCacheData = new DefaultCacheData('', serviceAttributes, [:])

    cachedTaskDefinition = TaskDefinition.builder()
      .taskDefinitionArn('arn:aws:ecs:us-west-1:123456789012:task-definition/myapp-stack-detail:42')
      .containerDefinitions(
        ContainerDefinition.builder()
          .image('my-image')
          .memoryReservation(256)
          .cpu(123)
          .environment([])
          .portMappings(PortMapping.builder().containerPort(1337).build())
          .build())
      .build()

    when:
    def retrievedCluster = provider.getCluster("myapp", CREDS_NAME, FAMILY_NAME)

    then:
    cacheView.getAll(Keys.Namespace.SERVICES.ns, _) >> [serviceCacheData]
    taskDefinitionCacheClient.get(_) >> cachedTaskDefinition
    def serverGroup = retrievedCluster.serverGroups.iterator().next() as EcsServerGroup
    serverGroup.taskDefinitionRevision == 42
    serverGroup.deploymentId == 'ecs-svc/9876543210'
    serverGroup.rolloutState == 'IN_PROGRESS'
    serverGroup.rolloutStateReason == 'ECS deployment is in progress.'
    // The fixture service has no ecs-native ownership tag, so it is treated as external/classic
    // regardless of its versioned name.
    serverGroup.getIsNative() == null
  }

  def 'resolves the ECS account by exact name for server-group lookups'() {
    when:
    def serverGroup = provider.getServerGroup('TEST', 'us-east-1', 'myapp-kcats-liated-v007', true)

    then: 'classic ecs keeps CredentialsRepository#getOne exact-match semantics'
    serverGroup == null
    0 * credentialsRepository.getAll()
  }

  def 'attaches task-definition revisions only to the requested native server group (includeDetails=#includeDetails)'() {
    given: 'two ecs-native services in the same application'
    def requested = nativeServiceCacheData("${FAMILY_NAME}-v007", REQUESTED_ARN)
    def sibling = nativeServiceCacheData('myapp-sibling', SIBLING_ARN)
    def revisions = [EcsTaskDefinitionRevision.builder().taskDefinitionArn(REQUESTED_ARN).revision(3).current(true).build()]

    when:
    def serverGroup = provider.getServerGroup(CREDS_NAME, 'us-east-1', "${FAMILY_NAME}-v007", includeDetails) as EcsServerGroup

    then:
    cacheView.getAll(Keys.Namespace.SERVICES.ns, _) >> [requested, sibling]
    cacheView.get(Keys.Namespace.SERVICES.ns, Keys.getServiceKey(CREDS_NAME, 'us-east-1', "${FAMILY_NAME}-v007")) >> requested
    // Exactly one live call, for the requested service in the requested region: none for the
    // sibling service or for the same service in the account's other region.
    1 * ecsTaskDefinitionRevisionService.listRevisions(_, 'us-east-1', REQUESTED_ARN) >> revisions
    0 * ecsTaskDefinitionRevisionService._
    serverGroup.getIsNative()
    serverGroup.taskDefinitionRevisions == revisions
    serverGroup.extraAttributes.taskDefinitionRevisions == revisions

    where:
    // Deck's details pane (the rollback picker's data source) requests includeDetails=false, so
    // the revisions must be attached either way.
    includeDetails << [true, false]
  }

  def 'does not list task-definition revisions for cluster or application listings'() {
    given:
    def requested = nativeServiceCacheData("${FAMILY_NAME}-v007", REQUESTED_ARN)
    def sibling = nativeServiceCacheData('myapp-sibling', SIBLING_ARN)

    when:
    def cluster = provider.getCluster('myapp', CREDS_NAME, FAMILY_NAME)
    def accountClusters = provider.getClusters('myapp', CREDS_NAME)
    def details = provider.getClusterDetails('myapp')

    then:
    cacheView.getAll(Keys.Namespace.SERVICES.ns, _) >> [requested, sibling]
    0 * ecsTaskDefinitionRevisionService._
    cluster.serverGroups.every { (it as EcsServerGroup).getIsNative() && it.taskDefinitionRevisions == null }
    accountClusters.serverGroups.flatten().every { it.taskDefinitionRevisions == null }
    details.values().flatten().serverGroups.flatten().every { it.taskDefinitionRevisions == null }
  }

  def 'does not list task-definition revisions for a classic server group'() {
    when:
    def serverGroup = provider.getServerGroup(CREDS_NAME, 'us-east-1', "${FAMILY_NAME}-v007", true) as EcsServerGroup

    then:
    0 * ecsTaskDefinitionRevisionService._
    serverGroup.getIsNative() == null
    serverGroup.taskDefinitionRevisions == null
  }

  private DefaultCacheData nativeServiceCacheData(String serviceName, String taskDefinitionArn) {
    def creds = Mock(NetflixECSCredentials)
    creds.getName() >> CREDS_NAME
    def service = cachedService.toBuilder()
      .serviceName(serviceName)
      .taskDefinition(taskDefinitionArn)
      .tags(EcsNativeServiceTag.tag())
      .build()
    def attributes = TestServiceCachingAgentFactory.create(creds, 'us-east-1').convertServiceToAttributes(service)
    return new DefaultCacheData('', attributes, [:])
  }

  def makeEcsServerGroup(String serviceName, String region, long startTime, String taskId, Map healthStatus, String ip) {
    Names name = Names.parseName(serviceName)
    new EcsServerGroup(
      name: serviceName,
      type: 'ecs',
      cloudProvider: 'ecs',
      region: region,
      disabled: false,
      createdTime: startTime,
      instances: [
        new EcsTask(taskId, startTime, 'RUNNING', 'RUNNING', 'HEALTHY', "us-west-1a", [healthStatus], "${ip}:1337", null, true)
      ],
      vpcId: 'vpc-1234',
      securityGroups: ['sg-1234'],
      instanceCounts: new ServerGroup.InstanceCounts(
        total: 1,
        up: 1,
        down: 0,
        unknown: 0,
        outOfService: 0,
        starting: 0
      ),
      capacity: new ServerGroup.Capacity(
        min: 1,
        max: 2,
        desired: 1
      ),
      taskDefinition: new com.netflix.spinnaker.clouddriver.ecs.model.TaskDefinition(
        containerImage: 'my-image',
        containerPort: 1337,
        cpuUnits: 123,
        memoryReservation: 256,
        environmentVariables: [],
        iamRole: 'None'
      ),
      image: new EcsServerGroup.Image(
        imageId: 'my-image',
        name: 'my-image'
      ),
      metricAlarms: [],
      moniker: Moniker.builder()
        .app(name.app)
        .stack(name.stack)
        .detail(name.detail)
        .sequence(name.sequence)
        .cluster(name.cluster)
        .build()
    )
  }
}
