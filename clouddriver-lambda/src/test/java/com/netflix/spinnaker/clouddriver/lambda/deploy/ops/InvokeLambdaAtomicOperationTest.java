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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.InvokeLambdaFunctionDescription;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.InvokeLambdaFunctionOutputDescription;
import com.netflix.spinnaker.clouddriver.lambda.provider.view.LambdaFunctionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class InvokeLambdaAtomicOperationTest implements LambdaTestingDefaults {
  @Test
  void testInvokeLambda() {
    InvokeLambdaFunctionDescription invokeDesc = new InvokeLambdaFunctionDescription();
    invokeDesc.setFunctionName(fName).setQualifier(version).setRegion(region).setAccount(account);

    InvokeLambdaAtomicOperation invokeOperation = spy(new InvokeLambdaAtomicOperation(invokeDesc));
    doNothing().when(invokeOperation).updateTaskStatus(anyString());

    AWSLambda lambdaClient = mock(AWSLambda.class);
    LambdaFunction cachedFunction = getMockedFunctionDefintion();

    LambdaFunctionProvider lambdaFunctionProvider = mock(LambdaFunctionProvider.class);
    ReflectionTestUtils.setField(invokeOperation, "lambdaFunctionProvider", lambdaFunctionProvider);

    doReturn(lambdaClient).when(invokeOperation).getLambdaClient();
    doReturn(cachedFunction)
        .when(lambdaFunctionProvider)
        .getFunction(anyString(), anyString(), anyString());

    InvokeRequest invokeRequest = new InvokeRequest();
    invokeRequest.withQualifier(version).withFunctionName(functionArn);
    InvokeResult mockDeleteResult = new InvokeResult();
    doReturn(mockDeleteResult).when(lambdaClient).invoke(invokeRequest);

    InvokeLambdaFunctionOutputDescription output = invokeOperation.operate(null);
    verify(invokeOperation, atLeastOnce()).updateTaskStatus(anyString());
  }
}
