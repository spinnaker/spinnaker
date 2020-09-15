/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult;
import com.amazonaws.services.elasticloadbalancingv2.model.RegisterTargetsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.RegisterTargetsResult;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetDescription;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.AddPermissionRequest;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.Environment;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.amazonaws.services.lambda.model.VpcConfig;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.CreateLambdaFunctionDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

public class CreateLambdaAtomicOperation
    extends AbstractLambdaAtomicOperation<CreateLambdaFunctionDescription, CreateFunctionResult>
    implements AtomicOperation<CreateFunctionResult> {

  public CreateLambdaAtomicOperation(CreateLambdaFunctionDescription description) {
    super(description, "CREATE_LAMBDA_FUNCTION");
  }

  @Override
  public CreateFunctionResult operate(List priorOutputs) {
    updateTaskStatus("Initializing Creation of AWS Lambda Function Operation...");
    return createFunction();
  }

  private CreateFunctionResult createFunction() {
    FunctionCode code =
        new FunctionCode()
            .withS3Bucket(description.getProperty("s3bucket").toString())
            .withS3Key(description.getProperty("s3key").toString());

    Map<String, String> objTag = new HashMap<>();
    if (null != description.getTags()) {

      for (Entry<String, String> entry : description.getTags().entrySet()) {
        objTag.put(entry.getKey(), entry.getValue());
      }
    }

    AWSLambda client = getLambdaClient();

    CreateFunctionRequest request = new CreateFunctionRequest();
    request.setFunctionName(
        combineAppDetail(description.getAppName(), description.getFunctionName()));
    request.setDescription(description.getDescription());
    request.setHandler(description.getHandler());
    request.setMemorySize(description.getMemorySize());
    request.setPublish(description.getPublish());
    request.setRole(description.getRole());
    request.setRuntime(description.getRuntime());
    request.setTimeout(description.getTimeout());

    request.setCode(code);
    request.setTags(objTag);

    Map<String, String> envVariables = description.getEnvVariables();
    if (null != envVariables) {
      request.setEnvironment(new Environment().withVariables(envVariables));
    }

    if (null != description.getSecurityGroupIds() || null != description.getSubnetIds()) {
      request.setVpcConfig(
          new VpcConfig()
              .withSecurityGroupIds(description.getSecurityGroupIds())
              .withSubnetIds(description.getSubnetIds()));
    }
    if (!description.getDeadLetterConfig().getTargetArn().isEmpty()) {
      request.setDeadLetterConfig(description.getDeadLetterConfig());
    }
    request.setKMSKeyArn(description.getKmskeyArn());
    request.setTracingConfig(description.getTracingConfig());

    CreateFunctionResult result = client.createFunction(request);
    updateTaskStatus("Finished Creation of AWS Lambda Function Operation...");
    if (description.getTargetGroups() != null && !description.getTargetGroups().isEmpty()) {

      updateTaskStatus(
          String.format(
              "Started registering lambda to targetGroup (%s)", description.getTargetGroups()));
      String functionArn = result.getFunctionArn();
      registerTargetGroup(functionArn, client);
    }

    return result;
  }

  protected String combineAppDetail(String appName, String functionName) {
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

  private RegisterTargetsResult registerTargetGroup(String functionArn, AWSLambda lambdaClient) {

    AmazonElasticLoadBalancing loadBalancingV2 = getAmazonElasticLoadBalancingClient();
    TargetGroup targetGroup = retrieveTargetGroup(loadBalancingV2);

    AddPermissionRequest addPermissionRequest =
        new AddPermissionRequest()
            .withFunctionName(functionArn)
            .withAction("lambda:InvokeFunction")
            .withSourceArn(targetGroup.getTargetGroupArn())
            .withPrincipal("elasticloadbalancing.amazonaws.com")
            .withStatementId(UUID.randomUUID().toString());

    lambdaClient.addPermission(addPermissionRequest);

    updateTaskStatus(
        String.format(
            "Lambda (%s) invoke permissions added to Target group (%s).",
            functionArn, targetGroup.getTargetGroupArn()));

    RegisterTargetsResult result =
        loadBalancingV2.registerTargets(
            new RegisterTargetsRequest()
                .withTargets(new TargetDescription().withId(functionArn))
                .withTargetGroupArn(targetGroup.getTargetGroupArn()));

    updateTaskStatus(
        String.format(
            "Registered the Lambda (%s) with Target group (%s).",
            functionArn, targetGroup.getTargetGroupArn()));
    return result;
  }

  private TargetGroup retrieveTargetGroup(AmazonElasticLoadBalancing loadBalancingV2) {

    DescribeTargetGroupsRequest request =
        new DescribeTargetGroupsRequest().withNames(description.getTargetGroups());
    DescribeTargetGroupsResult describeTargetGroupsResult =
        loadBalancingV2.describeTargetGroups(request);

    if (describeTargetGroupsResult.getTargetGroups().size() == 1) {
      return describeTargetGroupsResult.getTargetGroups().get(0);
    } else if (describeTargetGroupsResult.getTargetGroups().size() > 1) {
      throw new IllegalArgumentException(
          "There are multiple target groups with the name " + description.getTargetGroups() + ".");
    } else {
      throw new IllegalArgumentException(
          "There is no target group with the name " + description.getTargetGroups() + ".");
    }
  }

  private AmazonElasticLoadBalancing getAmazonElasticLoadBalancingClient() {
    AWSCredentialsProvider credentialsProvider = getCredentials().getCredentialsProvider();
    NetflixAmazonCredentials credentialAccount = description.getCredentials();

    return amazonClientProvider.getAmazonElasticLoadBalancingV2(
        credentialAccount, getRegion(), false);
  }
}
