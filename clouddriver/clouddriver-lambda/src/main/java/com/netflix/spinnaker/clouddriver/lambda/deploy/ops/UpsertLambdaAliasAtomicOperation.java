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

import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.UpsertLambdaFunctionAliasDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.AliasRoutingConfiguration;
import software.amazon.awssdk.services.lambda.model.CreateAliasRequest;
import software.amazon.awssdk.services.lambda.model.CreateAliasResponse;
import software.amazon.awssdk.services.lambda.model.UpdateAliasRequest;
import software.amazon.awssdk.services.lambda.model.UpdateAliasResponse;

public class UpsertLambdaAliasAtomicOperation
    extends AbstractLambdaAtomicOperation<UpsertLambdaFunctionAliasDescription, Object>
    implements AtomicOperation<Object> {

  public UpsertLambdaAliasAtomicOperation(UpsertLambdaFunctionAliasDescription description) {
    super(description, "UPSERT_LAMBDA_FUNCTION_ALIAS");
  }

  @Override
  public Object operate(List priorOutputs) {

    String functionName = description.getFunctionName();
    String region = description.getRegion();
    String account = description.getAccount();
    LambdaFunction cache =
        (LambdaFunction) lambdaFunctionProvider.getFunction(account, region, functionName);
    List<Map<String, Object>> aliasConfigurations = cache.getAliasConfigurations();
    boolean aliasExists = false;

    if (aliasConfigurations != null) {
      for (Map<String, Object> aliasConfiguration : aliasConfigurations) {
        if (description.getAliasName().equalsIgnoreCase((String) aliasConfiguration.get("name"))) {
          aliasExists = true;
        }
      }
    }

    return aliasExists ? updateAliasResult(cache) : createAliasResult(cache);
  }

  private UpdateAliasResponse updateAliasResult(LambdaFunction cache) {
    updateTaskStatus("Initializing Updating of AWS Lambda Function Alias Operation...");

    Map<String, Double> routingConfig = new LinkedHashMap<>();
    String minorFunctionVersion = description.getMinorFunctionVersion();
    Double weightToMinorFunctionVersion = description.getWeightToMinorFunctionVersion();

    if (StringUtils.isNotEmpty(minorFunctionVersion) && weightToMinorFunctionVersion != null) {
      routingConfig.put(
          description.getMinorFunctionVersion(), description.getWeightToMinorFunctionVersion());
    }

    LambdaClient client = getLambdaClient();
    UpdateAliasRequest request =
        UpdateAliasRequest.builder()
            .functionName(cache.getFunctionArn())
            .description(description.getAliasDescription())
            .functionVersion(description.getMajorFunctionVersion())
            .name(description.getAliasName())
            .routingConfig(
                AliasRoutingConfiguration.builder().additionalVersionWeights(routingConfig).build())
            .build();

    UpdateAliasResponse result = client.updateAlias(request);
    updateTaskStatus("Finished Updating of AWS Lambda Function Alias Operation...");

    return result;
  }

  private CreateAliasResponse createAliasResult(LambdaFunction cache) {
    updateTaskStatus("Initializing Creation of AWS Lambda Function Alias Operation...");

    Map<String, Double> routingConfig = new LinkedHashMap<>();
    String minorFunctionVersion = description.getMinorFunctionVersion();
    Double weightToMinorFunctionVersion = description.getWeightToMinorFunctionVersion();

    if (StringUtils.isNotEmpty(minorFunctionVersion) && weightToMinorFunctionVersion != null) {
      routingConfig.put(
          description.getMinorFunctionVersion(), description.getWeightToMinorFunctionVersion());
    }

    LambdaClient client = getLambdaClient();
    CreateAliasRequest request =
        CreateAliasRequest.builder()
            .functionName(cache.getFunctionArn())
            .description(description.getAliasDescription())
            .functionVersion(description.getMajorFunctionVersion())
            .name(description.getAliasName())
            .routingConfig(
                AliasRoutingConfiguration.builder().additionalVersionWeights(routingConfig).build())
            .build();

    CreateAliasResponse result = client.createAlias(request);
    updateTaskStatus("Finished Creation of AWS Lambda Function Alias Operation...");

    return result;
  }
}
