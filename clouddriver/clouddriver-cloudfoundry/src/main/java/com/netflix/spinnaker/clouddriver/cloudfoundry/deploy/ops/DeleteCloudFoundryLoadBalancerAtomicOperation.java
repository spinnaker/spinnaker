/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeleteCloudFoundryLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DeleteCloudFoundryLoadBalancerAtomicOperation implements AtomicOperation<Void> {
  private static final String PHASE = "DELETE_LOAD_BALANCER";
  private final DeleteCloudFoundryLoadBalancerDescription description;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List priorOutputs) {
    CloudFoundryClient client = description.getClient();

    if (description.getLoadBalancer() == null) {
      throw new CloudFoundryApiException("Load balancer does not exist");
    } else {
      getTask()
          .updateStatus(PHASE, "Deleting load balancer " + description.getLoadBalancer().getName());
      client.getRoutes().deleteRoute(description.getLoadBalancer().getId());
      getTask()
          .updateStatus(PHASE, "Deleted load balancer " + description.getLoadBalancer().getName());
    }

    return null;
  }
}
