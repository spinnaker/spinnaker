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
import com.amazonaws.services.lambda.model.*;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.UpsertLambdaFunctionEventMappingDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;

import java.util.HashMap;
import java.util.List;

public class UpsertLambdaEventSourceAtomicOperation
  extends AbstractLambdaAtomicOperation<UpsertLambdaFunctionEventMappingDescription, Object>
  implements AtomicOperation<Object> {

  public UpsertLambdaEventSourceAtomicOperation(UpsertLambdaFunctionEventMappingDescription description) {
    super(description, "UPSERT_LAMBDA_FUNCTION_EVENT_MAPPING");
  }

  @Override
  public Object operate(List priorOutputs) {
    String functionName = description.getFunctionName();
    String region = description.getRegion();
    String account = description.getAccount();

    LambdaFunction cache = (LambdaFunction) lambdaFunctionProvider.getFunction(account, region, functionName);

    List<EventSourceMappingConfiguration> eventSourceMappingConfigurations = cache.getEventSourceMappings();
    for (EventSourceMappingConfiguration eventSourceMappingConfiguration : eventSourceMappingConfigurations) {
      if (eventSourceMappingConfiguration.getEventSourceArn().equalsIgnoreCase(description.getEventSourceArn())) {
        description.setProperty("uuid", eventSourceMappingConfiguration.getUUID());
        return updateEventSourceMappingResult(cache);
      }
    }

    return createEventSourceMapping(cache);
  }

  private UpdateEventSourceMappingResult updateEventSourceMappingResult(LambdaFunction cache) {
    updateTaskStatus("Initializing Updating of AWS Lambda Function Event Mapping Operation...");

    AWSLambda client = getLambdaClient();
    UpdateEventSourceMappingRequest request = new UpdateEventSourceMappingRequest()
      .withFunctionName(cache.getFunctionArn())
      .withBatchSize(description.getBatchsize())
      .withEnabled(description.getEnabled())
      .withUUID(description.getUuid());

    UpdateEventSourceMappingResult result = client.updateEventSourceMapping(request);
    updateTaskStatus("Finished Updating of AWS Lambda Function Event Mapping Operation...");

    return result;
  }

  private CreateEventSourceMappingResult createEventSourceMapping(LambdaFunction cache) {
    updateTaskStatus("Initializing Creation of AWS Lambda Function Event Source Mapping...");

    AWSLambda client = getLambdaClient();
    CreateEventSourceMappingRequest request = new CreateEventSourceMappingRequest()
      .withFunctionName(cache.getFunctionArn())
      .withBatchSize(description.getBatchsize())
      .withEnabled(description.getEnabled())
      .withStartingPosition("LATEST")
      .withEventSourceArn(description.getEventSourceArn());

    CreateEventSourceMappingResult result = client.createEventSourceMapping(request);
    updateTaskStatus("Finished Creation of AWS Lambda Function Event Mapping Operation...");

    return result;
  }
}
