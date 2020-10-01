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
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.LogType;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.InvokeLambdaFunctionDescription;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.InvokeLambdaFunctionOutputDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;

public class InvokeLambdaAtomicOperation
    extends AbstractLambdaAtomicOperation<
        InvokeLambdaFunctionDescription, InvokeLambdaFunctionOutputDescription>
    implements AtomicOperation<InvokeLambdaFunctionOutputDescription> {

  public InvokeLambdaAtomicOperation(InvokeLambdaFunctionDescription description) {
    super(description, "INVOKE_LAMBDA_FUNCTION");
  }

  @Override
  public InvokeLambdaFunctionOutputDescription operate(List priorOutputs) {
    updateTaskStatus("Initializing Invoking AWS Lambda Function Operation...");
    return invokeFunction(description.getFunctionName(), description.getPayload());
  }

  private InvokeLambdaFunctionOutputDescription invokeFunction(
      String functionName, String payload) {
    AWSLambda client = getLambdaClient();
    InvokeRequest req =
        new InvokeRequest()
            .withFunctionName(functionName)
            .withLogType(LogType.Tail)
            .withPayload(payload);

    String qualifierRegex = "|[a-zA-Z0-9$_-]+";
    if (description.getQualifier().matches(qualifierRegex)) {
      req.setQualifier(description.getQualifier());
    }

    InvokeResult result = client.invoke(req);
    String ans = byteBuffer2String(result.getPayload(), Charset.forName("UTF-8"));
    InvokeLambdaFunctionOutputDescription is = new InvokeLambdaFunctionOutputDescription();
    is.setInvokeResult(result);
    is.setResponseString(ans);
    updateTaskStatus("Finished Invoking of AWS Lambda Function Operation...");
    return is;
  }

  public static String byteBuffer2String(ByteBuffer buf, Charset charset) {
    if (buf == null) {
      return null;
    }
    byte[] bytes;
    if (buf.hasArray()) {
      bytes = buf.array();
    } else {
      buf.rewind();
      bytes = new byte[buf.remaining()];
    }
    return new String(bytes, charset);
  }
}
