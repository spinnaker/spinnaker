/*
 * Copyright 2019 Adevinta.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.ExecuteChangeSetRequest;
import com.amazonaws.services.cloudformation.model.ExecuteChangeSetResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ExecuteCloudFormationChangeSetDescription;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

@Slf4j
public class ExecuteCloudFormationChangeSetAtomicOperation implements AtomicOperation<Map> {

  private static final String BASE_PHASE = "EXECUTE_CLOUDFORMATION_CHANGESET";

  @Autowired AmazonClientProvider amazonClientProvider;

  @Autowired
  @Qualifier("amazonObjectMapper")
  private ObjectMapper objectMapper;

  private ExecuteCloudFormationChangeSetDescription description;

  public ExecuteCloudFormationChangeSetAtomicOperation(
      ExecuteCloudFormationChangeSetDescription executeCloudFormationChangeSetDescription) {
    this.description = executeCloudFormationChangeSetDescription;
  }

  @Override
  public Map operate(List priorOutputs) {
    Task task = TaskRepository.threadLocalTask.get();
    task.updateStatus(BASE_PHASE, "Configuring CloudFormation Stack");
    AmazonCloudFormation amazonCloudFormation =
        amazonClientProvider.getAmazonCloudFormation(
            description.getCredentials(), description.getRegion());

    String stackName = description.getStackName();
    String changeSetName = description.getChangeSetName();

    ExecuteChangeSetRequest executeChangeSetRequest =
        new ExecuteChangeSetRequest()
            .withStackName(description.getStackName())
            .withChangeSetName(description.getChangeSetName());
    task.updateStatus(BASE_PHASE, "Executing CloudFormation ChangeSet");
    try {
      ExecuteChangeSetResult executeChangeSetResult =
          amazonCloudFormation.executeChangeSet(executeChangeSetRequest);
      return Collections.singletonMap("stackId", getStackId(amazonCloudFormation));
    } catch (AmazonCloudFormationException e) {
      log.error("Error executing change set", e);
      throw e;
    }
  }

  private String getStackId(AmazonCloudFormation amazonCloudFormation) {
    return amazonCloudFormation
        .describeStacks(new DescribeStacksRequest().withStackName(description.getStackName()))
        .getStacks().stream()
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No CloudFormation Stack found with stack name " + description.getStackName()))
        .getStackId();
  }
}
