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

import com.amazonaws.services.applicationautoscaling.model.ScalableTarget
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.Placement
import com.amazonaws.services.ecs.model.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys
import com.netflix.spinnaker.clouddriver.ecs.cache.client.*
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsLoadBalancerCache
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerCluster
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerGroup
import com.netflix.spinnaker.clouddriver.ecs.model.EcsTask
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.ServiceCachingAgent
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TaskCachingAgent
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
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
  def accountCredentialsProvider = Mock(AccountCredentialsProvider)
  def containerInformationService = Mock(ContainerInformationService)

  @Subject
  def provider = new EcsServerClusterProvider(accountCredentialsProvider,
    containerInformationService,
    taskCacheClient,
    serviceCacheClient,
    scalableTargetCacheClient,
    ecsLoadbalancerCacheClient,
    taskDefinitionCacheClient,
    ecsCloudWatchAlarmCacheClient)

  def 'should produce an ecs cluster'() {
    given:
    def applicationName = 'myapp'
    def taskId = 'task-id'
    def ip = '127.0.0.0'
    def region = 'us-west-1'
    def availabilityZone = "${region}a"
    def familyName = "${applicationName}-kcats-liated"
    def serviceName = "${familyName}-v007"
    def startedAt = new Date()

    def creds = Mock(AmazonCredentials)
    creds.getCloudProvider() >> 'ecs'
    creds.getName() >> 'test'
    creds.getRegions() >> [new AmazonCredentials.AWSRegion('us-east-1', ['us-east-1b', 'us-east-1c', 'us-east-1d']),
                           new AmazonCredentials.AWSRegion('us-west-1', ['us-west-1b', 'us-west-1c', 'us-west-1d'])]


    def cachedService = new Service(
      serviceName: serviceName,
      deploymentConfiguration: new DeploymentConfiguration(minimumHealthyPercent: 0, maximumPercent: 100),
      createdAt: startedAt,
      desiredCount: 1
    )

    def task = new Task(
      taskArn: "task-arn/${taskId}",
      clusterArn: 'cluster-arn',
      containerInstanceArn: 'container-instance-arn',
      group: 'service:' + serviceName,
      lastStatus: 'RUNNING',
      desiredStatus: 'RUNNING',
      startedAt: startedAt,
      containers: []
    )

    def loadbalancer = new EcsLoadBalancerCache()

    Map healthStatus = [
      instanceId: taskId,
      state     : 'RUNNING',
      type      : 'loadbalancer'
    ]

    def ec2Instance = new Instance(
      placement: new Placement(
        availabilityZone: availabilityZone
      )
    )

    def taskDefinition = new TaskDefinition(
      containerDefinitions: [
        new ContainerDefinition(
          image: 'my-image',
          memoryReservation: 256,
          cpu: 123,
          environment: [],
          portMappings: [new PortMapping(containerPort: 1337)]
        )
      ]
    )

    def scalableTarget = new ScalableTarget(
      minCapacity: 1,
      maxCapacity: 2,
      resourceId: "service:/mycluster/${serviceName}"
    )

    def ecsServerGroupEast = makeEcsServerGroup(serviceName, 'us-east-1', startedAt.getTime(), taskId, healthStatus, ip)
    def ecsServerGroupWest = makeEcsServerGroup(serviceName, 'us-west-1', startedAt.getTime(), taskId, healthStatus, ip)

    def expectedCluster = new EcsServerCluster()
    expectedCluster.setAccountName(creds.getName())
    expectedCluster.setName(familyName)
    expectedCluster.setServerGroups(new HashSet([ecsServerGroupEast, ecsServerGroupWest]))
    expectedCluster.setLoadBalancers(Collections.singleton(loadbalancer))


    def serviceAttributes = ServiceCachingAgent.convertServiceToAttributes(creds.getName(), creds.getRegions()[0].getName(), cachedService)
    def taskAttributes = TaskCachingAgent.convertTaskToAttributes(task)

    def serviceCacheData = new DefaultCacheData('', serviceAttributes, [:])
    def taskCacheData = new DefaultCacheData('', taskAttributes, [:])

    accountCredentialsProvider.getAll() >> [creds]
    ecsLoadbalancerCacheClient.find(_, _) >> [loadbalancer]
    containerInformationService.getTaskPrivateAddress(_, _, _) >> "${ip}:1337"
    containerInformationService.getHealthStatus(_, _, _, _) >> [healthStatus]
    containerInformationService.getEc2Instance(_, _, _) >> ec2Instance
    containerInformationService.getTaskZone(_, _, _) >> availabilityZone
    taskDefinitionCacheClient.get(_) >> taskDefinition
    scalableTargetCacheClient.get(_) >> scalableTarget
    ecsCloudWatchAlarmCacheClient.getMetricAlarms(_, _, _) >> []

    cacheView.filterIdentifiers(_, _) >> ['key']
    cacheView.getAll(Keys.Namespace.SERVICES.ns, _) >> [serviceCacheData]
    cacheView.getAll(Keys.Namespace.TASKS.ns, _) >> [taskCacheData]

    when:
    def retrievedCluster = provider.getCluster("myapp", creds.getName(), familyName)

    then:
    retrievedCluster == expectedCluster
  }

  def makeEcsServerGroup(String serviceName, String region, long startTime, String taskId, Map healthStatus, String ip) {
    new EcsServerGroup(
      name: serviceName,
      type: 'ecs',
      cloudProvider: 'ecs',
      region: region,
      disabled: false,
      createdTime: startTime,
      instances: [
        new EcsTask(taskId, startTime, 'RUNNING', 'RUNNING', "us-west-1a", [healthStatus], "${ip}:1337", null)
      ],
      securityGroups: [],
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
      metricAlarms: [],
    )
  }
}
