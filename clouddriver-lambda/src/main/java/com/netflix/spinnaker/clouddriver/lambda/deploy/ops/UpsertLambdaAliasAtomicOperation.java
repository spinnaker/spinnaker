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

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.AliasRoutingConfiguration;
import com.amazonaws.services.lambda.model.CreateAliasRequest;
import com.amazonaws.services.lambda.model.CreateAliasResult;
import com.amazonaws.services.lambda.model.UpdateAliasRequest;
import com.amazonaws.services.lambda.model.UpdateAliasResult;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.UpsertLambdaFunctionAliasDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.amazonaws.services.lambda.model.AliasConfiguration;
import org.apache.commons.lang.StringUtils;

import java.util.*;

public class UpsertLambdaAliasAtomicOperation
  extends AbstractLambdaAtomicOperation<UpsertLambdaFunctionAliasDescription, Object>
  implements AtomicOperation<Object> {

  public UpsertLambdaAliasAtomicOperation(UpsertLambdaFunctionAliasDescription description) {
    super(description, "UPSERT_LAMBDA_FUNCTION_ALIAS");
  }

  @Override
  public Object operate(List priorOutputs) {
    LambdaFunction lambdaFunction = (LambdaFunction) lambdaFunctionProvider.getFunction(
      description.getAccount(), description.getRegion(), description.getFunctionName()
    );

    boolean aliasExists = false;

    for (AliasConfiguration aliasConfiguration : lambdaFunction.getAliasConfigurations()) {
      if (aliasConfiguration.getName().equalsIgnoreCase(description.getAliasName())) {
        aliasExists = true;
      }
    }

    return aliasExists ? updateAliasResult(lambdaFunction) : createAliasResult(lambdaFunction);
  }

  private UpdateAliasResult updateAliasResult(LambdaFunction cache) {
    updateTaskStatus("Initializing Updating of AWS Lambda Function Alias Operation...");

    Map<String, Double> routingConfig = new LinkedHashMap<>();
    String minorFunctionVersion = description.getMinorFunctionVersion();
    Double weightToMinorFunctionVersion = description.getWeightToMinorFunctionVersion();

    if (StringUtils.isNotEmpty(minorFunctionVersion) && weightToMinorFunctionVersion != null) {
      routingConfig.put(description.getMinorFunctionVersion(), description.getWeightToMinorFunctionVersion());
    }

    AWSLambda client = getLambdaClient();
    UpdateAliasRequest request = new UpdateAliasRequest()
      .withFunctionName(cache.getFunctionArn())
      .withDescription(description.getAliasDescription())
      .withFunctionVersion(description.getMajorFunctionVersion())
      .withName(description.getAliasName())
      .withRoutingConfig(new AliasRoutingConfiguration().withAdditionalVersionWeights(routingConfig));

    UpdateAliasResult result = client.updateAlias(request);
    updateTaskStatus("Finished Updating of AWS Lambda Function Alias Operation...");

    return result;
  }

  private CreateAliasResult createAliasResult(LambdaFunction cache) {
    updateTaskStatus("Initializing Creation of AWS Lambda Function Alias Operation...");

    Map<String, Double> routingConfig = new LinkedHashMap<>();
    String minorFunctionVersion = description.getMinorFunctionVersion();
    Double weightToMinorFunctionVersion = description.getWeightToMinorFunctionVersion();

    if (StringUtils.isNotEmpty(minorFunctionVersion) && weightToMinorFunctionVersion != null) {
      routingConfig.put(description.getMinorFunctionVersion(), description.getWeightToMinorFunctionVersion());
    }

    AWSLambda client = getLambdaClient();
    CreateAliasRequest request = new CreateAliasRequest()
      .withFunctionName(cache.getFunctionArn())
      .withDescription(description.getAliasDescription())
      .withFunctionVersion(description.getMajorFunctionVersion())
      .withName(description.getAliasName())
      .withRoutingConfig(new AliasRoutingConfiguration().withAdditionalVersionWeights(routingConfig));

    CreateAliasResult result = client.createAlias(request);
    updateTaskStatus("Finished Creation of AWS Lambda Function Alias Operation...");

    return result;
  }
}
