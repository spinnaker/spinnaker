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
import com.amazonaws.services.lambda.model.PutFunctionConcurrencyRequest;
import com.amazonaws.services.lambda.model.PutFunctionConcurrencyResult;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.PutLambdaReservedConcurrencyDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;

public class PutLambdaReservedConcurrencyAtomicOperation
    extends AbstractLambdaAtomicOperation<
        PutLambdaReservedConcurrencyDescription, PutFunctionConcurrencyResult>
    implements AtomicOperation<PutFunctionConcurrencyResult> {

  public PutLambdaReservedConcurrencyAtomicOperation(
      PutLambdaReservedConcurrencyDescription description) {
    super(description, "PUT_LAMBDA_FUNCTION_RESERVED_CONCURRENCY");
  }

  @Override
  public PutFunctionConcurrencyResult operate(List priorOutputs) {
    updateTaskStatus("Initializing Atomic Operation AWS Lambda for PutReservedConcurrency...");
    return putReservedFunctionConcurrency(
        description.getFunctionName(), description.getReservedConcurrentExecutions());
  }

  private PutFunctionConcurrencyResult putReservedFunctionConcurrency(
      String functionName, int reservedConcurrentExecutions) {
    AWSLambda client = getLambdaClient();
    PutFunctionConcurrencyRequest req =
        new PutFunctionConcurrencyRequest()
            .withFunctionName(functionName)
            .withReservedConcurrentExecutions(reservedConcurrentExecutions);

    PutFunctionConcurrencyResult result = client.putFunctionConcurrency(req);
    updateTaskStatus("Finished Atomic Operation AWS Lambda for PutReservedConcurrency...");
    return result;
  }
}
