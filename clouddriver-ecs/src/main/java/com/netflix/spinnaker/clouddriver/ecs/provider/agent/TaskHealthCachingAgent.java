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

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASK_DEFINITIONS;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.Container;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.LoadBalancer;
import com.amazonaws.services.ecs.model.NetworkBinding;
import com.amazonaws.services.ecs.model.NetworkInterface;
import com.amazonaws.services.ecs.model.PortMapping;
import com.amazonaws.services.ecs.model.TaskDefinition;
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
import com.netflix.spinnaker.clouddriver.ecs.cache.client.*;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskHealthCachingAgent extends AbstractEcsCachingAgent<TaskHealth>
    implements HealthProvidingCachingAgent {
  private static final Collection<AgentDataType> types =
      Collections.unmodifiableCollection(Arrays.asList(AUTHORITATIVE.forType(HEALTH.toString())));
  private static final String HEALTH_ID = "ecs-task-instance-health";
  private static final String STATUS_UP = "Up";
  private static final String STATUS_UNKNOWN = "Unknown";
  private final Logger log = LoggerFactory.getLogger(getClass());

  private Collection<String> taskEvictions;
  private Collection<String> serviceEvictions;
  private Collection<String> taskDefEvictions;
  private ObjectMapper objectMapper;

  public TaskHealthCachingAgent(
      NetflixAmazonCredentials account,
      String region,
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
    TaskDefinitionCacheClient taskDefinitionCacheClient =
        new TaskDefinitionCacheClient(providerCache, objectMapper);
    ServiceCacheClient serviceCacheClient = new ServiceCacheClient(providerCache, objectMapper);

    TargetHealthCacheClient targetHealthCacheClient =
        new TargetHealthCacheClient(providerCache, objectMapper);

    ContainerInstanceCacheClient containerInstanceCacheClient =
        new ContainerInstanceCacheClient(providerCache);

    List<TaskHealth> taskHealthList = new LinkedList<>();
    taskEvictions = new LinkedList<>();
    serviceEvictions = new LinkedList<>();
    taskDefEvictions = new LinkedList<>();

    Collection<Task> tasks = taskCacheClient.getAll(accountName, region);
    if (tasks != null) {
      for (Task task : tasks) {
        String containerInstanceCacheKey =
            Keys.getContainerInstanceKey(accountName, region, task.getContainerInstanceArn());
        ContainerInstance containerInstance =
            containerInstanceCacheClient.get(containerInstanceCacheKey);

        String serviceName = StringUtils.substringAfter(task.getGroup(), "service:");
        String serviceKey = Keys.getServiceKey(accountName, region, serviceName);
        Service service = serviceCacheClient.get(serviceKey);

        if (service == null) {
          String taskEvictionKey = Keys.getTaskKey(accountName, region, task.getTaskId());
          taskEvictions.add(taskEvictionKey);
          continue;
        }

        String taskDefinitionCacheKey =
            Keys.getTaskDefinitionKey(accountName, region, service.getTaskDefinition());
        TaskDefinition taskDefinition = taskDefinitionCacheClient.get(taskDefinitionCacheKey);

        if (isContainerMissingNetworking(task)) {
          continue;
        }

        TaskHealth taskHealth;
        if (task.getContainers().get(0).getNetworkBindings().size() >= 1) {
          taskHealth =
              inferHealthNetworkBindedContainer(
                  targetHealthCacheClient,
                  task,
                  containerInstance,
                  serviceName,
                  service,
                  taskDefinition);
        } else {
          taskHealth =
              inferHealthNetworkInterfacedContainer(
                  targetHealthCacheClient, task, serviceName, service, taskDefinition);
        }
        log.debug("Task Health contains the following elements: {}", taskHealth);

        if (taskHealth != null) {
          taskHealthList.add(taskHealth);
        }
        log.debug("TaskHealthList contains the following elements: {}", taskHealthList);
      }
    }

    return taskHealthList;
  }

  private TaskHealth inferHealthNetworkInterfacedContainer(
      TargetHealthCacheClient targetHealthCacheClient,
      Task task,
      String serviceName,
      Service loadBalancerService,
      TaskDefinition taskDefinition) {

    if (taskDefinition == null) {
      log.debug("Provided task definition is null.");
      return null;
    }

    List<LoadBalancer> loadBalancers = loadBalancerService.getLoadBalancers();
    log.debug("LoadBalancerService found {} load balancers.", loadBalancers.size());

    TaskHealth overallTaskHealth = null;
    for (LoadBalancer loadBalancer : loadBalancers) {
      if (loadBalancer.getTargetGroupArn() == null) {
        log.debug("LoadBalancer does not contain a target group arn.");
        continue;
      }

      if (!isContainerPortPresent(
          taskDefinition.getContainerDefinitions(), loadBalancer.getContainerPort())) {
        log.debug(
            "Container does not contain a port mapping with load balanced container port: {}.",
            loadBalancer.getContainerPort());
        continue;
      }

      NetworkInterface networkInterface = task.getContainers().get(0).getNetworkInterfaces().get(0);

      overallTaskHealth =
          describeTargetHealth(
              targetHealthCacheClient,
              task,
              loadBalancerService,
              serviceName,
              loadBalancer.getTargetGroupArn(),
              networkInterface.getPrivateIpv4Address(),
              loadBalancer.getContainerPort(),
              overallTaskHealth);
    }
    return overallTaskHealth;
  }

  private void evictStaleData(Task task, Service loadBalancerService) {
    String serviceEvictionKey =
        Keys.getTaskDefinitionKey(accountName, region, loadBalancerService.getServiceName());
    serviceEvictions.add(serviceEvictionKey);
    String taskEvictionKey = Keys.getTaskKey(accountName, region, task.getTaskId());
    taskEvictions.add(taskEvictionKey);

    String taskDefArn = loadBalancerService.getTaskDefinition();
    String taskDefKey = Keys.getTaskDefinitionKey(accountName, region, taskDefArn);
    taskDefEvictions.add(taskDefKey);
  }

  private TaskHealth makeTaskHealth(
      Task task, String serviceName, TargetHealthDescription healthDescription) {
    String targetHealth = STATUS_UNKNOWN;
    if (healthDescription != null) {
      log.debug("Task target health is: {}", healthDescription.getTargetHealth());
      targetHealth =
          healthDescription.getTargetHealth().getState().equals("healthy")
              ? STATUS_UP
              : STATUS_UNKNOWN;
    }
    TaskHealth taskHealth = new TaskHealth();
    taskHealth.setType("loadBalancer");
    taskHealth.setState(targetHealth);
    taskHealth.setServiceName(serviceName);
    taskHealth.setTaskId(task.getTaskId());
    taskHealth.setTaskArn(task.getTaskArn());
    taskHealth.setInstanceId(task.getTaskArn());
    log.debug("Task Health is: {}", taskHealth);
    return taskHealth;
  }

  private TaskHealth inferHealthNetworkBindedContainer(
      TargetHealthCacheClient targetHealthCacheClient,
      Task task,
      ContainerInstance containerInstance,
      String serviceName,
      Service loadBalancerService,
      TaskDefinition taskDefinition) {
    if (taskDefinition == null) {
      log.debug("Provided task definition is null.");
      return null;
    }

    List<LoadBalancer> loadBalancers = loadBalancerService.getLoadBalancers();
    log.debug("LoadBalancerService found {} load balancers.", loadBalancers.size());

    TaskHealth overallTaskHealth = null;
    for (LoadBalancer loadBalancer : loadBalancers) {
      if (loadBalancer.getTargetGroupArn() == null) {
        log.debug("LoadBalancer does not contain a target group arn.");
        continue;
      }

      if (containerInstance == null || containerInstance.getEc2InstanceId() == null) {
        log.debug("Container instance is missing or does not contain a ec2 instance id.");
        continue;
      }

      Optional<Integer> hostPort =
          getHostPort(task.getContainers(), loadBalancer.getContainerPort());
      if (!hostPort.isPresent()) {
        log.debug(
            "Container does not contain a port mapping with load balanced container port: {}.",
            loadBalancer.getContainerPort());
        continue;
      }

      overallTaskHealth =
          describeTargetHealth(
              targetHealthCacheClient,
              task,
              loadBalancerService,
              serviceName,
              loadBalancer.getTargetGroupArn(),
              containerInstance.getEc2InstanceId(),
              hostPort.get(),
              overallTaskHealth);
    }

    return overallTaskHealth;
  }

  private TargetHealthDescription findHealthDescription(
      List<TargetHealthDescription> targetHealths, String targetId, Integer targetPort) {

    return targetHealths.stream()
        .filter(
            h ->
                h.getTarget().getId().equals(targetId)
                    && h.getTarget().getPort().equals(targetPort))
        .findFirst()
        .orElse(null);
  }

  private TaskHealth describeTargetHealth(
      TargetHealthCacheClient targetHealthCacheClient,
      Task task,
      Service loadBalancerService,
      String serviceName,
      String targetGroupArn,
      String targetId,
      Integer targetPort,
      TaskHealth overallTaskHealth) {

    String targetHealthKey = Keys.getTargetHealthKey(accountName, region, targetGroupArn);
    EcsTargetHealth targetHealth = targetHealthCacheClient.get(targetHealthKey);

    if (targetHealth == null) {
      log.debug("Cached EcsTargetHealth is empty for targetGroup {}", targetGroupArn);
      evictStaleData(task, loadBalancerService);
      return makeTaskHealth(task, serviceName, null);
    }
    TargetHealthDescription targetHealthDescription =
        findHealthDescription(targetHealth.getTargetHealthDescriptions(), targetId, targetPort);

    if (targetHealthDescription == null) {
      log.debug(
          "TargetHealthDescription is empty on targetGroup '{}' for {}:{}",
          targetGroupArn,
          targetId,
          targetPort);
      evictStaleData(task, loadBalancerService);
      return makeTaskHealth(task, serviceName, null);
    }

    log.debug("Retrieved health of targetId {} for targetGroup {}", targetId, targetGroupArn);

    TaskHealth taskHealth = makeTaskHealth(task, serviceName, targetHealthDescription);
    if ((overallTaskHealth == null) || (taskHealth.getState().equals(STATUS_UNKNOWN))) {
      return taskHealth;
    }

    return overallTaskHealth;
  }

  private Optional<Integer> getHostPort(List<Container> containers, Integer hostPort) {
    if (containers != null && !containers.isEmpty()) {
      for (Container container : containers) {
        for (NetworkBinding networkBinding : container.getNetworkBindings()) {
          if (networkBinding.getContainerPort().intValue() == hostPort.intValue()) {
            log.debug("Load balanced hostPort: {} found for container.", hostPort);
            return Optional.of(networkBinding.getHostPort());
          }
        }
      }
    }

    return Optional.empty();
  }

  private boolean isContainerPortPresent(
      List<ContainerDefinition> containerDefinitions, Integer containerPort) {
    for (ContainerDefinition containerDefinition : containerDefinitions) {
      for (PortMapping portMapping : containerDefinition.getPortMappings()) {
        if (portMapping.getContainerPort().intValue() == containerPort.intValue()) {
          log.debug("Load balanced containerPort: {} found for container.", containerPort);
          return true;
        }
      }
    }

    return false;
  }

  private boolean isContainerMissingNetworking(Task task) {
    if (task.getContainers().isEmpty()) {
      return true;
    }

    return isTaskMissingNetworkBindings(task) && isTaskMissingNetworkInterfaces(task);
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
  protected Map<String, Collection<CacheData>> generateFreshData(
      Collection<TaskHealth> taskHealthList) {
    Collection<CacheData> dataPoints = new LinkedList<>();

    for (TaskHealth taskHealth : taskHealthList) {
      Map<String, Object> attributes = convertTaskHealthToAttributes(taskHealth);

      String key = Keys.getTaskHealthKey(accountName, region, taskHealth.getTaskId());
      dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));
    }

    log.info("Caching {} task health checks in {}", dataPoints.size(), getAgentType());
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(HEALTH.toString(), dataPoints);

    return dataMap;
  }

  @Override
  protected Map<String, Collection<String>> addExtraEvictions(
      Map<String, Collection<String>> evictions) {
    if (!taskEvictions.isEmpty()) {
      if (evictions.containsKey(TASKS.toString())) {
        evictions.get(TASKS.toString()).addAll(taskEvictions);
      } else {
        evictions.put(TASKS.toString(), taskEvictions);
      }
    }
    if (!serviceEvictions.isEmpty()) {
      if (evictions.containsKey(SERVICES.toString())) {
        evictions.get(SERVICES.toString()).addAll(serviceEvictions);
      } else {
        evictions.put(SERVICES.toString(), serviceEvictions);
      }
    }
    if (!taskDefEvictions.isEmpty()) {
      if (evictions.containsKey(TASK_DEFINITIONS.toString())) {
        evictions.get(TASK_DEFINITIONS.toString()).addAll(taskDefEvictions);
      } else {
        evictions.put(TASK_DEFINITIONS.toString(), taskDefEvictions);
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
