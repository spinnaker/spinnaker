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

package com.netflix.spinnaker.clouddriver.ecs.services;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ContainerInstanceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsInstanceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskHealthCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.ContainerInstance;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Task;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.TaskHealth;
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ContainerInformationService {

  private final ECSCredentialsConfig ecsCredentialsConfig;
  private final TaskCacheClient taskCacheClient;
  private final ServiceCacheClient serviceCacheClient;
  private final TaskHealthCacheClient taskHealthCacheClient;
  private final EcsInstanceCacheClient ecsInstanceCacheClient;
  private final ContainerInstanceCacheClient containerInstanceCacheClient;

  @Autowired
  public ContainerInformationService(ECSCredentialsConfig ecsCredentialsConfig,
                                     TaskCacheClient taskCacheClient,
                                     ServiceCacheClient serviceCacheClient,
                                     TaskHealthCacheClient taskHealthCacheClient,
                                     EcsInstanceCacheClient ecsInstanceCacheClient,
                                     ContainerInstanceCacheClient containerInstanceCacheClient) {
    this.ecsCredentialsConfig = ecsCredentialsConfig;
    this.taskCacheClient = taskCacheClient;
    this.serviceCacheClient = serviceCacheClient;
    this.taskHealthCacheClient = taskHealthCacheClient;
    this.ecsInstanceCacheClient = ecsInstanceCacheClient;
    this.containerInstanceCacheClient = containerInstanceCacheClient;
  }

  public List<Map<String, Object>> getHealthStatus(String taskId, String serviceName, String accountName, String region) {
    String serviceCacheKey = Keys.getServiceKey(accountName, region, serviceName);
    Service service = serviceCacheClient.get(serviceCacheKey);

    String healthKey = Keys.getTaskHealthKey(accountName, region, taskId);
    TaskHealth taskHealth = taskHealthCacheClient.get(healthKey);

    if (service == null || taskHealth == null) {
      List<Map<String, Object>> healthMetrics = new ArrayList<>();

      Map<String, Object> loadBalancerHealth = new HashMap<>();
      loadBalancerHealth.put("instanceId", taskId);
      loadBalancerHealth.put("state", "Unknown");
      loadBalancerHealth.put("type", "loadBalancer");

      healthMetrics.add(loadBalancerHealth);
      return healthMetrics;
    }

    List<LoadBalancer> loadBalancers = service.getLoadBalancers();
    //There should only be 1 based on AWS documentation.
    if (loadBalancers.size() == 1) {

      List<Map<String, Object>> healthMetrics = new ArrayList<>();
      Map<String, Object> loadBalancerHealth = new HashMap<>();
      loadBalancerHealth.put("instanceId", taskId);
      loadBalancerHealth.put("state", taskHealth.getState());
      loadBalancerHealth.put("type", taskHealth.getType());

      healthMetrics.add(loadBalancerHealth);
      return healthMetrics;
    } else if (loadBalancers.size() >= 2) {
      throw new IllegalArgumentException("Cannot have more than 1 load balancer while checking ECS health.");
    }
    return null;

  }

  public String getClusterArn(String accountName, String region, String taskId) {
    String key = Keys.getTaskKey(accountName, region, taskId);
    Task task = taskCacheClient.get(key);
    if (task != null) {
      return task.getClusterArn();
    }
    return null;
  }

  public String getClusterName(String serviceName, String accountName, String region) {
    String serviceCachekey = Keys.getServiceKey(accountName, region, serviceName);
    Service service = serviceCacheClient.get(serviceCachekey);
    if (service != null) {
      return service.getClusterName();
    }
    return null;
  }

  public String getTaskPrivateAddress(String accountName, String region, Task task) {
    if (task.getContainers().size() > 1) {
      throw new IllegalArgumentException("Multiple containers for a task is not supported.");
    }

    int hostPort;
    try {
      hostPort = task.getContainers().get(0).getNetworkBindings().get(0).getHostPort();
    } catch (Exception e) {
      hostPort = -1;
    }

    if (hostPort < 0 || hostPort > 65535) {
      return null;
    }

    Instance instance = getEc2Instance(accountName, region, task);
    if(instance == null){
      return null;
    }

    String hostPrivateIpAddress = instance.getPrivateIpAddress();
    return String.format("%s:%s", hostPrivateIpAddress, hostPort);
  }

  public Instance getEc2Instance(String ecsAccount, String region, Task task){
    String containerInstanceCacheKey = Keys.getContainerInstanceKey(ecsAccount, region, task.getContainerInstanceArn());
    ContainerInstance containerInstance = containerInstanceCacheClient.get(containerInstanceCacheKey);
    if (containerInstance == null) {
      return null;
    }

    Set<Instance> instances = ecsInstanceCacheClient.find(containerInstance.getEc2InstanceId(), getAwsAccountName(ecsAccount), region);
    if (instances.size() > 1) {
      throw new IllegalArgumentException("There cannot be more than 1 EC2 container instance for a given region and instance ID.");
    } else if (instances.size() == 0) {
      return null;
    }

    return instances.iterator().next();
  }

  private String getAwsAccountName(String ecsAccountName) {
    for (ECSCredentialsConfig.Account ecsAccount : ecsCredentialsConfig.getAccounts()) {
      if (ecsAccount.getName().equals(ecsAccountName)) {
        return ecsAccount.getAwsAccount();
      }
    }
    return null;
  }
}
