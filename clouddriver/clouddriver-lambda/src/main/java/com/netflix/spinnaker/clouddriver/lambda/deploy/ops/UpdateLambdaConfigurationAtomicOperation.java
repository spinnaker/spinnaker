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

import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DeregisterTargetsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DeregisterTargetsResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult;
import com.amazonaws.services.elasticloadbalancingv2.model.RegisterTargetsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.RegisterTargetsResult;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetDescription;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.CreateLambdaFunctionConfigurationDescription;
import com.netflix.spinnaker.clouddriver.lambda.names.LambdaTagNamer;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.Environment;
import software.amazon.awssdk.services.lambda.model.ListTagsRequest;
import software.amazon.awssdk.services.lambda.model.ListTagsResponse;
import software.amazon.awssdk.services.lambda.model.TagResourceRequest;
import software.amazon.awssdk.services.lambda.model.UntagResourceRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationResponse;
import software.amazon.awssdk.services.lambda.model.VpcConfig;

public class UpdateLambdaConfigurationAtomicOperation
    extends AbstractLambdaAtomicOperation<
        CreateLambdaFunctionConfigurationDescription, UpdateFunctionConfigurationResponse>
    implements AtomicOperation<UpdateFunctionConfigurationResponse> {

  private boolean autoApplyTags;

  public UpdateLambdaConfigurationAtomicOperation(
      CreateLambdaFunctionConfigurationDescription description, boolean autoApplyTags) {
    super(description, "UPDATE_LAMBDA_FUNCTION_CONFIGURATION");
    this.autoApplyTags = autoApplyTags;
  }

  @Override
  public UpdateFunctionConfigurationResponse operate(List priorOutputs) {
    updateTaskStatus("Initializing Updating of AWS Lambda Function Configuration Operation...");
    return updateFunctionConfigurationResult();
  }

  private UpdateFunctionConfigurationResponse updateFunctionConfigurationResult() {
    LambdaFunction cache =
        (LambdaFunction)
            lambdaFunctionProvider.getFunction(
                description.getAccount(), description.getRegion(), description.getFunctionName());

    LambdaClient client = getLambdaClient();

    UpdateFunctionConfigurationRequest.Builder requestBuilder =
        UpdateFunctionConfigurationRequest.builder()
            .functionName(cache.getFunctionArn())
            .description(description.getDescription())
            .handler(description.getHandler())
            .memorySize(description.getMemorySize())
            .role(description.getRole())
            .timeout(description.getTimeout())
            .layers(description.getLayers())
            .kmsKeyArn(description.getKmskeyArn())
            .runtime(description.getRuntime());

    if (description.getDeadLetterConfig() != null) {
      requestBuilder.deadLetterConfig(description.getDeadLetterConfig());
    }

    if (description.getSecurityGroupIds() != null || description.getSubnetIds() != null) {
      requestBuilder.vpcConfig(
          VpcConfig.builder()
              .securityGroupIds(description.getSecurityGroupIds())
              .subnetIds(description.getSubnetIds())
              .build());
    }

    if (description.getTracingConfig() != null && description.getTracingConfig().mode() != null) {
      requestBuilder.tracingConfig(description.getTracingConfig());
    }

    if (null != description.getEnvVariables()) {
      requestBuilder.environment(
          Environment.builder().variables(description.getEnvVariables()).build());
    }
    LambdaTagNamer.applyIfNeeded(description, description.getAppName(), autoApplyTags);

    UpdateFunctionConfigurationResponse result =
        client.updateFunctionConfiguration(requestBuilder.build());

    Map<String, String> objTag = new HashMap<>();
    if (null != description.getTags()) {

      for (Map.Entry<String, String> entry : description.getTags().entrySet()) {
        objTag.put(entry.getKey(), entry.getValue());
      }
    }
    if (!objTag.isEmpty()) {

      ListTagsResponse existingTags =
          client.listTags(ListTagsRequest.builder().resource(result.functionArn()).build());

      List<String> existingTagKeys = List.copyOf(existingTags.tags().keySet());
      if (!existingTagKeys.isEmpty()) {
        client.untagResource(
            UntagResourceRequest.builder()
                .resource(result.functionArn())
                .tagKeys(existingTagKeys)
                .build());
      }
      client.tagResource(
          TagResourceRequest.builder().resource(result.functionArn()).tags(objTag).build());
    }
    updateTaskStatus("Finished Updating of AWS Lambda Function Configuration Operation...");
    if (StringUtils.isEmpty(description.getTargetGroups())) {
      if (cache.getTargetGroups() != null && !cache.getTargetGroups().isEmpty()) {
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
      List<String> cacheTargetGroups = cache.getTargetGroups();
      if (cacheTargetGroups == null || cacheTargetGroups.isEmpty()) {
        registerTarget(
            loadBalancingV2,
            cache.getFunctionArn(),
            retrieveTargetGroup(loadBalancingV2, description.getTargetGroups())
                .getTargetGroupArn());
        updateTaskStatus("Registered the target group...");
      } else {
        for (String groupName : cacheTargetGroups) {
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
    NetflixAmazonCredentials credentialAccount = description.getCredentials();

    return getAmazonClientProvider()
        .getAmazonElasticLoadBalancingV2(credentialAccount, getRegion(), false);
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
