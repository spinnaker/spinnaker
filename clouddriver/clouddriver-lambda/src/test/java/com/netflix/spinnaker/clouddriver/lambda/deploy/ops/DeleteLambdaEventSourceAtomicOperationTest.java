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
import com.amazonaws.services.lambda.model.DeleteEventSourceMappingRequest;
import com.amazonaws.services.lambda.model.DeleteEventSourceMappingResult;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.UpsertLambdaFunctionEventMappingDescription;
import com.netflix.spinnaker.clouddriver.lambda.provider.view.LambdaFunctionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class DeleteLambdaEventSourceAtomicOperationTest implements LambdaTestingDefaults {
  @Test
  void testUpdateLambdaEventMapping() {
    UpsertLambdaFunctionEventMappingDescription eventMappingDesc =
        new UpsertLambdaFunctionEventMappingDescription();
    eventMappingDesc
        .setFunctionName(fName)
        .setBatchsize(1)
        .setEnabled(true)
        .setEventSourceArn(eventArn)
        .setUuid(eventUuid)
        .setRegion(region)
        .setAccount(account);
    ;
    DeleteLambdaEventSourceAtomicOperation deleteEventSourceOperation =
        spy(new DeleteLambdaEventSourceAtomicOperation(eventMappingDesc));
    doNothing().when(deleteEventSourceOperation).updateTaskStatus(anyString());

    AWSLambda lambdaClient = mock(AWSLambda.class);
    LambdaFunctionProvider lambdaFunctionProvider = mock(LambdaFunctionProvider.class);

    ReflectionTestUtils.setField(
        deleteEventSourceOperation, "lambdaFunctionProvider", lambdaFunctionProvider);

    LambdaFunction cachedFunction = getMockedFunctionDefintion();
    doReturn(lambdaClient).when(deleteEventSourceOperation).getLambdaClient();
    doReturn(cachedFunction)
        .when(lambdaFunctionProvider)
        .getFunction(anyString(), anyString(), anyString());

    DeleteEventSourceMappingRequest testRequest = new DeleteEventSourceMappingRequest();
    testRequest.setUUID(eventUuid);

    DeleteEventSourceMappingResult mockDeleteEventResult = new DeleteEventSourceMappingResult();
    doReturn(mockDeleteEventResult).when(lambdaClient).deleteEventSourceMapping(testRequest);

    Object output = deleteEventSourceOperation.operate(null);
    verify(deleteEventSourceOperation, atLeastOnce()).updateTaskStatus(anyString());
    assertThat(output).isEqualTo(mockDeleteEventResult);
  }
}
