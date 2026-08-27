/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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
 */

package com.netflix.spinnaker.clouddriver.lambda.deploy.ops;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.CreateLambdaFunctionDescription;
import com.netflix.spinnaker.clouddriver.lambda.names.LambdaTagNamer;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.config.LambdaConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.RegisterTargetsRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.RegisterTargetsResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetDescription;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.AddPermissionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.Environment;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.VpcConfig;

public class CreateLambdaAtomicOperation
    extends AbstractLambdaAtomicOperation<CreateLambdaFunctionDescription, CreateFunctionResponse>
    implements AtomicOperation<CreateFunctionResponse> {

  private final LambdaConfiguration config;

  public CreateLambdaAtomicOperation(
      CreateLambdaFunctionDescription description, LambdaConfiguration config) {
    super(description, "CREATE_LAMBDA_FUNCTION");
    this.config = config;
  }

  @Override
  public CreateFunctionResponse operate(List priorOutputs) {
    updateTaskStatus("Initializing Creation of AWS Lambda Function Operation...");
    return createFunction();
  }

  private CreateFunctionResponse createFunction() {
    FunctionCode code =
        FunctionCode.builder()
            .s3Bucket(description.getS3bucket())
            .s3Key(description.getS3key())
            .build();

    LambdaTagNamer.applyIfNeeded(description, description.getAppName(), config.isSetMonikerTags());

    Map<String, String> objTag = new HashMap<>();
    if (null != description.getTags()) {

      for (Entry<String, String> entry : description.getTags().entrySet()) {
        objTag.put(entry.getKey(), entry.getValue());
      }
    }

    LambdaClient client = getLambdaClient();

    CreateFunctionRequest.Builder requestBuilder =
        CreateFunctionRequest.builder()
            .functionName(combineAppDetail(description.getAppName(), description.getFunctionName()))
            .description(description.getDescription())
            .handler(description.getHandler())
            .memorySize(description.getMemorySize())
            .publish(description.getPublish())
            .role(description.getRole())
            .runtime(description.getRuntime())
            .timeout(description.getTimeout())
            .layers(description.getLayers())
            .code(code)
            .tags(objTag);

    Map<String, String> envVariables = description.getEnvVariables();
    if (null != envVariables) {
      requestBuilder.environment(Environment.builder().variables(envVariables).build());
    }

    if (null != description.getSecurityGroupIds() || null != description.getSubnetIds()) {
      requestBuilder.vpcConfig(
          VpcConfig.builder()
              .securityGroupIds(description.getSecurityGroupIds())
              .subnetIds(description.getSubnetIds())
              .build());
    }
    if (description.getDeadLetterConfig() != null
        && description.getDeadLetterConfig().targetArn() != null
        && !description.getDeadLetterConfig().targetArn().isEmpty()) {
      requestBuilder.deadLetterConfig(description.getDeadLetterConfig());
    }
    requestBuilder.kmsKeyArn(description.getKmskeyArn());
    if (description.getTracingConfig() != null && description.getTracingConfig().mode() != null) {
      requestBuilder.tracingConfig(description.getTracingConfig());
    }

    CreateFunctionResponse result = client.createFunction(requestBuilder.build());
    updateTaskStatus("Finished Creation of AWS Lambda Function Operation...");
    if (description.getTargetGroups() != null && !description.getTargetGroups().isEmpty()) {

      updateTaskStatus(
          String.format(
              "Started registering lambda to targetGroup (%s)", description.getTargetGroups()));
      String functionArn = result.functionArn();
      registerTargetGroup(functionArn, client);
    }

    return result;
  }

  protected String combineAppDetail(String appName, String functionName) {
    if (!config.isPrefixApplicationNameToFunction()) {
      return functionName;
    }
    Names functionAppName = Names.parseName(functionName);
    if (null != functionAppName) {
      return functionAppName.getApp().equals(appName)
          ? functionName
          : (appName + "-" + functionName);
    } else {
      throw new IllegalArgumentException(
          String.format("Function name {%s} contains invlaid charachetrs ", functionName));
    }
  }

  private RegisterTargetsResponse registerTargetGroup(
      String functionArn, LambdaClient lambdaClient) {

    ElasticLoadBalancingV2Client loadBalancingV2 = getAmazonElasticLoadBalancingClient();
    TargetGroup targetGroup = retrieveTargetGroup(loadBalancingV2);

    AddPermissionRequest addPermissionRequest =
        AddPermissionRequest.builder()
            .functionName(functionArn)
            .action("lambda:InvokeFunction")
            .sourceArn(targetGroup.targetGroupArn())
            .principal("elasticloadbalancing.amazonaws.com")
            .statementId(UUID.randomUUID().toString())
            .build();

    lambdaClient.addPermission(addPermissionRequest);

    updateTaskStatus(
        String.format(
            "Lambda (%s) invoke permissions added to Target group (%s).",
            functionArn, targetGroup.targetGroupArn()));

    RegisterTargetsResponse result =
        loadBalancingV2.registerTargets(
            RegisterTargetsRequest.builder()
                .targetGroupArn(targetGroup.targetGroupArn())
                .targets(TargetDescription.builder().id(functionArn).build())
                .build());

    updateTaskStatus(
        String.format(
            "Registered the Lambda (%s) with Target group (%s).",
            functionArn, targetGroup.targetGroupArn()));
    return result;
  }

  private TargetGroup retrieveTargetGroup(ElasticLoadBalancingV2Client loadBalancingV2) {

    DescribeTargetGroupsRequest request =
        DescribeTargetGroupsRequest.builder().names(description.getTargetGroups()).build();
    DescribeTargetGroupsResponse describeTargetGroupsResult =
        loadBalancingV2.describeTargetGroups(request);

    if (describeTargetGroupsResult.targetGroups().size() == 1) {
      return describeTargetGroupsResult.targetGroups().get(0);
    } else if (describeTargetGroupsResult.targetGroups().size() > 1) {
      throw new IllegalArgumentException(
          "There are multiple target groups with the name " + description.getTargetGroups() + ".");
    } else {
      throw new IllegalArgumentException(
          "There is no target group with the name " + description.getTargetGroups() + ".");
    }
  }

  private ElasticLoadBalancingV2Client getAmazonElasticLoadBalancingClient() {

    return getAmazonClientProvider()
        .getElasticLoadBalancingV2Client(description.getCredentials(), getRegion());
  }
}
