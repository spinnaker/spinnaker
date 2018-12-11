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

public class DeleteLambdaEventSourceAtomicOperation
  extends AbstractLambdaAtomicOperation<UpsertLambdaFunctionEventMappingDescription, Object>
  implements AtomicOperation<Object> {

  public DeleteLambdaEventSourceAtomicOperation(UpsertLambdaFunctionEventMappingDescription description) {
    super(description, "DELETE_LAMBDA_FUNCTION_EVENT_MAPPING");
  }

  @Override
  public Object operate(List priorOutputs) {
    LambdaFunction lambdaFunction = (LambdaFunction) lambdaFunctionProvider.getFunction(
      description.getAccount(), description.getRegion(), description.getFunctionName()
    );

    List<EventSourceMappingConfiguration> eventSourceMappingConfigurations = lambdaFunction.getEventSourceMappings();

    for (EventSourceMappingConfiguration eventSourceMappingConfiguration : eventSourceMappingConfigurations) {
      if (eventSourceMappingConfiguration.getEventSourceArn().equalsIgnoreCase(description.getEventSourceArn())) {
        description.setUuid(eventSourceMappingConfiguration.getUUID());
        return deleteEventSourceMappingResult();
      }
    }

    return null;
  }

  private DeleteEventSourceMappingResult deleteEventSourceMappingResult() {
    updateTaskStatus("Initializing Deleting of AWS Lambda Function Event Mapping Operation...");

    AWSLambda client = getLambdaClient();
    DeleteEventSourceMappingRequest request = new DeleteEventSourceMappingRequest().withUUID(description.getUuid());

    DeleteEventSourceMappingResult result = client.deleteEventSourceMapping(request);
    updateTaskStatus("Finished Deleting of AWS Lambda Function Event Mapping Operation...");

    return result;
  }
}
