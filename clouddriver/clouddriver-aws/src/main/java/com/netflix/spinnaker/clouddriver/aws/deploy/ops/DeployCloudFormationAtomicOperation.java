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
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.ChangeSetType;
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException;
import software.amazon.awssdk.services.cloudformation.model.CreateChangeSetRequest;
import software.amazon.awssdk.services.cloudformation.model.CreateChangeSetResponse;
import software.amazon.awssdk.services.cloudformation.model.CreateStackRequest;
import software.amazon.awssdk.services.cloudformation.model.CreateStackResponse;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksRequest;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.Tag;
import software.amazon.awssdk.services.cloudformation.model.UpdateStackRequest;
import software.amazon.awssdk.services.cloudformation.model.UpdateStackResponse;
import software.amazon.awssdk.services.cloudformation.model.ValidateTemplateRequest;

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
    CloudFormationClient cloudFormationClient =
        amazonClientProvider.getAmazonCloudFormationV2(
            description.getCredentials(), description.getRegion());
    String templateURL = description.getTemplateURL();
    String templateBody = description.getTemplateBody();
    validateTemplate(cloudFormationClient, templateURL, templateBody);
    String roleARN = description.getRoleARN();
    List<Parameter> parameters =
        description.getParameters().entrySet().stream()
            .map(
                entry ->
                    Parameter.builder()
                        .parameterKey(entry.getKey())
                        .parameterValue(entry.getValue())
                        .build())
            .collect(Collectors.toList());
    List<Tag> tags =
        description.getTags().entrySet().stream()
            .map(entry -> Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
            .collect(Collectors.toList());

    List<String> notificationARNs =
        Optional.ofNullable(description.getNotificationARNs()).orElse(Collections.emptyList());

    boolean stackExists = stackExists(cloudFormationClient);

    String stackId;
    if (description.isChangeSet()) {
      ChangeSetType changeSetType = stackExists ? ChangeSetType.UPDATE : ChangeSetType.CREATE;
      log.info("{} change set for stack: {}", changeSetType, description);
      stackId =
          createChangeSet(
              cloudFormationClient,
              templateURL,
              templateBody,
              roleARN,
              parameters,
              tags,
              description.getCapabilities(),
              notificationARNs,
              changeSetType);
    } else {
      if (stackExists) {
        log.info("Updating existing stack {}", description);
        stackId =
            updateStack(
                cloudFormationClient,
                templateURL,
                templateBody,
                roleARN,
                parameters,
                tags,
                description.getCapabilities(),
                notificationARNs);
      } else {
        log.info("Creating new stack: {}", description);
        stackId =
            createStack(
                cloudFormationClient,
                templateURL,
                templateBody,
                roleARN,
                parameters,
                tags,
                description.getCapabilities(),
                notificationARNs);
      }
    }
    return Collections.singletonMap("stackId", stackId);
  }

  private String createStack(
      CloudFormationClient cloudFormationClient,
      String templateURL,
      String templateBody,
      String roleARN,
      List<Parameter> parameters,
      List<Tag> tags,
      List<String> capabilities,
      List<String> notificationARNs) {
    Task task = TaskRepository.threadLocalTask.get();
    task.updateStatus(BASE_PHASE, "Preparing CloudFormation Stack");
    CreateStackRequest.Builder requestBuilder =
        CreateStackRequest.builder()
            .stackName(description.getStackName())
            .parameters(parameters)
            .tags(tags)
            .capabilitiesWithStrings(capabilities)
            .notificationARNs(notificationARNs);

    if (StringUtils.hasText(templateURL)) {
      requestBuilder.templateURL(templateURL);
    } else {
      requestBuilder.templateBody(templateBody);
    }

    if (StringUtils.hasText(roleARN)) {
      requestBuilder.roleARN(roleARN);
    }
    task.updateStatus(BASE_PHASE, "Uploading CloudFormation Stack");
    CreateStackResponse createStackResponse =
        cloudFormationClient.createStack(requestBuilder.build());
    return createStackResponse.stackId();
  }

  private String updateStack(
      CloudFormationClient cloudFormationClient,
      String templateURL,
      String templateBody,
      String roleARN,
      List<Parameter> parameters,
      List<Tag> tags,
      List<String> capabilities,
      List<String> notificationARNs) {
    Task task = TaskRepository.threadLocalTask.get();
    task.updateStatus(BASE_PHASE, "CloudFormation Stack exists. Updating it");
    UpdateStackRequest.Builder requestBuilder =
        UpdateStackRequest.builder()
            .stackName(description.getStackName())
            .parameters(parameters)
            .tags(tags)
            .capabilitiesWithStrings(capabilities)
            .notificationARNs(notificationARNs);

    if (StringUtils.hasText(templateURL)) {
      requestBuilder.templateURL(templateURL);
    } else {
      requestBuilder.templateBody(templateBody);
    }

    if (StringUtils.hasText(roleARN)) {
      requestBuilder.roleARN(roleARN);
    }
    task.updateStatus(BASE_PHASE, "Uploading CloudFormation Stack");
    try {
      UpdateStackResponse updateStackResponse =
          cloudFormationClient.updateStack(requestBuilder.build());
      return updateStackResponse.stackId();
    } catch (CloudFormationException e) {
      if (e.getMessage().contains(NO_CHANGE_STACK_ERROR_MESSAGE)) {
        // No changes on the stack, ignore failure
        return getStackId(cloudFormationClient);
      }
      log.error("Error updating stack", e);
      throw e;
    }
  }

  private String createChangeSet(
      CloudFormationClient cloudFormationClient,
      String templateURL,
      String templateBody,
      String roleARN,
      List<Parameter> parameters,
      List<Tag> tags,
      List<String> capabilities,
      List<String> notificationARNs,
      ChangeSetType changeSetType) {
    Task task = TaskRepository.threadLocalTask.get();
    task.updateStatus(BASE_PHASE, "CloudFormation Stack exists. Creating a change set");
    CreateChangeSetRequest.Builder requestBuilder =
        CreateChangeSetRequest.builder()
            .stackName(description.getStackName())
            .changeSetName(description.getChangeSetName())
            .parameters(parameters)
            .tags(tags)
            .capabilitiesWithStrings(capabilities)
            .notificationARNs(notificationARNs)
            .changeSetType(changeSetType)
            .includeNestedStacks(
                awsConfigurationProperties.getCloudformation().getChangeSetsIncludeNestedStacks());

    if (StringUtils.hasText(templateURL)) {
      requestBuilder.templateURL(templateURL);
    } else {
      requestBuilder.templateBody(templateBody);
    }

    if (StringUtils.hasText(roleARN)) {
      requestBuilder.roleARN(roleARN);
    }

    task.updateStatus(BASE_PHASE, "Uploading CloudFormation ChangeSet");
    try {
      CreateChangeSetResponse createChangeSetResponse =
          cloudFormationClient.createChangeSet(requestBuilder.build());
      return createChangeSetResponse.stackId();
    } catch (CloudFormationException e) {
      log.error("Error creating change set", e);
      throw e;
    }
  }

  private boolean stackExists(CloudFormationClient cloudFormationClient) {
    try {
      getStackId(cloudFormationClient);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private String getStackId(CloudFormationClient cloudFormationClient) {
    return cloudFormationClient
        .describeStacks(
            DescribeStacksRequest.builder().stackName(description.getStackName()).build())
        .stacks()
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No CloudFormation Stack found with stack name " + description.getStackName()))
        .stackId();
  }

  private void validateTemplate(
      CloudFormationClient cloudFormationClient, String templateURL, String templateBody) {
    try {
      ValidateTemplateRequest.Builder requestBuilder = ValidateTemplateRequest.builder();

      if (StringUtils.hasText(templateURL)) {
        requestBuilder.templateURL(templateURL);
      } else {
        requestBuilder.templateBody(templateBody);
      }

      cloudFormationClient.validateTemplate(requestBuilder.build());
    } catch (CloudFormationException e) {
      log.error("Error validating cloudformation template", e);
      throw e;
    }
  }
}
