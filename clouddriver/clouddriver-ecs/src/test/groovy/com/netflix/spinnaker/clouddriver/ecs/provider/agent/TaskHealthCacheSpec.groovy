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

import software.amazon.awssdk.services.ecs.EcsClient
import com.netflix.spinnaker.clouddriver.aws.jackson.AwsSdkV2Module
import software.amazon.awssdk.services.ecs.model.Container
import software.amazon.awssdk.services.ecs.model.ContainerDefinition
import software.amazon.awssdk.services.ecs.model.LoadBalancer
import software.amazon.awssdk.services.ecs.model.NetworkBinding
import software.amazon.awssdk.services.ecs.model.PortMapping
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetHealthResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetDescription
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetHealth
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetHealthDescription
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetHealthStateEnum
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskHealthCacheClient
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TARGET_HEALTHS
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASK_DEFINITIONS

class TaskHealthCacheSpec extends Specification {
  def ecs = Mock(EcsClient)
  def clientProvider = Mock(AmazonClientProvider)
  def providerCache = Mock(ProviderCache)
  ObjectMapper mapper = new ObjectMapper().registerModule(new AwsSdkV2Module())

  @Subject
  TaskHealthCachingAgent agent = new TaskHealthCachingAgent(CommonCachingAgent.netflixAmazonCredentials, CommonCachingAgent.REGION, clientProvider, mapper)
  TaskHealthCacheClient client = new TaskHealthCacheClient(providerCache)


  def 'should retrieve from written cache'() {
    given:
    ElasticLoadBalancingV2Client amazonloadBalancing = Mock(ElasticLoadBalancingV2Client)
    clientProvider.getElasticLoadBalancingV2Client(_, _) >> amazonloadBalancing

    def targetGroupArn = 'arn:aws:elasticloadbalancing:' + CommonCachingAgent.REGION + ':769716316905:targetgroup/test-target-group/9e8997b7cff00c62'

    def taskKey = Keys.getTaskKey(CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, CommonCachingAgent.TASK_ID_1)
    def healthKey = Keys.getTaskHealthKey(CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, CommonCachingAgent.TASK_ID_1)
    def serviceKey = Keys.getServiceKey(CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, CommonCachingAgent.SERVICE_NAME_1)
    def containerInstanceKey = Keys.getContainerInstanceKey(CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, CommonCachingAgent.CONTAINER_INSTANCE_ARN_1)
    def targetHealthKey = Keys.getTargetHealthKey(CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, targetGroupArn)

    ObjectMapper mapper = new ObjectMapper().registerModule(new AwsSdkV2Module())
    Map<String, Object> containerMap = mapper.convertValue(Container.builder().networkBindings(NetworkBinding.builder().containerPort(1338).hostPort(1338).build()).build(), Map.class)
    Map<String, Object> loadbalancerMap = mapper.convertValue(LoadBalancer.builder().targetGroupArn(targetGroupArn).containerPort(1338).build(), Map.class)
    Map<String, Object> targetHealthMap = mapper.convertValue(
      TargetHealthDescription.builder().target(TargetDescription.builder().id(CommonCachingAgent.EC2_INSTANCE_ID_1).port(1338).build()).targetHealth(TargetHealth.builder().state(TargetHealthStateEnum.HEALTHY).build()).build(), Map.class)

    def taskAttributes = [
      taskId              : CommonCachingAgent.TASK_ID_1,
      taskArn             : CommonCachingAgent.TASK_ARN_1,
      startedAt           : new Date().getTime(),
      containerInstanceArn: CommonCachingAgent.CONTAINER_INSTANCE_ARN_1,
      group               : 'service:' + CommonCachingAgent.SERVICE_NAME_1,
      containers          : Collections.singletonList(containerMap)
    ]
    def taskCacheData = new DefaultCacheData(taskKey, taskAttributes, Collections.emptyMap())
    providerCache.filterIdentifiers(_, _) >> []
    providerCache.getAll(TASKS.toString(), _) >> Collections.singletonList(taskCacheData)

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

    def targetHealthAttributes = [
      targetGroupArn : targetGroupArn,
      targetHealthDescriptions : Collections.singletonList(targetHealthMap)
    ]

    def targetHealthCache = new DefaultCacheData(targetHealthKey, targetHealthAttributes, Collections.emptyMap())
    providerCache.get(TARGET_HEALTHS.toString(), targetHealthKey) >> targetHealthCache

    DescribeTargetHealthResponse describeTargetHealthResult = DescribeTargetHealthResponse.builder().targetHealthDescriptions(
      TargetHealthDescription.builder().targetHealth(TargetHealth.builder().state(TargetHealthStateEnum.HEALTHY).build()).build()
    ).build()

    amazonloadBalancing.describeTargetHealth(_) >> describeTargetHealthResult
    providerCache.getAll(HEALTH.toString()) >> []

    Map<String, Object> containerDefinitionMap = mapper.convertValue(ContainerDefinition.builder().portMappings(
      PortMapping.builder().hostPort(1338).build()
    ).build(), Map.class)
    def taskDefAttributes = [
      taskDefinitionArn    : CommonCachingAgent.TASK_DEFINITION_ARN_1,
      containerDefinitions : [ containerDefinitionMap ]
    ]
    def taskDefKey = Keys.getTaskDefinitionKey(CommonCachingAgent.ACCOUNT, CommonCachingAgent.REGION, CommonCachingAgent.TASK_DEFINITION_ARN_1)
    def taskDefCacheData = new DefaultCacheData(taskDefKey, taskDefAttributes, Collections.emptyMap())
    providerCache.get(TASK_DEFINITIONS.toString(), taskDefKey) >> taskDefCacheData

    when:
    def cacheResult = agent.loadData(providerCache)
    providerCache.get(HEALTH.toString(), healthKey) >> cacheResult.getCacheResults().get(HEALTH.toString()).iterator().next()

    then:
    def cacheData = cacheResult.getCacheResults().get(HEALTH.toString())
    def taskHealth = client.get(healthKey)

    cacheData != null
    cacheData.size() == 1

    def retrievedKey = cacheData.iterator().next().getId()
    retrievedKey == healthKey

    taskHealth.getState() == 'Up'
    taskHealth.getType() == 'loadBalancer'
    taskHealth.getInstanceId() == CommonCachingAgent.TASK_ARN_1
    taskHealth.getServiceName() == CommonCachingAgent.SERVICE_NAME_1
    taskHealth.getTaskArn() == CommonCachingAgent.TASK_ARN_1
    taskHealth.getTaskId() == CommonCachingAgent.TASK_ID_1
  }
}
