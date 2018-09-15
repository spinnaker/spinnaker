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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.UpsertCloudFoundryLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class UpsertCloudFoundryLoadBalancerAtomicOperation implements AtomicOperation<CloudFoundryLoadBalancer> {
  private static final String PHASE = "UPSERT_LOAD_BALANCER";
  private final UpsertCloudFoundryLoadBalancerDescription description;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public CloudFoundryLoadBalancer operate(List priorOutputs) {
    getTask().updateStatus(PHASE, "Creating load balancer in '" + description.getRegion() + "'");

    CloudFoundryClient client = description.getClient();
    CloudFoundryLoadBalancer loadBalancer = client.getRoutes().createRoute(new RouteId(description.getHost(), description.getPath(), description.getPort(), description.getDomain().getId()),
      description.getSpace().getId());

    if (loadBalancer != null) {
      getTask().updateStatus(PHASE, "Done creating load balancer");
    } else {
      getTask().updateStatus(PHASE, "Load balancer already exists in another organization and space");
      getTask().fail();
    }

    return loadBalancer;
  }
}
