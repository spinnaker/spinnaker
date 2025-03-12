/*
 * Copyright (c) 2019 Adevinta
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
package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.*;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteCloudFormationChangeSetDescription;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class DeleteCloudFormationChangeSetAtomicOperation implements AtomicOperation<Map> {

  private static final String BASE_PHASE = "DELETE_CLOUDFORMATION_CHANGESET";

  @Autowired AmazonClientProvider amazonClientProvider;

  private DeleteCloudFormationChangeSetDescription description;

  public DeleteCloudFormationChangeSetAtomicOperation(
      DeleteCloudFormationChangeSetDescription deployCloudFormationDescription) {
    this.description = deployCloudFormationDescription;
  }

  @Override
  public Map operate(List priorOutputs) {
    Task task = TaskRepository.threadLocalTask.get();
    AmazonCloudFormation amazonCloudFormation =
        amazonClientProvider.getAmazonCloudFormation(
            description.getCredentials(), description.getRegion());

    DeleteChangeSetRequest deleteChangeSetRequest =
        new DeleteChangeSetRequest()
            .withStackName(description.getStackName())
            .withChangeSetName(description.getChangeSetName());
    try {
      task.updateStatus(BASE_PHASE, "Deleting CloudFormation ChangeSet");
      amazonCloudFormation.deleteChangeSet(deleteChangeSetRequest);
    } catch (AmazonServiceException e) {
      log.error(
          "Error removing change set "
              + description.getChangeSetName()
              + " on stack "
              + description.getStackName(),
          e);
      throw e;
    }
    return Collections.emptyMap();
  }
}
