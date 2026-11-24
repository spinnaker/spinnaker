/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.deploy.ops;

import com.netflix.spinnaker.clouddriver.cloudrun.config.CloudrunConfigurationProperties;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.CloudrunServiceClient;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.DestroyCloudrunDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunServerGroup;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.view.CloudrunClusterProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import groovy.util.logging.Slf4j;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class DestroyCloudrunAtomicOperation extends CloudrunAtomicOperation<Void> {

  private static final String BASE_PHASE = "DESTROY_SERVER_GROUP";

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  private final DestroyCloudrunDescription description;

  @Autowired CloudrunClusterProvider cloudrunClusterProvider;

  CloudrunServiceClient serviceClient;

  @Autowired(required = false)
  CloudrunConfigurationProperties configurationProperties;

  public DestroyCloudrunAtomicOperation(DestroyCloudrunDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing destruction of server group " + description.getServerGroupName());
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Looking up server group with serverGroupName " + description.getServerGroupName());
    CloudrunServerGroup serverGroup =
        cloudrunClusterProvider.getServerGroup(
            description.getAccountName(),
            description.getRegion(),
            description.getServerGroupName());
    if (serverGroup == null) {
      throw new CloudrunOperationException(
          "Failed to get server group by account "
              + description.getAccountName()
              + " , region "
              + description.getRegion()
              + " and server group name : "
              + description.getServerGroupName());
    } else {
      String region = description.getRegion();
      try {
        if (serviceClient == null) {
          serviceClient =
              CloudrunServiceClient.forAccount(
                  description.getCredentials(), configurationProperties);
        }
        serviceClient.deleteRevision(region, description.getServerGroupName());
      } catch (Exception e) {
        throw new CloudrunOperationException(
            "Failed to delete the server group "
                + description.getServerGroupName()
                + ": "
                + e.getMessage(),
            e);
      }
    }
    getTask()
        .updateStatus(
            BASE_PHASE, "Successfully destroyed server group " + description.getServerGroupName());
    return null;
  }
}
