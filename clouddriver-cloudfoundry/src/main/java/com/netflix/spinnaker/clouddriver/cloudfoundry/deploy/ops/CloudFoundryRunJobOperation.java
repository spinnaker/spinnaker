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
 *
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Task;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.CloudFoundryRunJobOperationDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
public class CloudFoundryRunJobOperation implements AtomicOperation<DeploymentResult> {
  private static final String PHASE = "RUN_CLOUDFOUNDRY_JOB";
  private final CloudFoundryRunJobOperationDescription description;

  @Override
  public DeploymentResult operate(List priorOutputs) {
    CloudFoundryClient client = description.getClient();
    CloudFoundryServerGroup serverGroup = description.getServerGroup();
    String applicationGuid = serverGroup.getId();
    String applicationName = serverGroup.getName();

    // make the job name unique by appending to it a random string so its logs are filterable
    String originalName = description.getJobName();
    String randomString = Long.toHexString(Double.doubleToLongBits(Math.random()));
    String jobName =
        (StringUtils.isNotEmpty(originalName) ? originalName + "-" : "") + randomString;

    TaskRepository.threadLocalTask
        .get()
        .updateStatus(
            PHASE,
            String.format(
                "Running job '%1$s' as a CloudFoundry task '%1$s' on org/space '%2$s' with application '%3$s'",
                jobName, description.getRegion(), applicationName));

    Task cfTask = client.getTasks().createTask(applicationGuid, description.getCommand(), jobName);

    DeploymentResult deploymentResult = new DeploymentResult();
    deploymentResult
        .getDeployedNamesByLocation()
        .put(description.getRegion(), Collections.singletonList(cfTask.getGuid()));
    return deploymentResult;
  }
}
