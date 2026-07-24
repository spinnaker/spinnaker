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

import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.UpsertLambdaFunctionEventMappingDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import java.util.Map;
import org.pf4j.util.StringUtils;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.CreateEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.CreateEventSourceMappingResponse;
import software.amazon.awssdk.services.lambda.model.UpdateEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.UpdateEventSourceMappingResponse;

public class UpsertLambdaEventSourceAtomicOperation
    extends AbstractLambdaAtomicOperation<UpsertLambdaFunctionEventMappingDescription, Object>
    implements AtomicOperation<Object> {

  public UpsertLambdaEventSourceAtomicOperation(
      UpsertLambdaFunctionEventMappingDescription description) {
    super(description, "UPSERT_LAMBDA_FUNCTION_EVENT_MAPPING");
  }

  @Override
  public Object operate(List priorOutputs) {
    String functionName = description.getFunctionName();
    String region = description.getRegion();
    String account = description.getAccount();

    LambdaFunction cache =
        (LambdaFunction) lambdaFunctionProvider.getFunction(account, region, functionName);

    List<Map<String, Object>> eventSourceMappingConfigurations = cache.getEventSourceMappings();
    if (eventSourceMappingConfigurations != null) {
      for (Map<String, Object> eventSourceMappingConfiguration : eventSourceMappingConfigurations) {
        if (description
            .getEventSourceArn()
            .equalsIgnoreCase((String) eventSourceMappingConfiguration.get("eventSourceArn"))) {
          description.setUuid((String) eventSourceMappingConfiguration.get("uuid"));
          return updateEventSourceMappingResult(cache);
        }
      }
    }

    return createEventSourceMapping(cache);
  }

  private UpdateEventSourceMappingResponse updateEventSourceMappingResult(LambdaFunction cache) {
    updateTaskStatus("Initializing Updating of AWS Lambda Function Event Mapping Operation...");

    LambdaClient client = getLambdaClient();
    UpdateEventSourceMappingRequest.Builder requestBuilder =
        UpdateEventSourceMappingRequest.builder()
            .functionName(cache.getFunctionArn())
            .batchSize(description.getBatchsize())
            .bisectBatchOnFunctionError(description.getBisectBatchOnError())
            .maximumBatchingWindowInSeconds(description.getMaxBatchingWindowSecs())
            .maximumRecordAgeInSeconds(description.getMaxRecordAgeSecs())
            .maximumRetryAttempts(description.getMaxRetryAttempts())
            .parallelizationFactor(description.getParallelizationFactor())
            .tumblingWindowInSeconds(description.getTumblingWindowSecs())
            .destinationConfig(description.getDestinationConfig())
            .enabled(description.getEnabled())
            .uuid(description.getUuid());

    if (StringUtils.isNotNullOrEmpty(description.getQualifier())) {
      String fullArnWithQualifier =
          String.format("%s:%s", cache.getFunctionArn(), description.getQualifier());
      requestBuilder.functionName(fullArnWithQualifier);
    }

    UpdateEventSourceMappingResponse result =
        client.updateEventSourceMapping(requestBuilder.build());
    updateTaskStatus("Finished Updating of AWS Lambda Function Event Mapping Operation...");

    return result;
  }

  private CreateEventSourceMappingResponse createEventSourceMapping(LambdaFunction cache) {
    updateTaskStatus("Initializing Creation of AWS Lambda Function Event Source Mapping...");

    LambdaClient client = getLambdaClient();
    CreateEventSourceMappingRequest.Builder requestBuilder =
        CreateEventSourceMappingRequest.builder()
            .functionName(cache.getFunctionArn())
            .batchSize(description.getBatchsize())
            .bisectBatchOnFunctionError(description.getBisectBatchOnError())
            .maximumBatchingWindowInSeconds(description.getMaxBatchingWindowSecs())
            .maximumRecordAgeInSeconds(description.getMaxRecordAgeSecs())
            .maximumRetryAttempts(description.getMaxRetryAttempts())
            .parallelizationFactor(description.getParallelizationFactor())
            .tumblingWindowInSeconds(description.getTumblingWindowSecs())
            .destinationConfig(description.getDestinationConfig())
            .enabled(description.getEnabled())
            .startingPosition(description.getStartingPosition())
            .eventSourceArn(description.getEventSourceArn());

    if (StringUtils.isNotNullOrEmpty(description.getQualifier())) {
      String fullArnWithQualifier =
          String.format("%s:%s", cache.getFunctionArn(), description.getQualifier());
      requestBuilder.functionName(fullArnWithQualifier);
    }

    CreateEventSourceMappingResponse result =
        client.createEventSourceMapping(requestBuilder.build());
    updateTaskStatus("Finished Creation of AWS Lambda Function Event Mapping Operation...");

    return result;
  }
}
