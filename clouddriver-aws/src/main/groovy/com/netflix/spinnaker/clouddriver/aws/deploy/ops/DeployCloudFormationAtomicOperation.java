/*
 * Copyright (c) 2019 Schibsted Media Group.
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

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeployCloudFormationDescription;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class DeployCloudFormationAtomicOperation implements AtomicOperation<Map> {

  private static final String BASE_PHASE = "DEPLOY_CLOUDFORMATION_STACK";


  @Autowired
  AmazonClientProvider amazonClientProvider;

  @Autowired
  @Qualifier("amazonObjectMapper")
  private ObjectMapper objectMapper;

  private DeployCloudFormationDescription description;

  public DeployCloudFormationAtomicOperation(DeployCloudFormationDescription deployCloudFormationDescription) {
    this.description = deployCloudFormationDescription;
  }

  @Override
  public Map operate(List priorOutputs) {
    Task task = TaskRepository.threadLocalTask.get();
    task.updateStatus(BASE_PHASE, "Configuring CloudFormation Stack");
    AmazonCloudFormation amazonCloudFormation = amazonClientProvider.getAmazonCloudFormation(
      description.getCredentials(), description.getRegion());
    String template;
    try {
      template = objectMapper.writeValueAsString(description.getTemplateBody());
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Could not serialize CloudFormation Stack template body", e);
    }
    List<Parameter> parameters = description.getParameters().entrySet().stream()
      .map(entry -> new Parameter()
        .withParameterKey(entry.getKey())
        .withParameterValue(entry.getValue()))
      .collect(Collectors.toList());
    try {
      String stackId = createStack(amazonCloudFormation, template, parameters, description.getCapabilities());
      return Collections.singletonMap("stackId", stackId);
    } catch (AlreadyExistsException e) {
      String stackId = updateStack(amazonCloudFormation, template, parameters, description.getCapabilities());
      return Collections.singletonMap("stackId", stackId);
    }
  }

  private String createStack(AmazonCloudFormation amazonCloudFormation, String template, List<Parameter> parameters, List<String> capabilities) {
    Task task = TaskRepository.threadLocalTask.get();
    task.updateStatus(BASE_PHASE, "Preparing CloudFormation Stack");
    CreateStackRequest createStackRequest = new CreateStackRequest()
      .withStackName(description.getStackName())
      .withParameters(parameters)
      .withTemplateBody(template)
      .withCapabilities(capabilities);
    task.updateStatus(BASE_PHASE, "Uploading CloudFormation Stack");
    CreateStackResult createStackResult = amazonCloudFormation.createStack(createStackRequest);
    return createStackResult.getStackId();
  }

  private String updateStack(AmazonCloudFormation amazonCloudFormation, String template, List<Parameter> parameters, List<String> capabilities) {
    Task task = TaskRepository.threadLocalTask.get();
    task.updateStatus(BASE_PHASE, "CloudFormation Stack exists. Updating it");
    UpdateStackRequest updateStackRequest = new UpdateStackRequest()
      .withStackName(description.getStackName())
      .withParameters(parameters)
      .withTemplateBody(template)
      .withCapabilities(capabilities);
    task.updateStatus(BASE_PHASE, "Uploading CloudFormation Stack");
    try {
      UpdateStackResult updateStackResult = amazonCloudFormation.updateStack(updateStackRequest);
      return updateStackResult.getStackId();
    } catch (AmazonCloudFormationException e) {
      // No changes on the stack, ignore failure
      return amazonCloudFormation
        .describeStacks(new DescribeStacksRequest().withStackName(description.getStackName()))
        .getStacks()
        .stream()
        .findFirst()
        .orElseThrow(() ->
          new IllegalArgumentException("No CloudFormation Stack found with stack name " + description.getStackName()))
        .getStackId();
    }

  }

}
