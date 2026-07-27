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

import com.netflix.spinnaker.clouddriver.lambda.deploy.description.PutLambdaReservedConcurrencyDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.PutFunctionConcurrencyRequest;
import software.amazon.awssdk.services.lambda.model.PutFunctionConcurrencyResponse;

public class PutLambdaReservedConcurrencyAtomicOperation
    extends AbstractLambdaAtomicOperation<
        PutLambdaReservedConcurrencyDescription, PutFunctionConcurrencyResponse>
    implements AtomicOperation<PutFunctionConcurrencyResponse> {

  public PutLambdaReservedConcurrencyAtomicOperation(
      PutLambdaReservedConcurrencyDescription description) {
    super(description, "PUT_LAMBDA_FUNCTION_RESERVED_CONCURRENCY");
  }

  @Override
  public PutFunctionConcurrencyResponse operate(List priorOutputs) {
    updateTaskStatus("Initializing Atomic Operation AWS Lambda for PutReservedConcurrency...");
    return putReservedFunctionConcurrency(
        description.getFunctionName(), description.getReservedConcurrentExecutions());
  }

  private PutFunctionConcurrencyResponse putReservedFunctionConcurrency(
      String functionName, int reservedConcurrentExecutions) {
    LambdaClient client = getLambdaClient();
    PutFunctionConcurrencyRequest req =
        PutFunctionConcurrencyRequest.builder()
            .functionName(functionName)
            .reservedConcurrentExecutions(reservedConcurrentExecutions)
            .build();

    PutFunctionConcurrencyResponse result = client.putFunctionConcurrency(req);
    updateTaskStatus("Finished Atomic Operation AWS Lambda for PutReservedConcurrency...");
    return result;
  }
}
