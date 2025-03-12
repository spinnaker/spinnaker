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
import com.amazonaws.services.lambda.model.DeleteFunctionConcurrencyRequest;
import com.amazonaws.services.lambda.model.DeleteFunctionConcurrencyResult;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.DeleteLambdaReservedConcurrencyDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;

public class DeleteLambdaReservedConcurrencyAtomicOperation
    extends AbstractLambdaAtomicOperation<
        DeleteLambdaReservedConcurrencyDescription, DeleteFunctionConcurrencyResult>
    implements AtomicOperation<DeleteFunctionConcurrencyResult> {

  public DeleteLambdaReservedConcurrencyAtomicOperation(
      DeleteLambdaReservedConcurrencyDescription description) {
    super(description, "DELETE_LAMBDA_FUNCTION_RESERVED_CONCURRENCY");
  }

  @Override
  public DeleteFunctionConcurrencyResult operate(List priorOutputs) {
    updateTaskStatus("Initializing Atomic Operation AWS Lambda for DeleteReservedConcurrency...");
    return deleteReservedFunctionConcurrency(description.getFunctionName());
  }

  private DeleteFunctionConcurrencyResult deleteReservedFunctionConcurrency(String functionName) {
    AWSLambda client = getLambdaClient();
    DeleteFunctionConcurrencyRequest req =
        new DeleteFunctionConcurrencyRequest().withFunctionName(functionName);

    DeleteFunctionConcurrencyResult result = client.deleteFunctionConcurrency(req);
    updateTaskStatus("Finished Atomic Operation AWS Lambda for DeleteReservedConcurrency...");
    return result;
  }
}
