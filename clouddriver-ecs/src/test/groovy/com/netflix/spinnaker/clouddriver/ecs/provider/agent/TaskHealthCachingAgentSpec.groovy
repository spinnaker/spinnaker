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
import com.amazonaws.services.ecs.model.LoadBalancer
import com.amazonaws.services.ecs.model.NetworkBinding
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthResult
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealth
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthDescription
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthStateEnum
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys
import com.netflix.spinnaker.clouddriver.ecs.cache.model.TaskHealth
import spock.lang.Specification
import spock.lang.Subject

class TaskHealthCachingAgentSpec extends Specification {
  def ecs = Mock(AmazonECS)
  def clientProvider = Mock(AmazonClientProvider)
  def providerCache = Mock(ProviderCache)
  def credentialsProvider = Mock(AWSCredentialsProvider)
  ObjectMapper mapper = new ObjectMapper()

  @Subject
  TaskHealthCachingAgent agent = new TaskHealthCachingAgent(CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, clientProvider, credentialsProvider, mapper)


  def 'should get a list of task definitions'() {
    given:
    AmazonElasticLoadBalancing amazonloadBalancing = Mock(AmazonElasticLoadBalancing)
    clientProvider.getAmazonElasticLoadBalancingV2(_, _, _) >> amazonloadBalancing

    def targetGroupArn = 'arn:aws:elasticloadbalancing:' + CommonCachingAgent.REGION + ':769716316905:targetgroup/test-target-group/9e8997b7cff00c62'

    def taskKey = Keys.getTaskKey(CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, CommonCachingAgent.TASK_ID_1)
    def serviceKey = Keys.getServiceKey(CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, CommonCachingAgent.SERVICE_NAME_1)
    def containerInstanceKey = Keys.getContainerInstanceKey(CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, CommonCachingAgent.CONTAINER_INSTANCE_ARN_1)

    ObjectMapper mapper = new ObjectMapper()
    Map<String, Object> containerMap = mapper.convertValue(new Container().withNetworkBindings(new NetworkBinding().withHostPort(1337)), Map.class)
    Map<String, Object> loadbalancerMap = mapper.convertValue(new LoadBalancer().withTargetGroupArn(targetGroupArn), Map.class)

    def taskAttributes = [
      taskId               : CommonCachingAgent.TASK_ID_1,
      taskArn              : CommonCachingAgent.TASK_ARN_1,
      startedAt            : new Date().getTime(),
      containerInstanceArn: CommonCachingAgent.CONTAINER_INSTANCE_ARN_1,
      group                : 'service:' + CommonCachingAgent.SERVICE_NAME_1,
      containers           : Collections.singletonList(containerMap)
    ]
    def taskCacheData = new DefaultCacheData(taskKey, taskAttributes, Collections.emptyMap())
    providerCache.getAll(Keys.Namespace.TASKS.toString()) >> Collections.singletonList(taskCacheData)

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

    DescribeTargetHealthResult describeTargetHealthResult = new DescribeTargetHealthResult().withTargetHealthDescriptions(
      new TargetHealthDescription().withTargetHealth(new TargetHealth().withState(TargetHealthStateEnum.Healthy))
    )

    amazonloadBalancing.describeTargetHealth(_) >> describeTargetHealthResult

    when:
    def taskHealthList = agent.getItems(ecs, providerCache)

    then:
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
    dataMap.containsKey(Namespace.HEALTH.toString())
    dataMap.get(Namespace.HEALTH.toString()).size() == taskIds.size()

    for (CacheData cacheData : dataMap.get(Namespace.HEALTH.toString())) {
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
