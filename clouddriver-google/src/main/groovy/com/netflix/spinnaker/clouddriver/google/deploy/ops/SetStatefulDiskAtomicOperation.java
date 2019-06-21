/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops;

import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.StatefulPolicy;
import com.google.api.services.compute.model.StatefulPolicyPreservedState;
import com.google.api.services.compute.model.StatefulPolicyPreservedStateDiskDevice;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeApiFactory;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleServerGroupManagers;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.deploy.description.SetStatefulDiskDescription;
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;

public class SetStatefulDiskAtomicOperation extends GoogleAtomicOperation<Void> {

  private static final String BASE_PHASE = "SET_STATEFUL_DISK";

  private final GoogleClusterProvider clusterProvider;
  private final GoogleComputeApiFactory computeApiFactory;
  private final SetStatefulDiskDescription description;

  public SetStatefulDiskAtomicOperation(
      GoogleClusterProvider clusterProvider,
      GoogleComputeApiFactory computeApiFactory,
      SetStatefulDiskDescription description) {
    this.clusterProvider = clusterProvider;
    this.computeApiFactory = computeApiFactory;
    this.description = description;
  }

  /*
      curl -X POST -H "Content-Type: application/json" -d '
        [ { "setStatefulDisk": {
              "serverGroupName": "myapp-dev-v000",
              "region": "us-east1",
              "device-name": "myapp-dev-v000-1",
              "credentials": "my-account-name"
        } } ]' localhost:7002/gce/ops
  */
  @Override
  public Void operate(List priorOutputs) {

    Task task = TaskRepository.threadLocalTask.get();

    task.updateStatus(
        BASE_PHASE,
        String.format(
            "Initializing set stateful disk of instance group %s in region %s",
            description.getServerGroupName(), description.getRegion()));

    GoogleServerGroup.View serverGroup =
        GCEUtil.queryServerGroup(
            clusterProvider,
            description.getAccount(),
            description.getRegion(),
            description.getServerGroupName());

    try {
      GoogleServerGroupManagers managers =
          computeApiFactory.createServerGroupManagers(description.getCredentials(), serverGroup);

      task.updateStatus(BASE_PHASE, "Retrieving current instance group definition");

      InstanceGroupManager instanceGroupManager = managers.get().execute();

      setStatefulPolicy(instanceGroupManager);

      task.updateStatus(BASE_PHASE, "Storing updated instance group definition");

      managers
          .update(instanceGroupManager)
          .executeAndWait(TaskRepository.threadLocalTask.get(), BASE_PHASE);

      return null;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void setStatefulPolicy(InstanceGroupManager instanceGroupManager) {

    if (instanceGroupManager.getStatefulPolicy() == null) {
      instanceGroupManager.setStatefulPolicy(new StatefulPolicy());
    }
    StatefulPolicy statefulPolicy = instanceGroupManager.getStatefulPolicy();
    if (statefulPolicy.getPreservedState() == null) {
      statefulPolicy.setPreservedState(new StatefulPolicyPreservedState());
    }
    StatefulPolicyPreservedState preservedState = statefulPolicy.getPreservedState();
    if (preservedState.getDisks() == null) {
      preservedState.setDisks(new HashMap<>());
    }
    preservedState
        .getDisks()
        .put(description.getDeviceName(), new StatefulPolicyPreservedStateDiskDevice());
  }

  @VisibleForTesting
  public SetStatefulDiskDescription getDescription() {
    return description;
  }
}
