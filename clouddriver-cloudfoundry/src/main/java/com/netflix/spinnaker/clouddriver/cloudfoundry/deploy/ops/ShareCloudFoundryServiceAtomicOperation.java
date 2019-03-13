/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceInstanceResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.ShareCloudFoundryServiceDescription;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@RequiredArgsConstructor
public class ShareCloudFoundryServiceAtomicOperation implements AtomicOperation<ServiceInstanceResponse> {
  private static final String PHASE = "SHARE_SERVICE";
  private final ShareCloudFoundryServiceDescription description;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public ServiceInstanceResponse operate(List priorOutputs) {
    Task task = getTask();

    String serviceInstanceName = description.getServiceInstanceName();
    String region = description.getRegion();
    Set<String> shareToRegions = description.getShareToRegions();
    task.updateStatus(PHASE, "Sharing service instance '" + serviceInstanceName +
      "' from '" + region + "' into " +
      String.join(", ", shareToRegions.stream().map(s -> "'" + s + "'").collect(toSet())));

    ServiceInstanceResponse results = description.getClient().getServiceInstances().shareServiceInstance(region, serviceInstanceName, shareToRegions);

    task.updateStatus(PHASE, "Finished sharing service instance '" + serviceInstanceName + "'");

    return results;
  }
}
