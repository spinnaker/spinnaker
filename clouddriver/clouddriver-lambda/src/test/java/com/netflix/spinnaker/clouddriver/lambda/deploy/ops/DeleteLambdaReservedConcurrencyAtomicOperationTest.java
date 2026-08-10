/*
 * Copyright 2022 Netflix, Inc.
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
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.DeleteLambdaReservedConcurrencyDescription;
import com.netflix.spinnaker.clouddriver.lambda.provider.view.LambdaFunctionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionConcurrencyRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionConcurrencyResponse;

public class DeleteLambdaReservedConcurrencyAtomicOperationTest implements LambdaTestingDefaults {
  @Test
  void testDeleteReservedConcurrency() {
    DeleteLambdaReservedConcurrencyDescription deleteDesc =
        new DeleteLambdaReservedConcurrencyDescription();
    deleteDesc.setFunctionName(fName);

    DeleteLambdaReservedConcurrencyAtomicOperation deleteOperation =
        spy(new DeleteLambdaReservedConcurrencyAtomicOperation(deleteDesc));
    doNothing().when(deleteOperation).updateTaskStatus(anyString());

    LambdaClient lambdaClient = mock(LambdaClient.class);
    LambdaFunction cachedFunction = getMockedFunctionDefintion();

    LambdaFunctionProvider lambdaFunctionProvider = mock(LambdaFunctionProvider.class);
    ReflectionTestUtils.setField(deleteOperation, "lambdaFunctionProvider", lambdaFunctionProvider);

    doReturn(lambdaClient).when(deleteOperation).getLambdaClient();
    doReturn(cachedFunction)
        .when(lambdaFunctionProvider)
        .getFunction(anyString(), anyString(), anyString());

    DeleteFunctionConcurrencyRequest deleteRequest =
        DeleteFunctionConcurrencyRequest.builder().functionName(fName).build();
    DeleteFunctionConcurrencyResponse mockDeleteResult =
        DeleteFunctionConcurrencyResponse.builder().build();
    doReturn(mockDeleteResult).when(lambdaClient).deleteFunctionConcurrency(deleteRequest);

    DeleteFunctionConcurrencyResponse output = deleteOperation.operate(null);
    verify(deleteOperation, atLeastOnce()).updateTaskStatus(anyString());
    assertThat(output).isEqualTo(mockDeleteResult);
  }
}
