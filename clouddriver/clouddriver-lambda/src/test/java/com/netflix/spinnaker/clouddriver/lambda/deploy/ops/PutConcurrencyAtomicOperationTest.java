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

import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.PutLambdaProvisionedConcurrencyDescription;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.PutLambdaReservedConcurrencyDescription;
import com.netflix.spinnaker.clouddriver.lambda.provider.view.LambdaFunctionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.PutFunctionConcurrencyRequest;
import software.amazon.awssdk.services.lambda.model.PutFunctionConcurrencyResponse;
import software.amazon.awssdk.services.lambda.model.PutProvisionedConcurrencyConfigRequest;
import software.amazon.awssdk.services.lambda.model.PutProvisionedConcurrencyConfigResponse;

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

    LambdaClient lambdaClient = mock(LambdaClient.class);
    LambdaFunctionProvider lambdaFunctionProvider = mock(LambdaFunctionProvider.class);

    ReflectionTestUtils.setField(
        putConcurrencyOperation, "lambdaFunctionProvider", lambdaFunctionProvider);

    LambdaFunction cachedFunction = getMockedFunctionDefintion();
    doReturn(lambdaClient).when(putConcurrencyOperation).getLambdaClient();
    doReturn(cachedFunction)
        .when(lambdaFunctionProvider)
        .getFunction(anyString(), anyString(), anyString());

    PutProvisionedConcurrencyConfigRequest testRequest =
        PutProvisionedConcurrencyConfigRequest.builder()
            .functionName(fName)
            .qualifier(version)
            .provisionedConcurrentExecutions(2)
            .build();

    PutProvisionedConcurrencyConfigResponse mockProvisionResult =
        PutProvisionedConcurrencyConfigResponse.builder()
            .allocatedProvisionedConcurrentExecutions(2)
            .availableProvisionedConcurrentExecutions(4)
            .status("provisioned")
            .build();
    doReturn(mockProvisionResult).when(lambdaClient).putProvisionedConcurrencyConfig(testRequest);

    PutProvisionedConcurrencyConfigResponse output = putConcurrencyOperation.operate(null);
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

    LambdaClient lambdaClient = mock(LambdaClient.class);
    LambdaFunctionProvider lambdaFunctionProvider = mock(LambdaFunctionProvider.class);

    ReflectionTestUtils.setField(
        putConcurrencyOperation, "lambdaFunctionProvider", lambdaFunctionProvider);

    LambdaFunction cachedFunction = getMockedFunctionDefintion();
    doReturn(lambdaClient).when(putConcurrencyOperation).getLambdaClient();
    doReturn(cachedFunction)
        .when(lambdaFunctionProvider)
        .getFunction(anyString(), anyString(), anyString());

    PutFunctionConcurrencyRequest testRequest =
        PutFunctionConcurrencyRequest.builder()
            .reservedConcurrentExecutions(2)
            .functionName(fName)
            .build();

    PutFunctionConcurrencyResponse mockProvisionResult =
        PutFunctionConcurrencyResponse.builder().build();
    doReturn(mockProvisionResult).when(lambdaClient).putFunctionConcurrency(testRequest);

    PutFunctionConcurrencyResponse output = putConcurrencyOperation.operate(null);
    verify(putConcurrencyOperation, atLeastOnce()).updateTaskStatus(anyString());
    assertThat(output).isEqualTo(mockProvisionResult);
  }
}
