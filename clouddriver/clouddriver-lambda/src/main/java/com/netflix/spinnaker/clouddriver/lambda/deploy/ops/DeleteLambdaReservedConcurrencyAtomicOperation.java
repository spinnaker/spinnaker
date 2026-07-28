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

import com.netflix.spinnaker.clouddriver.lambda.deploy.description.DeleteLambdaReservedConcurrencyDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionConcurrencyRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionConcurrencyResponse;

public class DeleteLambdaReservedConcurrencyAtomicOperation
    extends AbstractLambdaAtomicOperation<
        DeleteLambdaReservedConcurrencyDescription, DeleteFunctionConcurrencyResponse>
    implements AtomicOperation<DeleteFunctionConcurrencyResponse> {

  public DeleteLambdaReservedConcurrencyAtomicOperation(
      DeleteLambdaReservedConcurrencyDescription description) {
    super(description, "DELETE_LAMBDA_FUNCTION_RESERVED_CONCURRENCY");
  }

  @Override
  public DeleteFunctionConcurrencyResponse operate(List priorOutputs) {
    updateTaskStatus("Initializing Atomic Operation AWS Lambda for DeleteReservedConcurrency...");
    return deleteReservedFunctionConcurrency(description.getFunctionName());
  }

  private DeleteFunctionConcurrencyResponse deleteReservedFunctionConcurrency(String functionName) {
    LambdaClient client = getLambdaClient();
    DeleteFunctionConcurrencyRequest req =
        DeleteFunctionConcurrencyRequest.builder().functionName(functionName).build();

    DeleteFunctionConcurrencyResponse result = client.deleteFunctionConcurrency(req);
    updateTaskStatus("Finished Atomic Operation AWS Lambda for DeleteReservedConcurrency...");
    return result;
  }
}
