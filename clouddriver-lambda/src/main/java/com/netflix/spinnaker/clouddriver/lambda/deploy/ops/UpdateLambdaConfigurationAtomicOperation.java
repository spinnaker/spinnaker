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
import com.amazonaws.services.elasticloadbalancingv2.model.*;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.*;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.CreateLambdaFunctionConfigurationDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

public class UpdateLambdaConfigurationAtomicOperation
    extends AbstractLambdaAtomicOperation<
        CreateLambdaFunctionConfigurationDescription, UpdateFunctionConfigurationResult>
    implements AtomicOperation<UpdateFunctionConfigurationResult> {

  public UpdateLambdaConfigurationAtomicOperation(
      CreateLambdaFunctionConfigurationDescription description) {
    super(description, "UPDATE_LAMBDA_FUNCTION_CONFIGURATION");
  }

  @Override
  public UpdateFunctionConfigurationResult operate(List priorOutputs) {
    updateTaskStatus("Initializing Updating of AWS Lambda Function Configuration Operation...");
    return updateFunctionConfigurationResult();
  }

  private UpdateFunctionConfigurationResult updateFunctionConfigurationResult() {
    LambdaFunction cache =
        (LambdaFunction)
            lambdaFunctionProvider.getFunction(
                description.getAccount(), description.getRegion(), description.getFunctionName());

    AWSLambda client = getLambdaClient();

    UpdateFunctionConfigurationRequest request =
        new UpdateFunctionConfigurationRequest()
            .withFunctionName(cache.getFunctionArn())
            .withDescription(description.getDescription())
            .withHandler(description.getHandler())
            .withMemorySize(description.getMemorySize())
            .withRole(description.getRole())
            .withTimeout(description.getTimeout())
            .withDeadLetterConfig(description.getDeadLetterConfig())
            .withVpcConfig(
                new VpcConfig()
                    .withSecurityGroupIds(description.getSecurityGroupIds())
                    .withSubnetIds(description.getSubnetIds()))
            .withKMSKeyArn(description.getKmskeyArn())
            .withTracingConfig(description.getTracingConfig())
            .withRuntime(description.getRuntime());

    if (null != description.getEnvVariables()) {
      request.setEnvironment(new Environment().withVariables(description.getEnvVariables()));
    }

    UpdateFunctionConfigurationResult result = client.updateFunctionConfiguration(request);
    TagResourceRequest tagResourceRequest = new TagResourceRequest();
    Map<String, String> objTag = new HashMap<>();
    if (null != description.getTags()) {

      for (Map.Entry<String, String> entry : description.getTags().entrySet()) {
        objTag.put(entry.getKey(), entry.getValue());
      }
    }
    if (!objTag.isEmpty()) {

      UntagResourceRequest untagResourceRequest =
          new UntagResourceRequest().withResource(result.getFunctionArn());
      ListTagsResult existingTags =
          client.listTags(new ListTagsRequest().withResource(result.getFunctionArn()));
      for (Map.Entry<String, String> entry : existingTags.getTags().entrySet()) {
        untagResourceRequest.getTagKeys().add(entry.getKey());
      }
      if (!untagResourceRequest.getTagKeys().isEmpty()) {
        client.untagResource(untagResourceRequest);
      }
      for (Map.Entry<String, String> entry : objTag.entrySet()) {
        tagResourceRequest.addTagsEntry(entry.getKey(), entry.getValue());
      }
      tagResourceRequest.setResource(result.getFunctionArn());
      client.tagResource(tagResourceRequest);
    }
    updateTaskStatus("Finished Updating of AWS Lambda Function Configuration Operation...");
    if (StringUtils.isEmpty(description.getTargetGroups())) {
      if (!cache.getTargetGroups().isEmpty()) {
        AmazonElasticLoadBalancing loadBalancingV2 = getAmazonElasticLoadBalancingClient();
        for (String groupName : cache.getTargetGroups()) {
          deregisterTarget(
              loadBalancingV2,
              cache.getFunctionArn(),
              retrieveTargetGroup(loadBalancingV2, groupName).getTargetGroupArn());
          updateTaskStatus("De-registered the target group...");
        }
      }

    } else {
      AmazonElasticLoadBalancing loadBalancingV2 = getAmazonElasticLoadBalancingClient();
      if (cache.getTargetGroups().isEmpty()) {
        registerTarget(
            loadBalancingV2,
            cache.getFunctionArn(),
            retrieveTargetGroup(loadBalancingV2, description.getTargetGroups())
                .getTargetGroupArn());
        updateTaskStatus("Registered the target group...");
      } else {
        for (String groupName : cache.getTargetGroups()) {
          if (!groupName.equals(description.getTargetGroups())) {
            registerTarget(
                loadBalancingV2,
                cache.getFunctionArn(),
                retrieveTargetGroup(loadBalancingV2, description.getTargetGroups())
                    .getTargetGroupArn());
            updateTaskStatus("Registered the target group...");
          }
        }
      }
    }
    return result;
  }

  private TargetGroup retrieveTargetGroup(
      AmazonElasticLoadBalancing loadBalancingV2, String targetGroupName) {

    DescribeTargetGroupsRequest request =
        new DescribeTargetGroupsRequest().withNames(targetGroupName);
    DescribeTargetGroupsResult describeTargetGroupsResult =
        loadBalancingV2.describeTargetGroups(request);

    if (describeTargetGroupsResult.getTargetGroups().size() == 1) {
      return describeTargetGroupsResult.getTargetGroups().get(0);
    } else if (describeTargetGroupsResult.getTargetGroups().size() > 1) {
      throw new IllegalArgumentException(
          "There are multiple target groups with the name " + targetGroupName + ".");
    } else {
      throw new IllegalArgumentException(
          "There is no target group with the name " + targetGroupName + ".");
    }
  }

  private AmazonElasticLoadBalancing getAmazonElasticLoadBalancingClient() {
    AWSCredentialsProvider credentialsProvider = getCredentials().getCredentialsProvider();
    NetflixAmazonCredentials credentialAccount = description.getCredentials();

    return amazonClientProvider.getAmazonElasticLoadBalancingV2(
        credentialAccount, getRegion(), false);
  }

  private void registerTarget(
      AmazonElasticLoadBalancing loadBalancingV2, String functionArn, String targetGroupArn) {
    RegisterTargetsResult result =
        loadBalancingV2.registerTargets(
            new RegisterTargetsRequest()
                .withTargets(new TargetDescription().withId(functionArn))
                .withTargetGroupArn(targetGroupArn));
  }

  private void deregisterTarget(
      AmazonElasticLoadBalancing loadBalancingV2, String functionArn, String targetGroupArn) {
    DeregisterTargetsResult result =
        loadBalancingV2.deregisterTargets(
            new DeregisterTargetsRequest()
                .withTargetGroupArn(targetGroupArn)
                .withTargets(new TargetDescription().withId(functionArn)));
  }
}
