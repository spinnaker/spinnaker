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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.PutFunctionConcurrencyRequest;
import com.amazonaws.services.lambda.model.PutFunctionConcurrencyResult;
import com.amazonaws.services.lambda.model.PutProvisionedConcurrencyConfigRequest;
import com.amazonaws.services.lambda.model.PutProvisionedConcurrencyConfigResult;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.PutLambdaProvisionedConcurrencyDescription;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.PutLambdaReservedConcurrencyDescription;
import com.netflix.spinnaker.clouddriver.lambda.provider.view.LambdaFunctionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class PutConcurrencyAtomicOperationTest implements LambdaTestingDefaults {

  @Test
  void testProvisionedConcurrency() {
    PutLambdaProvisionedConcurrencyDescription provisionedConcurrencyDescription =
        new PutLambdaProvisionedConcurrencyDescription();
    provisionedConcurrencyDescription
        .setFunctionName(fName)
        .setQualifier(version)
        .setProvisionedConcurrentExecutions(2)
        .setRegion(region)
        .setAccount(account);
    ;

    PutLambdaProvisionedConcurrencyAtomicOperation putConcurrencyOperation =
        spy(new PutLambdaProvisionedConcurrencyAtomicOperation(provisionedConcurrencyDescription));
    doNothing().when(putConcurrencyOperation).updateTaskStatus(anyString());

    AWSLambda lambdaClient = mock(AWSLambda.class);
    LambdaFunctionProvider lambdaFunctionProvider = mock(LambdaFunctionProvider.class);

    ReflectionTestUtils.setField(
        putConcurrencyOperation, "lambdaFunctionProvider", lambdaFunctionProvider);

    LambdaFunction cachedFunction = getMockedFunctionDefintion();
    doReturn(lambdaClient).when(putConcurrencyOperation).getLambdaClient();
    doReturn(cachedFunction)
        .when(lambdaFunctionProvider)
        .getFunction(anyString(), anyString(), anyString());

    PutProvisionedConcurrencyConfigRequest testRequest =
        new PutProvisionedConcurrencyConfigRequest();
    testRequest.setFunctionName(fName);
    testRequest.setQualifier(version);
    testRequest.setProvisionedConcurrentExecutions(2);

    PutProvisionedConcurrencyConfigResult mockProvisionResult =
        new PutProvisionedConcurrencyConfigResult();
    mockProvisionResult.setAllocatedProvisionedConcurrentExecutions(2);
    mockProvisionResult.setAvailableProvisionedConcurrentExecutions(4);
    mockProvisionResult.setStatus("provisioned");
    doReturn(mockProvisionResult).when(lambdaClient).putProvisionedConcurrencyConfig(testRequest);

    PutProvisionedConcurrencyConfigResult output = putConcurrencyOperation.operate(null);
    verify(putConcurrencyOperation, atLeastOnce()).updateTaskStatus(anyString());
    assertThat(output).isEqualTo(mockProvisionResult);
  }

  @Test
  void testReservedConcurrency() {
    PutLambdaReservedConcurrencyDescription provisionedConcurrencyDescription =
        new PutLambdaReservedConcurrencyDescription();
    provisionedConcurrencyDescription
        .setFunctionName(fName)
        .setReservedConcurrentExecutions(2)
        .setRegion(region)
        .setAccount(account);
    ;

    PutLambdaReservedConcurrencyAtomicOperation putConcurrencyOperation =
        spy(new PutLambdaReservedConcurrencyAtomicOperation(provisionedConcurrencyDescription));
    doNothing().when(putConcurrencyOperation).updateTaskStatus(anyString());

    AWSLambda lambdaClient = mock(AWSLambda.class);
    LambdaFunctionProvider lambdaFunctionProvider = mock(LambdaFunctionProvider.class);

    ReflectionTestUtils.setField(
        putConcurrencyOperation, "lambdaFunctionProvider", lambdaFunctionProvider);

    LambdaFunction cachedFunction = getMockedFunctionDefintion();
    doReturn(lambdaClient).when(putConcurrencyOperation).getLambdaClient();
    doReturn(cachedFunction)
        .when(lambdaFunctionProvider)
        .getFunction(anyString(), anyString(), anyString());

    PutFunctionConcurrencyRequest testRequest = new PutFunctionConcurrencyRequest();
    testRequest.setReservedConcurrentExecutions(2);
    testRequest.setFunctionName(fName);

    PutFunctionConcurrencyResult mockProvisionResult = new PutFunctionConcurrencyResult();
    doReturn(mockProvisionResult).when(lambdaClient).putFunctionConcurrency(testRequest);

    PutFunctionConcurrencyResult output = putConcurrencyOperation.operate(null);
    verify(putConcurrencyOperation, atLeastOnce()).updateTaskStatus(anyString());
    assertThat(output).isEqualTo(mockProvisionResult);
  }
}
