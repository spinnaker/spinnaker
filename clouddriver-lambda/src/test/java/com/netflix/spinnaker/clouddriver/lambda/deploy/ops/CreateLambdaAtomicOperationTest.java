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
import static org.mockito.Mockito.*;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.DeadLetterConfig;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.CreateLambdaFunctionDescription;
import org.junit.jupiter.api.Test;

public class CreateLambdaAtomicOperationTest {
  @Test
  void testPublishLambda() {
    // given
    CreateLambdaFunctionDescription b =
        new CreateLambdaFunctionDescription()
            .setS3bucket("s3://bucket")
            .setS3key("key/key/path")
            .setAppName("appName")
            .setFunctionName("funcName")
            .setDeadLetterConfig(new DeadLetterConfig().withTargetArn(""));
    CreateLambdaAtomicOperation clao = spy(new CreateLambdaAtomicOperation(b));
    doNothing().when(clao).updateTaskStatus(anyString());
    AWSLambda lambdaClient = mock(AWSLambda.class);
    doReturn(lambdaClient).when(clao).getLambdaClient();
    CreateFunctionRequest createRequest =
        new CreateFunctionRequest()
            .withFunctionName("appName-funcName")
            .withCode(new FunctionCode().withS3Bucket("s3://bucket").withS3Key("key/key/path"));
    CreateFunctionResult createLambdaResult =
        new CreateFunctionResult()
            .withFunctionName("appName-funcName")
            .withCodeSha256("abc123def456");
    doReturn(createLambdaResult).when(lambdaClient).createFunction(createRequest);
    // when
    CreateFunctionResult output = clao.operate(null);
    // then
    verify(clao, atLeastOnce()).updateTaskStatus(anyString());
    assertThat(output).isEqualTo(createLambdaResult);
  }
}
