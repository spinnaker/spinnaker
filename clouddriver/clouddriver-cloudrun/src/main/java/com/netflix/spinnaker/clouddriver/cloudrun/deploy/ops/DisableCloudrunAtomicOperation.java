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

import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.EnableDisableCloudrunDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunServerGroup;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.view.CloudrunClusterProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEvent;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class DisableCloudrunAtomicOperation extends CloudrunAtomicOperation<Void> {

  private static final String BASE_PHASE = "DISABLE_SERVER_GROUP";

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public EnableDisableCloudrunDescription getDescription() {
    return description;
  }

  private final EnableDisableCloudrunDescription description;

  @Autowired CloudrunClusterProvider cloudrunClusterProvider;

  public DisableCloudrunAtomicOperation(EnableDisableCloudrunDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List<Void> priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing disabling of server group " + description.getServerGroupName());
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
    }
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Successfully disabled the server group : " + description.getServerGroupName());
    return null;
  }

  @Override
  public Collection<OperationEvent> getEvents() {
    return super.getEvents();
  }
}
