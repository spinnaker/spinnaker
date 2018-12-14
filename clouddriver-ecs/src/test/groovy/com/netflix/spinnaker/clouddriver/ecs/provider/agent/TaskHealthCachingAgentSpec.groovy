/*
 * Copyright 2017 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.ecs.provider.agent

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.ecs.AmazonECS
import com.amazonaws.services.ecs.model.Container
import com.amazonaws.services.ecs.model.ContainerDefinition
import com.amazonaws.services.ecs.model.LoadBalancer
import com.amazonaws.services.ecs.model.NetworkBinding
import com.amazonaws.services.ecs.model.NetworkInterface
import com.amazonaws.services.ecs.model.PortMapping
import com.amazonaws.services.ecs.model.TaskDefinition
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthResult
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealth
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthDescription
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthStateEnum
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys
import com.netflix.spinnaker.clouddriver.ecs.cache.model.TaskHealth
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASK_DEFINITIONS

class TaskHealthCachingAgentSpec extends Specification {
  def ecs = Mock(AmazonECS)
  def clientProvider = Mock(AmazonClientProvider)
  def providerCache = Mock(ProviderCache)
  def credentialsProvider = Mock(AWSCredentialsProvider)
  def amazonloadBalancing = Mock(AmazonElasticLoadBalancing)
  def targetGroupArn = 'arn:aws:elasticloadbalancing:' + CommonCachingAgent.REGION + ':769716316905:targetgroup/test-target-group/9e8997b7cff00c62'
  ObjectMapper mapper = new ObjectMapper()


  @Subject
  TaskHealthCachingAgent agent = new TaskHealthCachingAgent(CommonCachingAgent.netflixAmazonCredentials, CommonCachingAgent.REGION, clientProvider, credentialsProvider, mapper)

  def setup() {
    clientProvider.getAmazonElasticLoadBalancingV2(_, _, _) >> amazonloadBalancing

    def serviceKey = Keys.getServiceKey(CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, CommonCachingAgent.SERVICE_NAME_1)
    def containerInstanceKey = Keys.getContainerInstanceKey(CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, CommonCachingAgent.CONTAINER_INSTANCE_ARN_1)

    ObjectMapper mapper = new ObjectMapper()
    Map<String, Object> loadbalancerMap = mapper.convertValue(new LoadBalancer().withTargetGroupArn(targetGroupArn), Map.class)

    providerCache.filterIdentifiers(_, _) >> []

    def serviceAttributes = [
      loadBalancers        : Collections.singletonList(loadbalancerMap),
      taskDefinition       : CommonCachingAgent.TASK_DEFINITION_ARN_1,
      desiredCount         : 1,
      maximumPercent       : 1,
      minimumHealthyPercent: 1,
      createdAt            : new Date().getTime()
    ]
    def serviceCacheData = new DefaultCacheData(serviceKey, serviceAttributes, Collections.emptyMap())
    providerCache.get(Keys.Namespace.SERVICES.toString(), serviceKey) >> serviceCacheData

    def containerInstanceAttributes = [
      ec2InstanceId: CommonCachingAgent.EC2_INSTANCE_ID_1
    ]
    def containerInstanceCache = new DefaultCacheData(containerInstanceKey, containerInstanceAttributes, Collections.emptyMap())
    providerCache.get(Keys.Namespace.CONTAINER_INSTANCES.toString(), containerInstanceKey) >> containerInstanceCache
  }

  def 'should get a list of task health'() {
    given:
    ObjectMapper mapper = new ObjectMapper()
    Map<String, Object> containerMap = mapper.convertValue(new Container().withNetworkBindings(new NetworkBinding().withHostPort(1337)), Map.class)
    def taskAttributes = [
      taskId               : CommonCachingAgent.TASK_ID_1,
      taskArn              : CommonCachingAgent.TASK_ARN_1,
      startedAt            : new Date().getTime(),
      containerInstanceArn: CommonCachingAgent.CONTAINER_INSTANCE_ARN_1,
      group                : 'service:' + CommonCachingAgent.SERVICE_NAME_1,
      containers           : Collections.singletonList(containerMap)
    ]
    def taskKey = Keys.getTaskKey(CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, CommonCachingAgent.TASK_ID_1)
    def taskCacheData = new DefaultCacheData(taskKey, taskAttributes, Collections.emptyMap())
    providerCache.getAll(TASKS.toString(), _) >> Collections.singletonList(taskCacheData)

    when:
    def taskHealthList = agent.getItems(ecs, providerCache)

    then:
    amazonloadBalancing.describeTargetHealth({ DescribeTargetHealthRequest request ->
      request.targetGroupArn == targetGroupArn
      request.targets.size() == 1
      request.targets.get(0).id == CommonCachingAgent.EC2_INSTANCE_ID_1
      request.targets.get(0).port == 1337
    }) >> new DescribeTargetHealthResult().withTargetHealthDescriptions(
      new TargetHealthDescription().withTargetHealth(new TargetHealth().withState(TargetHealthStateEnum.Healthy))
    )

    taskHealthList.size() == 1
    TaskHealth taskHealth = taskHealthList.get(0)
    taskHealth.getState() == 'Up'
    taskHealth.getType() == 'loadBalancer'
    taskHealth.getInstanceId() == CommonCachingAgent.TASK_ARN_1
    taskHealth.getServiceName() == CommonCachingAgent.SERVICE_NAME_1
    taskHealth.getTaskArn() == CommonCachingAgent.TASK_ARN_1
    taskHealth.getTaskId() == CommonCachingAgent.TASK_ID_1
  }

  def 'should get a list of task health for aws-vpc mode'() {
    given:
    ObjectMapper mapper = new ObjectMapper()
    Map<String, Object> containerMap = mapper.convertValue(new Container().withNetworkInterfaces(
      new NetworkInterface().withPrivateIpv4Address("192.168.0.100")),
      Map.class)
    def taskAttributes = [
      taskId               : CommonCachingAgent.TASK_ID_1,
      taskArn              : CommonCachingAgent.TASK_ARN_1,
      startedAt            : new Date().getTime(),
      group                : 'service:' + CommonCachingAgent.SERVICE_NAME_1,
      containers           : Collections.singletonList(containerMap)
    ]
    def taskKey = Keys.getTaskKey(CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, CommonCachingAgent.TASK_ID_1)
    def taskCacheData = new DefaultCacheData(taskKey, taskAttributes, Collections.emptyMap())
    providerCache.getAll(TASKS.toString(), _) >> Collections.singletonList(taskCacheData)

    Map<String, Object> containerDefinitionMap = mapper.convertValue(new ContainerDefinition().withPortMappings(
      new PortMapping().withContainerPort(1338)
    ), Map.class)
    def taskDefAttributes = [
      taskDefinitionArn    : CommonCachingAgent.TASK_DEFINITION_ARN_1,
      containerDefinitions : [ containerDefinitionMap ]
    ]
    def taskDefKey = Keys.getTaskDefinitionKey(CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, CommonCachingAgent.TASK_DEFINITION_ARN_1)
    def taskDefCacheData = new DefaultCacheData(taskDefKey, taskDefAttributes, Collections.emptyMap())
    providerCache.get(TASK_DEFINITIONS.toString(), taskDefKey) >> taskDefCacheData

    when:
    def taskHealthList = agent.getItems(ecs, providerCache)

    then:
    amazonloadBalancing.describeTargetHealth({ DescribeTargetHealthRequest request ->
      request.targetGroupArn == targetGroupArn
      request.targets.size() == 1
      request.targets.get(0).id == "192.168.0.100"
      request.targets.get(0).port == 1338
    }) >> new DescribeTargetHealthResult().withTargetHealthDescriptions(
      new TargetHealthDescription().withTargetHealth(new TargetHealth().withState(TargetHealthStateEnum.Healthy))
    )

    taskHealthList.size() == 1
    TaskHealth taskHealth = taskHealthList.get(0)
    taskHealth.getState() == 'Up'
    taskHealth.getType() == 'loadBalancer'
    taskHealth.getInstanceId() == CommonCachingAgent.TASK_ARN_1
    taskHealth.getServiceName() == CommonCachingAgent.SERVICE_NAME_1
    taskHealth.getTaskArn() == CommonCachingAgent.TASK_ARN_1
    taskHealth.getTaskId() == CommonCachingAgent.TASK_ID_1
  }

  def 'should generate fresh data'() {
    given:
    def taskIds = [CommonCachingAgent.TASK_ID_1, CommonCachingAgent.TASK_ID_2]

    def taskHealthList = []
    def keys = []

    for (String taskId : taskIds) {
      taskHealthList << new TaskHealth(
        taskId: taskId,
        type: 'loadBalancer',
        state: 'Up',
        instanceId: 'i-deadbeef',
        taskArn: 'task-arn',
        serviceName: 'service-name'
      )

      keys << Keys.getTaskHealthKey(CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, taskId)
    }

    when:
    def dataMap = agent.generateFreshData(taskHealthList)

    then:
    dataMap.keySet().size() == 1
    dataMap.containsKey(HEALTH.toString())
    dataMap.get(HEALTH.toString()).size() == taskIds.size()

    for (CacheData cacheData : dataMap.get(HEALTH.toString())) {
      def attributes = cacheData.getAttributes()
      keys.contains(cacheData.getId())
      taskIds.contains(attributes.get('taskId'))
      attributes.get('state') == 'Up'
      attributes.get('service') == 'service-name'
      attributes.get('taskArn') == 'task-arn'
      attributes.get('type') == 'loadBalancer'
      attributes.get('instanceId') == 'i-deadbeef'
    }
  }
}
