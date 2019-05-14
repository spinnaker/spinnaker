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
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.CreateLambdaFunctionDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class CreateLambdaAtomicOperation
    extends AbstractLambdaAtomicOperation<CreateLambdaFunctionDescription, CreateFunctionResult>
    implements AtomicOperation<CreateFunctionResult> {

  public CreateLambdaAtomicOperation(CreateLambdaFunctionDescription description) {
    super(description, "CREATE_LAMBDA_FUNCTION");
  }

  @Override
  public CreateFunctionResult operate(List priorOutputs) {
    updateTaskStatus("Initializing Creation of AWS Lambda Function Operation...");
    return createFunction();
  }

  private CreateFunctionResult createFunction() {
    FunctionCode code =
        new FunctionCode()
            .withS3Bucket(description.getProperty("s3bucket").toString())
            .withS3Key(description.getProperty("s3key").toString());

    Map<String, String> objTag = new HashMap<>();
    for (Map<String, String> tags : description.getTags()) {
      for (Entry<String, String> entry : tags.entrySet()) {
        objTag.put(entry.getKey(), entry.getValue());
      }
    }

    AWSLambda client = getLambdaClient();

    CreateFunctionRequest request = new CreateFunctionRequest();
    request.setFunctionName(description.getFunctionName());
    request.setDescription(description.getDescription());
    request.setHandler(description.getHandler());
    request.setMemorySize(description.getMemory());
    request.setPublish(description.getPublish());
    request.setRole(description.getRole());
    request.setRuntime(description.getRuntime());
    request.setTimeout(description.getTimeout());

    request.setCode(code);
    request.setTags(objTag);

    CreateFunctionResult result = client.createFunction(request);
    updateTaskStatus("Finished Creation of AWS Lambda Function Operation...");

    return result;
  }
}
