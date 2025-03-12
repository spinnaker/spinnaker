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
import com.amazonaws.services.lambda.model.DeleteProvisionedConcurrencyConfigRequest;
import com.amazonaws.services.lambda.model.DeleteProvisionedConcurrencyConfigResult;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.DeleteLambdaProvisionedConcurrencyDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;

public class DeleteLambdaProvisionedConcurrencyAtomicOperation
    extends AbstractLambdaAtomicOperation<
        DeleteLambdaProvisionedConcurrencyDescription, DeleteProvisionedConcurrencyConfigResult>
    implements AtomicOperation<DeleteProvisionedConcurrencyConfigResult> {

  public DeleteLambdaProvisionedConcurrencyAtomicOperation(
      DeleteLambdaProvisionedConcurrencyDescription description) {
    super(description, "DELETE_LAMBDA_FUNCTION_PROVISIONED_CONCURRENCY");
  }

  @Override
  public DeleteProvisionedConcurrencyConfigResult operate(List priorOutputs) {
    updateTaskStatus(
        "Initializing Atomic Operation AWS Lambda for DeleteProvisionedConcurrency...");
    return deleteProvisionedFunctionConcurrency(
        description.getFunctionName(), description.getQualifier());
  }

  private DeleteProvisionedConcurrencyConfigResult deleteProvisionedFunctionConcurrency(
      String functionName, String qualifier) {
    AWSLambda client = getLambdaClient();
    DeleteProvisionedConcurrencyConfigRequest req =
        new DeleteProvisionedConcurrencyConfigRequest()
            .withFunctionName(functionName)
            .withQualifier(qualifier);

    DeleteProvisionedConcurrencyConfigResult result =
        client.deleteProvisionedConcurrencyConfig(req);
    updateTaskStatus("Finished Atomic Operation AWS Lambda for DeleteProvisionedConcurrency...");
    return result;
  }
}
