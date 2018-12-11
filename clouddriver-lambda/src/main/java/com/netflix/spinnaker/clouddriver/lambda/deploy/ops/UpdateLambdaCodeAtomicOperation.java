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
import com.amazonaws.services.lambda.model.UpdateFunctionCodeRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeResult;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.UpdateLambdaFunctionCodeDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;

import java.util.List;

public class UpdateLambdaCodeAtomicOperation
  extends AbstractLambdaAtomicOperation<UpdateLambdaFunctionCodeDescription, UpdateFunctionCodeResult>
  implements AtomicOperation<UpdateFunctionCodeResult> {

  public UpdateLambdaCodeAtomicOperation(UpdateLambdaFunctionCodeDescription description) {
    super(description, "UPDATE_LAMBDA_FUNCTION_CODE");
  }

  @Override
  public UpdateFunctionCodeResult operate(List priorOutputs) {
    updateTaskStatus("Initializing Updating of AWS Lambda Function Code Operation...");
    return updateFunctionConfigurationResult();
  }

  private UpdateFunctionCodeResult updateFunctionConfigurationResult (){
    LambdaFunction lambdaFunction = (LambdaFunction) lambdaFunctionProvider.getFunction(
      description.getAccount(), description.getRegion(), description.getFunctionName()
    );

    AWSLambda client = getLambdaClient();

    UpdateFunctionCodeRequest request = new UpdateFunctionCodeRequest()
      .withFunctionName(lambdaFunction.getFunctionArn())
      .withPublish(description.getPublish())
      .withS3Bucket(description.getS3Bucket())
      .withS3Key(description.getS3Key());

    UpdateFunctionCodeResult result = client.updateFunctionCode(request);
    updateTaskStatus("Finished Updating of AWS Lambda Function Code Operation...");

    return result;
  }
}
