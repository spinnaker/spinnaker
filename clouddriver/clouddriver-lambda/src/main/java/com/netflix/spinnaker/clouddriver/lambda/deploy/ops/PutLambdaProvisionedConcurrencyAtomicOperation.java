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

import com.netflix.spinnaker.clouddriver.lambda.deploy.description.PutLambdaProvisionedConcurrencyDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.PutProvisionedConcurrencyConfigRequest;
import software.amazon.awssdk.services.lambda.model.PutProvisionedConcurrencyConfigResponse;

public class PutLambdaProvisionedConcurrencyAtomicOperation
    extends AbstractLambdaAtomicOperation<
        PutLambdaProvisionedConcurrencyDescription, PutProvisionedConcurrencyConfigResponse>
    implements AtomicOperation<PutProvisionedConcurrencyConfigResponse> {

  public PutLambdaProvisionedConcurrencyAtomicOperation(
      PutLambdaProvisionedConcurrencyDescription description) {
    super(description, "PUT_LAMBDA_FUNCTION_PROVISIONED_CONCURRENCY");
  }

  @Override
  public PutProvisionedConcurrencyConfigResponse operate(List priorOutputs) {
    updateTaskStatus("Initializing Atomic Operation AWS Lambda for PutProvisionedConcurrency...");
    return putProvisionedFunctionConcurrency(
        description.getFunctionName(),
        description.getQualifier(),
        description.getProvisionedConcurrentExecutions());
  }

  private PutProvisionedConcurrencyConfigResponse putProvisionedFunctionConcurrency(
      String functionName, String qualifier, int provisionedConcurrentExecutions) {
    LambdaClient client = getLambdaClient();
    PutProvisionedConcurrencyConfigRequest req =
        PutProvisionedConcurrencyConfigRequest.builder()
            .functionName(functionName)
            .qualifier(qualifier)
            .provisionedConcurrentExecutions(provisionedConcurrentExecutions)
            .build();

    PutProvisionedConcurrencyConfigResponse result = client.putProvisionedConcurrencyConfig(req);
    updateTaskStatus("Finished Atomic Operation AWS Lambda for PutProvisionedConcurrency...");
    return result;
  }
}
