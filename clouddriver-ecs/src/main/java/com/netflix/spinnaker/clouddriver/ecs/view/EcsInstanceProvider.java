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

package com.netflix.spinnaker.clouddriver.ecs.view;

import com.amazonaws.services.ecs.model.NetworkInterface;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ContainerInstanceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Task;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsTask;
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EcsInstanceProvider implements InstanceProvider<EcsTask, String> {

  private final TaskCacheClient taskCacheClient;
  private final ContainerInstanceCacheClient containerInstanceCacheClient;
  private ContainerInformationService containerInformationService;

  @Autowired
  public EcsInstanceProvider(
      ContainerInformationService containerInformationService,
      TaskCacheClient taskCacheClient,
      ContainerInstanceCacheClient containerInstanceCacheClient) {
    this.containerInformationService = containerInformationService;
    this.taskCacheClient = taskCacheClient;
    this.containerInstanceCacheClient = containerInstanceCacheClient;
  }

  @Override
  public String getCloudProvider() {
    return EcsCloudProvider.ID;
  }

  @Override
  public EcsTask getInstance(String account, String region, String id) {
    if (!isValidId(id, region)) return null;

    EcsTask ecsInstance = null;

    String key = Keys.getTaskKey(account, region, id);
    Task task = taskCacheClient.get(key);
    if (task == null) {
      return null;
    }

    String serviceName = StringUtils.substringAfter(task.getGroup(), "service:");
    Long launchTime = task.getStartedAt();

    List<Map<String, Object>> healthStatus =
        containerInformationService.getHealthStatus(id, serviceName, account, region);
    String address = containerInformationService.getTaskPrivateAddress(account, region, task);
    String zone = containerInformationService.getTaskZone(account, region, task);

    NetworkInterface networkInterface =
        task.getContainers() != null
                && !task.getContainers().isEmpty()
                && !task.getContainers().get(0).getNetworkInterfaces().isEmpty()
            ? task.getContainers().get(0).getNetworkInterfaces().get(0)
            : null;

    Service service = containerInformationService.getService(serviceName, account, region);
    boolean hasHealthCheck =
        containerInformationService.taskHasHealthCheck(service, account, region);

    ecsInstance =
        new EcsTask(
            id,
            launchTime,
            task.getLastStatus(),
            task.getDesiredStatus(),
            task.getHealthStatus(),
            zone,
            healthStatus,
            address,
            networkInterface,
            hasHealthCheck);

    return ecsInstance;
  }

  @Override
  public String getConsoleOutput(String account, String region, String id) {
    return null;
  }

  private boolean isValidId(String id, String region) {
    String oldTaskIdRegex = "[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}";
    String newTaskIdRegex = "[\\da-f]{32}";
    String clusterNameRegex = "[a-zA-Z0-9\\-_]{1,255}";
    String oldTaskIdOnly = String.format("^%s$", oldTaskIdRegex);
    String newTaskIdOnly = String.format("^%s$", newTaskIdRegex);
    // arn:aws:ecs:region:account-id:task/task-id
    String oldTaskArn = String.format("arn:aws:ecs:%s:\\d*:task/%s", region, oldTaskIdRegex);
    // arn:aws:ecs:region:account-id:task/cluster-name/task-id
    String newTaskArn =
        String.format("arn:aws:ecs:%s:\\d*:task/%s/%s", region, clusterNameRegex, newTaskIdRegex);
    return id.matches(oldTaskIdOnly)
        || id.matches(newTaskIdOnly)
        || id.matches(oldTaskArn)
        || id.matches(newTaskArn);
  }
}
