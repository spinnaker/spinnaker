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
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.NetworkBinding;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ContainerInstanceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsInstanceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskDefinitionCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskHealthCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.ContainerInstance;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Task;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.TaskHealth;
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ContainerInformationService {

  private final ECSCredentialsConfig ecsCredentialsConfig;
  private final TaskCacheClient taskCacheClient;
  private final ServiceCacheClient serviceCacheClient;
  private final TaskHealthCacheClient taskHealthCacheClient;
  private final TaskDefinitionCacheClient taskDefinitionCacheClient;
  private final EcsInstanceCacheClient ecsInstanceCacheClient;
  private final ContainerInstanceCacheClient containerInstanceCacheClient;

  @Autowired
  public ContainerInformationService(
      ECSCredentialsConfig ecsCredentialsConfig,
      TaskCacheClient taskCacheClient,
      ServiceCacheClient serviceCacheClient,
      TaskHealthCacheClient taskHealthCacheClient,
      TaskDefinitionCacheClient taskDefinitionCacheClient,
      EcsInstanceCacheClient ecsInstanceCacheClient,
      ContainerInstanceCacheClient containerInstanceCacheClient) {
    this.ecsCredentialsConfig = ecsCredentialsConfig;
    this.taskCacheClient = taskCacheClient;
    this.serviceCacheClient = serviceCacheClient;
    this.taskHealthCacheClient = taskHealthCacheClient;
    this.taskDefinitionCacheClient = taskDefinitionCacheClient;
    this.ecsInstanceCacheClient = ecsInstanceCacheClient;
    this.containerInstanceCacheClient = containerInstanceCacheClient;
  }

  public List<Map<String, Object>> getHealthStatus(
      String taskId, String serviceName, String accountName, String region) {
    Service service = getService(serviceName, accountName, region);

    String healthKey = Keys.getTaskHealthKey(accountName, region, taskId);
    TaskHealth taskHealth = taskHealthCacheClient.get(healthKey);

    String taskKey = Keys.getTaskKey(accountName, region, taskId);
    Task task = taskCacheClient.get(taskKey);

    List<Map<String, Object>> healthMetrics = new ArrayList<>();

    // Load balancer-based health
    if (service == null || taskHealth == null) {
      Map<String, Object> loadBalancerHealth = new HashMap<>();
      loadBalancerHealth.put("instanceId", taskId);
      loadBalancerHealth.put("state", "Unknown");
      loadBalancerHealth.put("type", "loadBalancer");

      healthMetrics.add(loadBalancerHealth);
    } else {
      Map<String, Object> loadBalancerHealth = new HashMap<>();
      loadBalancerHealth.put("instanceId", taskId);
      loadBalancerHealth.put("state", taskHealth.getState());
      loadBalancerHealth.put("type", taskHealth.getType());

      healthMetrics.add(loadBalancerHealth);
    }

    // Task-based health
    if (task != null) {
      boolean hasHealthCheck = false;
      if (service != null) {
        hasHealthCheck = taskHasHealthCheck(service, accountName, region);
      }

      Map<String, Object> taskPlatformHealth = new HashMap<>();
      taskPlatformHealth.put("instanceId", taskId);
      taskPlatformHealth.put("type", "ecs");
      taskPlatformHealth.put("healthClass", "platform");
      taskPlatformHealth.put(
          "state",
          toPlatformHealthState(task.getLastStatus(), task.getHealthStatus(), hasHealthCheck));
      healthMetrics.add(taskPlatformHealth);
    }

    return healthMetrics;
  }

  public boolean taskHasHealthCheck(Service service, String accountName, String region) {
    if (service != null) {
      String taskDefinitionCacheKey =
          Keys.getTaskDefinitionKey(accountName, region, service.getTaskDefinition());
      TaskDefinition taskDefinition = taskDefinitionCacheClient.get(taskDefinitionCacheKey);

      if (taskDefinition != null) {
        for (ContainerDefinition containerDefinition : taskDefinition.getContainerDefinitions()) {
          if (containerDefinition.getHealthCheck() != null
              && containerDefinition.getHealthCheck().getCommand() != null) {
            return true;
          }
        }
      }
    }

    return false;
  }

  private String toPlatformHealthState(
      String ecsTaskStatus, String ecsTaskHealthStatus, boolean hasHealthCheck) {
    if (hasHealthCheck && "UNKNOWN".equals(ecsTaskHealthStatus)) {
      return "Starting";
    } else if ("UNHEALTHY".equals(ecsTaskHealthStatus)) {
      return "Down";
    }

    switch (ecsTaskStatus) {
      case "PROVISIONING":
      case "PENDING":
      case "ACTIVATING":
        return "Starting";
      case "RUNNING":
        return "Up";
      default:
        return "Down";
    }
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
    Service service = getService(serviceName, accountName, region);
    if (service != null) {
      return service.getClusterName();
    }
    return null;
  }

  public Service getService(String serviceName, String accountName, String region) {
    String serviceCacheKey = Keys.getServiceKey(accountName, region, serviceName);
    return serviceCacheClient.get(serviceCacheKey);
  }

  public String getTaskPrivateAddress(String accountName, String region, Task task) { //
    int hostPort;

    if (task.getContainers().size() > 1) {
      hostPort = getAddressHostPortForMultipleContainers(task);
    } else {
      try {
        hostPort = task.getContainers().get(0).getNetworkBindings().get(0).getHostPort();
      } catch (Exception e) {
        hostPort = -1;
      }
    }

    if (hostPort < 0 || hostPort > 65535) {
      return null;
    }

    Instance instance = getEc2Instance(accountName, region, task);
    if (instance == null) {
      return null;
    }

    String hostPrivateIpAddress = instance.getPrivateIpAddress();
    if (hostPrivateIpAddress == null || hostPrivateIpAddress.isEmpty()) {
      return null;
    }

    return String.format("%s:%s", hostPrivateIpAddress, hostPort);
  }

  public String getTaskZone(String accountName, String region, Task task) {
    Instance ec2Instance = getEc2Instance(accountName, region, task);
    if (ec2Instance != null && ec2Instance.getPlacement() != null) {
      return ec2Instance.getPlacement().getAvailabilityZone();
    }

    // TODO for tasks not placed on an instance (e.g. Fargate), determine the zone from the network
    // interface attachment
    return null;
  }

  public Instance getEc2Instance(String ecsAccount, String region, Task task) {
    String containerInstanceCacheKey =
        Keys.getContainerInstanceKey(ecsAccount, region, task.getContainerInstanceArn());
    ContainerInstance containerInstance =
        containerInstanceCacheClient.get(containerInstanceCacheKey);
    if (containerInstance == null) {
      return null;
    }

    Set<Instance> instances =
        ecsInstanceCacheClient.find(
            containerInstance.getEc2InstanceId(), getAwsAccountName(ecsAccount), region);
    if (instances.size() > 1) {
      throw new IllegalArgumentException(
          "There cannot be more than 1 EC2 container instance for a given region and instance ID.");
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

  private int getAddressHostPortForMultipleContainers(Task task) {
    List<Integer> hostPorts = new ArrayList<Integer>() {};

    task.getContainers()
        .forEach(
            (c) -> {
              List<NetworkBinding> networkBindings = c.getNetworkBindings();
              networkBindings.forEach(
                  (b) -> {
                    if (b.getHostPort() != null) {
                      hostPorts.add(b.getHostPort());
                    }
                  });
            });

    if (hostPorts.size() == 1) {
      return hostPorts.get(0);
    }

    return -1;
  }
}
