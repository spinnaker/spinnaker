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
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.UpsertLambdaFunctionEventMappingDescription;
import com.netflix.spinnaker.clouddriver.lambda.provider.view.LambdaFunctionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.DeleteEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.DeleteEventSourceMappingResponse;

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

    LambdaClient lambdaClient = mock(LambdaClient.class);
    LambdaFunctionProvider lambdaFunctionProvider = mock(LambdaFunctionProvider.class);

    ReflectionTestUtils.setField(
        deleteEventSourceOperation, "lambdaFunctionProvider", lambdaFunctionProvider);

    LambdaFunction cachedFunction = getMockedFunctionDefintion();
    doReturn(lambdaClient).when(deleteEventSourceOperation).getLambdaClient();
    doReturn(cachedFunction)
        .when(lambdaFunctionProvider)
        .getFunction(anyString(), anyString(), anyString());

    DeleteEventSourceMappingRequest testRequest =
        DeleteEventSourceMappingRequest.builder().uuid(eventUuid).build();

    DeleteEventSourceMappingResponse mockDeleteEventResult =
        DeleteEventSourceMappingResponse.builder().build();
    doReturn(mockDeleteEventResult).when(lambdaClient).deleteEventSourceMapping(testRequest);

    Object output = deleteEventSourceOperation.operate(null);
    verify(deleteEventSourceOperation, atLeastOnce()).updateTaskStatus(anyString());
    assertThat(output).isEqualTo(mockDeleteEventResult);
  }
}
