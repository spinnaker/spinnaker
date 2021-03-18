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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.AwsConfigurationProperties;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeployCloudFormationDescription;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.StringUtils;

@Slf4j
public class DeployCloudFormationAtomicOperation implements AtomicOperation<Map> {

  private static final String BASE_PHASE = "DEPLOY_CLOUDFORMATION_STACK";
  private static final String NO_CHANGE_STACK_ERROR_MESSAGE = "No updates";

  @Autowired AmazonClientProvider amazonClientProvider;
  @Autowired AwsConfigurationProperties awsConfigurationProperties;

  @Autowired
  @Qualifier("amazonObjectMapper")
  private ObjectMapper objectMapper;

  private DeployCloudFormationDescription description;

  public DeployCloudFormationAtomicOperation(
      DeployCloudFormationDescription deployCloudFormationDescription) {
    this.description = deployCloudFormationDescription;
  }

  @Override
  public Map operate(List priorOutputs) {
    Task task = TaskRepository.threadLocalTask.get();
    task.updateStatus(BASE_PHASE, "Configuring CloudFormation Stack");
    AmazonCloudFormation amazonCloudFormation =
        amazonClientProvider.getAmazonCloudFormation(
            description.getCredentials(), description.getRegion());
    String templateURL = description.getTemplateURL();
    String templateBody = description.getTemplateBody();
    validateTemplate(amazonCloudFormation, templateURL, templateBody);
    String roleARN = description.getRoleARN();
    List<Parameter> parameters =
        description.getParameters().entrySet().stream()
            .map(
                entry ->
                    new Parameter()
                        .withParameterKey(entry.getKey())
                        .withParameterValue(entry.getValue()))
            .collect(Collectors.toList());
    List<Tag> tags =
        description.getTags().entrySet().stream()
            .map(entry -> new Tag().withKey(entry.getKey()).withValue(entry.getValue()))
            .collect(Collectors.toList());

    boolean stackExists = stackExists(amazonCloudFormation);

    String stackId;
    if (description.isChangeSet()) {
      ChangeSetType changeSetType = stackExists ? ChangeSetType.UPDATE : ChangeSetType.CREATE;
      log.info("{} change set for stack: {}", changeSetType, description);
      stackId =
          createChangeSet(
              amazonCloudFormation,
              templateURL,
              templateBody,
              roleARN,
              parameters,
              tags,
              description.getCapabilities(),
              changeSetType);
    } else {
      if (stackExists) {
        log.info("Updating existing stack {}", description);
        stackId =
            updateStack(
                amazonCloudFormation,
                templateURL,
                templateBody,
                roleARN,
                parameters,
                tags,
                description.getCapabilities());
      } else {
        log.info("Creating new stack: {}", description);
        stackId =
            createStack(
                amazonCloudFormation,
                templateURL,
                templateBody,
                roleARN,
                parameters,
                tags,
                description.getCapabilities());
      }
    }
    return Collections.singletonMap("stackId", stackId);
  }

  private String createStack(
      AmazonCloudFormation amazonCloudFormation,
      String templateURL,
      String templateBody,
      String roleARN,
      List<Parameter> parameters,
      List<Tag> tags,
      List<String> capabilities) {
    Task task = TaskRepository.threadLocalTask.get();
    task.updateStatus(BASE_PHASE, "Preparing CloudFormation Stack");
    CreateStackRequest createStackRequest =
        new CreateStackRequest()
            .withStackName(description.getStackName())
            .withParameters(parameters)
            .withTags(tags)
            .withCapabilities(capabilities);

    if (StringUtils.hasText(templateURL)) {
      createStackRequest.setTemplateURL(templateURL);
    } else {
      createStackRequest.setTemplateBody(templateBody);
    }

    if (StringUtils.hasText(roleARN)) {
      createStackRequest.setRoleARN(roleARN);
    }
    task.updateStatus(BASE_PHASE, "Uploading CloudFormation Stack");
    CreateStackResult createStackResult = amazonCloudFormation.createStack(createStackRequest);
    return createStackResult.getStackId();
  }

  private String updateStack(
      AmazonCloudFormation amazonCloudFormation,
      String templateURL,
      String templateBody,
      String roleARN,
      List<Parameter> parameters,
      List<Tag> tags,
      List<String> capabilities) {
    Task task = TaskRepository.threadLocalTask.get();
    task.updateStatus(BASE_PHASE, "CloudFormation Stack exists. Updating it");
    UpdateStackRequest updateStackRequest =
        new UpdateStackRequest()
            .withStackName(description.getStackName())
            .withParameters(parameters)
            .withTags(tags)
            .withCapabilities(capabilities);

    if (StringUtils.hasText(templateURL)) {
      updateStackRequest.setTemplateURL(templateURL);
    } else {
      updateStackRequest.setTemplateBody(templateBody);
    }

    if (StringUtils.hasText(roleARN)) {
      updateStackRequest.setRoleARN(roleARN);
    }
    task.updateStatus(BASE_PHASE, "Uploading CloudFormation Stack");
    try {
      UpdateStackResult updateStackResult = amazonCloudFormation.updateStack(updateStackRequest);
      return updateStackResult.getStackId();
    } catch (AmazonCloudFormationException e) {

      if (e.getMessage().contains(NO_CHANGE_STACK_ERROR_MESSAGE)) {
        // No changes on the stack, ignore failure
        return getStackId(amazonCloudFormation);
      }
      log.error("Error updating stack", e);
      throw e;
    }
  }

  private String createChangeSet(
      AmazonCloudFormation amazonCloudFormation,
      String templateURL,
      String templateBody,
      String roleARN,
      List<Parameter> parameters,
      List<Tag> tags,
      List<String> capabilities,
      ChangeSetType changeSetType) {
    Task task = TaskRepository.threadLocalTask.get();
    task.updateStatus(BASE_PHASE, "CloudFormation Stack exists. Creating a change set");
    CreateChangeSetRequest createChangeSetRequest =
        new CreateChangeSetRequest()
            .withStackName(description.getStackName())
            .withChangeSetName(description.getChangeSetName())
            .withParameters(parameters)
            .withTags(tags)
            .withCapabilities(capabilities)
            .withChangeSetType(changeSetType)
            .withIncludeNestedStacks(
                awsConfigurationProperties.getCloudformation().getChangeSetsIncludeNestedStacks());

    if (StringUtils.hasText(templateURL)) {
      createChangeSetRequest.setTemplateURL(templateURL);
    } else {
      createChangeSetRequest.setTemplateBody(templateBody);
    }

    if (StringUtils.hasText(roleARN)) {
      createChangeSetRequest.setRoleARN(roleARN);
    }

    task.updateStatus(BASE_PHASE, "Uploading CloudFormation ChangeSet");
    try {
      CreateChangeSetResult createChangeSetResult =
          amazonCloudFormation.createChangeSet(createChangeSetRequest);
      return createChangeSetResult.getStackId();
    } catch (AmazonCloudFormationException e) {
      log.error("Error creating change set", e);
      throw e;
    }
  }

  private boolean stackExists(AmazonCloudFormation amazonCloudFormation) {
    try {
      getStackId(amazonCloudFormation);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private String getStackId(AmazonCloudFormation amazonCloudFormation) {
    return amazonCloudFormation
        .describeStacks(new DescribeStacksRequest().withStackName(description.getStackName()))
        .getStacks()
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No CloudFormation Stack found with stack name " + description.getStackName()))
        .getStackId();
  }

  private void validateTemplate(
      AmazonCloudFormation amazonCloudFormation, String templateURL, String templateBody) {
    try {
      ValidateTemplateRequest validateTemplateRequest = new ValidateTemplateRequest();

      if (StringUtils.hasText(templateURL)) {
        validateTemplateRequest.setTemplateURL(templateURL);
      } else {
        validateTemplateRequest.setTemplateBody(templateBody);
      }

      amazonCloudFormation.validateTemplate(validateTemplateRequest);
    } catch (AmazonCloudFormationException e) {
      log.error("Error validating cloudformation template", e);
      throw e;
    }
  }
}
