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
import com.amazonaws.services.lambda.model.UpdateFunctionCodeRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeResult;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.UpdateLambdaFunctionCodeDescription;
import com.netflix.spinnaker.clouddriver.lambda.provider.view.LambdaFunctionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class UpdateLambdaAtomicOperationTest implements LambdaTestingDefaults {

  @Test
  void testUpdateLambdaCode() {
    UpdateLambdaFunctionCodeDescription updateCodeDesc = new UpdateLambdaFunctionCodeDescription();
    updateCodeDesc.setFunctionName(fName).setRegion(region).setAccount(account);

    UpdateLambdaCodeAtomicOperation updateCodeOperation =
        spy(new UpdateLambdaCodeAtomicOperation(updateCodeDesc));
    doNothing().when(updateCodeOperation).updateTaskStatus(anyString());

    AWSLambda lambdaClient = mock(AWSLambda.class);
    LambdaFunction cachedFunction = getMockedFunctionDefintion();

    LambdaFunctionProvider lambdaFunctionProvider = mock(LambdaFunctionProvider.class);
    ReflectionTestUtils.setField(
        updateCodeOperation, "lambdaFunctionProvider", lambdaFunctionProvider);

    doReturn(lambdaClient).when(updateCodeOperation).getLambdaClient();
    doReturn(cachedFunction)
        .when(lambdaFunctionProvider)
        .getFunction(anyString(), anyString(), anyString());

    UpdateFunctionCodeRequest updateCodeRequest = new UpdateFunctionCodeRequest();
    updateCodeRequest.withFunctionName(functionArn);
    UpdateFunctionCodeResult mockCodeResult = new UpdateFunctionCodeResult();
    doReturn(mockCodeResult).when(lambdaClient).updateFunctionCode(updateCodeRequest);

    UpdateFunctionCodeResult output = updateCodeOperation.operate(null);
    verify(updateCodeOperation, atLeastOnce()).updateTaskStatus(anyString());
    assertThat(output).isEqualTo(mockCodeResult);
  }

  @Test
  void testUpdateLambdaConfig() {
    // TODO
  }
}
