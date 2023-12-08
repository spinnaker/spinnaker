/*
 * Copyright 2020 Netflix, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.InvokeLambdaFunctionDescription;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.InvokeLambdaFunctionOutputDescription;
import com.netflix.spinnaker.clouddriver.lambda.provider.view.LambdaFunctionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/*
* To test against clouddriver, you can verify results using the following example CURL. Note this
* is all "sample" data. The return type will be a callback that can be queried to know the status
* of the invoke operation. <code>curl -XPOST -k -H "X-SPINNAKER-USER: jason.mcintosh@armory.io" -H
 "Accept: application/json" -H "Content-Type: application/json"
 https://spin-clouddriver:7002/aws/ops/invokeLambdaFunction -d ' { "appName":"simple",
 "functionName": "simple-hello-world", "qualifier":"$LATEST", "region": "us-west-2",
 "credentials": "aws-internal-dev", "account": "aws-internal-dev", "timeout": 600000 }'</code>
*/
public class InvokeLambdaAtomicOperationTest implements LambdaTestingDefaults {
  InvokeLambdaAtomicOperation invokeOperation;
  InvokeLambdaFunctionDescription invokeDesc;

  @BeforeEach
  public void setup() {
    invokeDesc = new InvokeLambdaFunctionDescription();
    invokeDesc.setFunctionName(fName).setQualifier(version).setRegion(region).setAccount(account);
    invokeDesc.setPayload("example");
    invokeOperation = spy(new InvokeLambdaAtomicOperation(invokeDesc));

    LambdaFunctionProvider lambdaFunctionProvider = mock(LambdaFunctionProvider.class);
    ReflectionTestUtils.setField(invokeOperation, "lambdaFunctionProvider", lambdaFunctionProvider);
    LambdaFunction cachedFunction = getMockedFunctionDefintion();
    doReturn(cachedFunction)
        .when(lambdaFunctionProvider)
        .getFunction(anyString(), anyString(), anyString());
    doNothing().when(invokeOperation).updateTaskStatus(anyString());
  }

  @Test
  void testInvokeLambda() {

    AWSLambda lambdaClient = mock(AWSLambda.class);
    doReturn(lambdaClient).when(invokeOperation).getLambdaClient();

    ArgumentCaptor<InvokeRequest> captor = ArgumentCaptor.forClass(InvokeRequest.class);
    InvokeResult result = new InvokeResult();
    doReturn(result).when(lambdaClient).invoke(any(InvokeRequest.class));

    InvokeLambdaFunctionOutputDescription output = invokeOperation.operate(null);
    assertEquals(result, output.getInvokeResult());
    verify(lambdaClient).invoke(captor.capture());
    verify(invokeOperation, atLeastOnce()).updateTaskStatus(anyString());
    assertEquals(fName, captor.getValue().getFunctionName());
    assertNull(captor.getValue().getSdkRequestTimeout());
  }

  @Test
  void verifyTimeoutIsSet() {
    // Allows a base timeout for all operations of 100,000 then short it to 55 seconds for a
    // specific request per invoked request
    invokeDesc.setTimeout(55);

    AWSLambda lambdaClient = mock(AWSLambda.class);
    doReturn(lambdaClient).when(invokeOperation).getLambdaClient();

    ArgumentCaptor<InvokeRequest> invokeCaptor = ArgumentCaptor.forClass(InvokeRequest.class);
    doReturn(new InvokeResult()).when(lambdaClient).invoke(invokeCaptor.capture());
    invokeOperation.operate(null);
    assertEquals(55000, invokeCaptor.getValue().getSdkRequestTimeout().intValue());
  }
}
