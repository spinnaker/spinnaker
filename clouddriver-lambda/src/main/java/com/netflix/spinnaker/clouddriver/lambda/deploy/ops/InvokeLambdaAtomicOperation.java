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
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;;
import com.amazonaws.services.lambda.model.LogType;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.InvokeLambdaFunctionDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;

import java.util.List;

public class InvokeLambdaAtomicOperation
  extends AbstractLambdaAtomicOperation<InvokeLambdaFunctionDescription, InvokeResult>
  implements AtomicOperation<InvokeResult> {

  public InvokeLambdaAtomicOperation(InvokeLambdaFunctionDescription description) {
    super(description, "INVOKE_LAMBDA_FUNCTION");
  }

  @Override
  public InvokeResult operate(List priorOutputs) {
    updateTaskStatus("Initializing Invoking AWS Lambda Function Operation...");
    return invokeFunction(
      description.getFunctionName(),
      description.getPayload()
    );
  }

  private InvokeResult invokeFunction(String functionName, String payload) {
    AWSLambda client = getLambdaClient();
    InvokeRequest req = new InvokeRequest()
      .withFunctionName(functionName)
      .withLogType(LogType.Tail)
      .withPayload(payload);

    String qualifierRegex = "|[a-zA-Z0-9$_-]+";
    if (description.getQualifier().matches(qualifierRegex)) {
      req.setQualifier(description.getQualifier());
    }

    InvokeResult result = client.invoke(req);
    updateTaskStatus("Finished Invoking of AWS Lambda Function Operation...");
    return result;
  }
}
