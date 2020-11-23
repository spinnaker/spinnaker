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
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.DeleteFunctionResult;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.DeleteLambdaFunctionDescription;
import com.netflix.spinnaker.clouddriver.lambda.provider.view.LambdaFunctionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class DeleteLambdaAtomicOperationTest implements LambdaTestingDefaults {
  @Test
  void testDeleteLambda() {
    DeleteLambdaFunctionDescription deleteDesc = new DeleteLambdaFunctionDescription();
    deleteDesc.setFunctionName(fName).setQualifier(version).setRegion(region).setAccount(account);

    DeleteLambdaAtomicOperation deleteOperation = spy(new DeleteLambdaAtomicOperation(deleteDesc));
    doNothing().when(deleteOperation).updateTaskStatus(anyString());

    AWSLambda lambdaClient = mock(AWSLambda.class);
    LambdaFunction cachedFunction = getMockedFunctionDefintion();

    LambdaFunctionProvider lambdaFunctionProvider = mock(LambdaFunctionProvider.class);
    ReflectionTestUtils.setField(deleteOperation, "lambdaFunctionProvider", lambdaFunctionProvider);

    doReturn(lambdaClient).when(deleteOperation).getLambdaClient();
    doReturn(cachedFunction)
        .when(lambdaFunctionProvider)
        .getFunction(anyString(), anyString(), anyString());

    DeleteFunctionRequest deleteRequest = new DeleteFunctionRequest();
    deleteRequest.withQualifier(version).withFunctionName(functionArn);
    DeleteFunctionResult mockDeleteResult = new DeleteFunctionResult();
    doReturn(mockDeleteResult).when(lambdaClient).deleteFunction(deleteRequest);

    DeleteFunctionResult output = deleteOperation.operate(null);
    verify(deleteOperation, atLeastOnce()).updateTaskStatus(anyString());
    assertThat(output).isEqualTo(mockDeleteResult);
  }
}
