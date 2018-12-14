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

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.LoadBalancer;
import com.amazonaws.services.ecs.model.NetworkInterface;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthResult;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetDescription;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.core.provider.agent.HealthProvidingCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ContainerInstanceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskDefinitionCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.ContainerInstance;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Task;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.TaskHealth;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASK_DEFINITIONS;

public class TaskHealthCachingAgent extends AbstractEcsCachingAgent<TaskHealth> implements HealthProvidingCachingAgent {
  private static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(HEALTH.toString())
  ));
  private final static String HEALTH_ID = "ecs-task-instance-health";
  private final Logger log = LoggerFactory.getLogger(getClass());

  private Collection<String> taskEvicitions;
  private Collection<String> serviceEvicitions;
  private Collection<String> taskDefEvicitions;
  private ObjectMapper objectMapper;

  public TaskHealthCachingAgent(NetflixAmazonCredentials account, String region,
                                AmazonClientProvider amazonClientProvider,
                                AWSCredentialsProvider awsCredentialsProvider,
                                ObjectMapper objectMapper) {
    super(account, region, amazonClientProvider, awsCredentialsProvider);
    this.objectMapper = objectMapper;
  }

  public static Map<String, Object> convertTaskHealthToAttributes(TaskHealth taskHealth) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("instanceId", taskHealth.getInstanceId());

    attributes.put("state", taskHealth.getState());
    attributes.put("type", taskHealth.getType());
    attributes.put("service", taskHealth.getServiceName());
    attributes.put("taskArn", taskHealth.getTaskArn());
    attributes.put("taskId", taskHealth.getTaskId());
    return attributes;
  }

  @Override
  protected List<TaskHealth> getItems(AmazonECS ecs, ProviderCache providerCache) {
    TaskCacheClient taskCacheClient = new TaskCacheClient(providerCache, objectMapper);
    TaskDefinitionCacheClient taskDefinitionCacheClient = new TaskDefinitionCacheClient(providerCache, objectMapper);
    ServiceCacheClient serviceCacheClient = new ServiceCacheClient(providerCache, objectMapper);

    AmazonElasticLoadBalancing amazonloadBalancing = amazonClientProvider.getAmazonElasticLoadBalancingV2(account, region, false);

    ContainerInstanceCacheClient containerInstanceCacheClient = new ContainerInstanceCacheClient(providerCache);

    List<TaskHealth> taskHealthList = new LinkedList<>();
    taskEvicitions = new LinkedList<>();
    serviceEvicitions = new LinkedList<>();
    taskDefEvicitions = new LinkedList<>();

    Collection<Task> tasks = taskCacheClient.getAll(accountName, region);
    if (tasks != null) {
      for (Task task : tasks) {
        String containerInstanceCacheKey = Keys.getContainerInstanceKey(accountName, region, task.getContainerInstanceArn());
        ContainerInstance containerInstance = containerInstanceCacheClient.get(containerInstanceCacheKey);

        String serviceName = StringUtils.substringAfter(task.getGroup(), "service:");
        String serviceKey = Keys.getServiceKey(accountName, region, serviceName);
        Service service = serviceCacheClient.get(serviceKey);
        
        String taskDefinitionCacheKey = Keys.getTaskDefinitionKey(accountName, region, service.getTaskDefinition());
        TaskDefinition taskDefinition = taskDefinitionCacheClient.get(taskDefinitionCacheKey);

        if (service == null) {
          String taskEvictionKey = Keys.getTaskKey(accountName, region, task.getTaskId());
          taskEvicitions.add(taskEvictionKey);
          continue;
        }

        if (isContainerMissingNetworking(task)) {
          continue;
        }

        TaskHealth taskHealth;
        if (task.getContainers().get(0).getNetworkBindings().size() == 1) {
          taskHealth = inferHealthNetworkBindedContainer(amazonloadBalancing, task, containerInstance, serviceName, service);
        } else {
          taskHealth = inferHealthNetworkInterfacedContainer(amazonloadBalancing, task, serviceName, service, taskDefinition);
        }

        if (taskHealth != null) {
          taskHealthList.add(taskHealth);
        }
      }
    }

    return taskHealthList;
  }

  private TaskHealth inferHealthNetworkInterfacedContainer(AmazonElasticLoadBalancing amazonloadBalancing,
                                                           Task task,
                                                           String serviceName,
                                                           Service loadBalancerService,
                                                           TaskDefinition taskDefinition) {

    List<LoadBalancer> loadBalancers = loadBalancerService.getLoadBalancers();

    for (LoadBalancer loadBalancer : loadBalancers) {
      if (loadBalancer.getTargetGroupArn() == null) {
        continue;
      }

      NetworkInterface networkInterface = task.getContainers().get(0).getNetworkInterfaces().get(0);
      DescribeTargetHealthResult describeTargetHealthResult = amazonloadBalancing.describeTargetHealth(
        new DescribeTargetHealthRequest()
          .withTargetGroupArn(loadBalancer.getTargetGroupArn())
          .withTargets(
            new TargetDescription()
              .withId(networkInterface.getPrivateIpv4Address())
              .withPort(taskDefinition.getContainerDefinitions().get(0).getPortMappings().get(0).getContainerPort())
          )
      );

      if (describeTargetHealthResult.getTargetHealthDescriptions().isEmpty()) {
        evictStaleData(task, loadBalancerService);
        continue;
      }


      TargetHealthDescription healthDescription = describeTargetHealthResult.getTargetHealthDescriptions().get(0);

      TaskHealth taskHealth = makeTaskHealth(task, serviceName, healthDescription);
      return taskHealth;
    }
    return null;
  }

  private void evictStaleData(Task task, Service loadBalancerService) {
    String serviceEvictionKey = Keys.getTaskDefinitionKey(accountName, region, loadBalancerService.getServiceName());
    serviceEvicitions.add(serviceEvictionKey);
    String taskEvictionKey = Keys.getTaskKey(accountName, region, task.getTaskId());
    taskEvicitions.add(taskEvictionKey);

    String taskDefArn = loadBalancerService.getTaskDefinition();
    String taskDefKey = Keys.getTaskDefinitionKey(accountName, region, taskDefArn);
    taskDefEvicitions.add(taskDefKey);
  }

  private TaskHealth makeTaskHealth(Task task, String serviceName, TargetHealthDescription healthDescription) {
    String targetHealth = healthDescription.getTargetHealth().getState().equals("healthy") ? "Up" : "Unknown";

    TaskHealth taskHealth = new TaskHealth();
    taskHealth.setType("loadBalancer");
    taskHealth.setState(targetHealth);
    taskHealth.setServiceName(serviceName);
    taskHealth.setTaskId(task.getTaskId());
    taskHealth.setTaskArn(task.getTaskArn());
    taskHealth.setInstanceId(task.getTaskArn());
    return taskHealth;
  }

  private TaskHealth inferHealthNetworkBindedContainer(AmazonElasticLoadBalancing amazonloadBalancing,
                                                       Task task,
                                                       ContainerInstance containerInstance,
                                                       String serviceName,
                                                       Service loadBalancerService) {
    int port = task.getContainers().get(0).getNetworkBindings().get(0).getHostPort();

    List<LoadBalancer> loadBalancers = loadBalancerService.getLoadBalancers();

    for (LoadBalancer loadBalancer : loadBalancers) {
      if (loadBalancer.getTargetGroupArn() == null || containerInstance.getEc2InstanceId() == null) {
        continue;
      }

      DescribeTargetHealthResult describeTargetHealthResult;
      describeTargetHealthResult = amazonloadBalancing.describeTargetHealth(
        new DescribeTargetHealthRequest().withTargetGroupArn(loadBalancer.getTargetGroupArn()).withTargets(
          new TargetDescription().withId(containerInstance.getEc2InstanceId()).withPort(port)));

      if (describeTargetHealthResult.getTargetHealthDescriptions().isEmpty()) {
        evictStaleData(task, loadBalancerService);
        continue;
      }

      TargetHealthDescription healthDescription = describeTargetHealthResult.getTargetHealthDescriptions().get(0);

      TaskHealth taskHealth = makeTaskHealth(task, serviceName, healthDescription);
      return taskHealth;
    }

    return null;
  }

  private boolean isContainerMissingNetworking(Task task) {
    if (task.getContainers().isEmpty()) {
      return true;
    }

    if (isTaskMissingNetworkBindings(task)
      && isTaskMissingNetworkInterfaces(task)) {
      return true;
    } else {
      return false;
    }
  }

  private boolean isTaskMissingNetworkBindings(Task task) {
    return task.getContainers().isEmpty()
      || task.getContainers().get(0).getNetworkBindings() == null
      || task.getContainers().get(0).getNetworkBindings().isEmpty()
      || task.getContainers().get(0).getNetworkBindings().get(0) == null;
  }

  private boolean isTaskMissingNetworkInterfaces(Task task) {
    return task.getContainers().isEmpty()
      || task.getContainers().get(0).getNetworkInterfaces() == null
      || task.getContainers().get(0).getNetworkInterfaces().isEmpty()
      || task.getContainers().get(0).getNetworkInterfaces().get(0) == null;
  }

  @Override
  protected Map<String, Collection<CacheData>> generateFreshData(Collection<TaskHealth> taskHealthList) {
    Collection<CacheData> dataPoints = new LinkedList<>();

    for (TaskHealth taskHealth : taskHealthList) {
      Map<String, Object> attributes = convertTaskHealthToAttributes(taskHealth);

      String key = Keys.getTaskHealthKey(accountName, region, taskHealth.getTaskId());
      dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));
    }

    log.info("Caching " + dataPoints.size() + " task health checks in " + getAgentType());
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(HEALTH.toString(), dataPoints);

    return dataMap;
  }

  @Override
  protected Map<String, Collection<String>> addExtraEvictions(Map<String, Collection<String>> evictions) {
    if (!taskEvicitions.isEmpty()) {
      if (evictions.containsKey(TASKS.toString())) {
        evictions.get(TASKS.toString()).addAll(taskEvicitions);
      } else {
        evictions.put(TASKS.toString(), taskEvicitions);
      }
    }
    if (!serviceEvicitions.isEmpty()) {
      if (evictions.containsKey(SERVICES.toString())) {
        evictions.get(SERVICES.toString()).addAll(serviceEvicitions);
      } else {
        evictions.put(SERVICES.toString(), serviceEvicitions);
      }
    }
    if (!taskDefEvicitions.isEmpty()) {
      if (evictions.containsKey(TASK_DEFINITIONS.toString())) {
        evictions.get(TASK_DEFINITIONS.toString()).addAll(taskDefEvicitions);
      } else {
        evictions.put(TASK_DEFINITIONS.toString(), taskDefEvicitions);
      }
    }
    return evictions;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public String getAgentType() {
    return accountName + "/" + region + "/" + getClass().getSimpleName();
  }

  @Override
  public String getHealthId() {
    return HEALTH_ID;
  }
}
