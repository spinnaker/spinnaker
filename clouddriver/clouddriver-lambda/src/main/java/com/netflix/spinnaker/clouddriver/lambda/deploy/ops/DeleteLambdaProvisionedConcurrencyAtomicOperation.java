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

import com.netflix.spinnaker.clouddriver.lambda.deploy.description.DeleteLambdaProvisionedConcurrencyDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.DeleteProvisionedConcurrencyConfigRequest;
import software.amazon.awssdk.services.lambda.model.DeleteProvisionedConcurrencyConfigResponse;

public class DeleteLambdaProvisionedConcurrencyAtomicOperation
    extends AbstractLambdaAtomicOperation<
        DeleteLambdaProvisionedConcurrencyDescription, DeleteProvisionedConcurrencyConfigResponse>
    implements AtomicOperation<DeleteProvisionedConcurrencyConfigResponse> {

  public DeleteLambdaProvisionedConcurrencyAtomicOperation(
      DeleteLambdaProvisionedConcurrencyDescription description) {
    super(description, "DELETE_LAMBDA_FUNCTION_PROVISIONED_CONCURRENCY");
  }

  @Override
  public DeleteProvisionedConcurrencyConfigResponse operate(List priorOutputs) {
    updateTaskStatus(
        "Initializing Atomic Operation AWS Lambda for DeleteProvisionedConcurrency...");
    return deleteProvisionedFunctionConcurrency(
        description.getFunctionName(), description.getQualifier());
  }

  private DeleteProvisionedConcurrencyConfigResponse deleteProvisionedFunctionConcurrency(
      String functionName, String qualifier) {
    LambdaClient client = getLambdaClient();
    DeleteProvisionedConcurrencyConfigRequest req =
        DeleteProvisionedConcurrencyConfigRequest.builder()
            .functionName(functionName)
            .qualifier(qualifier)
            .build();

    DeleteProvisionedConcurrencyConfigResponse result =
        client.deleteProvisionedConcurrencyConfig(req);
    updateTaskStatus("Finished Atomic Operation AWS Lambda for DeleteProvisionedConcurrency...");
    return result;
  }
}
