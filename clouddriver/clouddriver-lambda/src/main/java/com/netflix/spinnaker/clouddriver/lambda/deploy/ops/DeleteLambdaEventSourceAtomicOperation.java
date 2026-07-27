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
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.DeleteEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.DeleteEventSourceMappingResponse;

public class DeleteLambdaEventSourceAtomicOperation
    extends AbstractLambdaAtomicOperation<UpsertLambdaFunctionEventMappingDescription, Object>
    implements AtomicOperation<Object> {

  public DeleteLambdaEventSourceAtomicOperation(
      UpsertLambdaFunctionEventMappingDescription description) {
    super(description, "DELETE_LAMBDA_FUNCTION_EVENT_MAPPING");
  }

  @Override
  public Object operate(List priorOutputs) {
    LambdaFunction lambdaFunction =
        (LambdaFunction)
            lambdaFunctionProvider.getFunction(
                description.getAccount(), description.getRegion(), description.getFunctionName());

    List<Map<String, Object>> eventSourceMappingConfigurations =
        lambdaFunction.getEventSourceMappings();

    if (eventSourceMappingConfigurations != null) {
      for (Map<String, Object> eventSourceMappingConfiguration : eventSourceMappingConfigurations) {
        if (description
            .getEventSourceArn()
            .equalsIgnoreCase((String) eventSourceMappingConfiguration.get("eventSourceArn"))) {
          description.setUuid((String) eventSourceMappingConfiguration.get("uuid"));
          return deleteEventSourceMappingResult();
        }
      }
    }

    return null;
  }

  private DeleteEventSourceMappingResponse deleteEventSourceMappingResult() {
    updateTaskStatus("Initializing Deleting of AWS Lambda Function Event Mapping Operation...");

    LambdaClient client = getLambdaClient();
    DeleteEventSourceMappingRequest request =
        DeleteEventSourceMappingRequest.builder().uuid(description.getUuid()).build();

    DeleteEventSourceMappingResponse result = client.deleteEventSourceMapping(request);
    updateTaskStatus("Finished Deleting of AWS Lambda Function Event Mapping Operation...");

    return result;
  }
}
