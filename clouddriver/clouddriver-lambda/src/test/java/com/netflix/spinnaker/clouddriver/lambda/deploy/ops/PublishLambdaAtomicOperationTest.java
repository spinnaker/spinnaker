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
import com.amazonaws.services.lambda.model.PublishVersionRequest;
import com.amazonaws.services.lambda.model.PublishVersionResult;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.PublishLambdaFunctionVersionDescription;
import com.netflix.spinnaker.clouddriver.lambda.provider.view.LambdaFunctionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class PublishLambdaAtomicOperationTest implements LambdaTestingDefaults {

  @Test
  void testPublishLambda() {
    PublishLambdaFunctionVersionDescription publishDesc =
        new PublishLambdaFunctionVersionDescription();
    publishDesc
        .setFunctionName(fName)
        .setDescription(revisionDesc)
        .setRevisionId(revisionId)
        .setRegion(region)
        .setAccount(account);
    ;

    PublishLambdaAtomicOperation publishOperation =
        spy(new PublishLambdaAtomicOperation(publishDesc));
    doNothing().when(publishOperation).updateTaskStatus(anyString());

    AWSLambda lambdaClient = mock(AWSLambda.class);
    LambdaFunctionProvider lambdaFunctionProvider = mock(LambdaFunctionProvider.class);

    ReflectionTestUtils.setField(
        publishOperation, "lambdaFunctionProvider", lambdaFunctionProvider);

    LambdaFunction cachedFunction = getMockedFunctionDefintion();
    doReturn(lambdaClient).when(publishOperation).getLambdaClient();
    doReturn(cachedFunction)
        .when(lambdaFunctionProvider)
        .getFunction(anyString(), anyString(), anyString());

    PublishVersionRequest testRequest = new PublishVersionRequest();
    testRequest.setFunctionName(fName);
    testRequest.setRevisionId(revisionId);
    testRequest.setDescription(revisionDesc);

    PublishVersionResult mockPublishResult = new PublishVersionResult();
    doReturn(mockPublishResult).when(lambdaClient).publishVersion(testRequest);

    PublishVersionResult output = publishOperation.operate(null);
    verify(publishOperation, atLeastOnce()).updateTaskStatus(anyString());
    assertThat(output).isEqualTo(mockPublishResult);
  }
}
