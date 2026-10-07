/*
 * Copyright 2021 Expedia, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteCloudFormationDescription;
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
import org.springframework.util.StringUtils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.DeleteStackRequest;

@Slf4j
public class DeleteCloudFormationAtomicOperation implements AtomicOperation<Map> {

  private static final String BASE_PHASE = "DELETE_CLOUDFORMATION_STACK";

  @Autowired AmazonClientProvider amazonClientProvider;

  @Autowired
  @Qualifier("amazonObjectMapper")
  private ObjectMapper objectMapper;

  private DeleteCloudFormationDescription description;

  public DeleteCloudFormationAtomicOperation(
      DeleteCloudFormationDescription deleteCloudFormationDescription) {
    this.description = deleteCloudFormationDescription;
  }

  @Override
  public Map operate(List priorOutputs) {
    Task task = TaskRepository.threadLocalTask.get();
    CloudFormationClient cloudFormationClient =
        amazonClientProvider.getAmazonCloudFormationV2(
            description.getCredentials(), description.getRegion());

    DeleteStackRequest.Builder requestBuilder =
        DeleteStackRequest.builder().stackName(description.getStackName());

    if (StringUtils.hasText(description.getRoleARN())) {
      requestBuilder.roleARN(description.getRoleARN());
    }

    try {
      task.updateStatus(BASE_PHASE, "Deleting CloudFormation Stack");
      cloudFormationClient.deleteStack(requestBuilder.build());
    } catch (AwsServiceException e) {
      log.error("Error deleting stack {}", description.getStackName(), e);
      throw e;
    }
    return Collections.emptyMap();
  }
}
