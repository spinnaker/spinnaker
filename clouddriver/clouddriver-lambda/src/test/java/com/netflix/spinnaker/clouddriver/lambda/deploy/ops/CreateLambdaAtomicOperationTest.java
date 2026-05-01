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
import com.netflix.spinnaker.clouddriver.lambda.names.LambdaTagNamer;
import com.netflix.spinnaker.config.LambdaConfiguration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateLambdaAtomicOperationTest {

  LambdaConfiguration config;

  @BeforeEach
  void setUp() {
    config = new LambdaConfiguration();
  }

  @Test
  void testPublishLambda() {
    // given
    CreateLambdaFunctionDescription b =
        new CreateLambdaFunctionDescription()
            .setS3bucket("s3://bucket")
            .setS3key("key/key/path")
            .setFunctionName("funcName")
            .setDeadLetterConfig(new DeadLetterConfig().withTargetArn(""));
    b.setAppName("appName");
    config.setSetMonikerTags(false);
    CreateLambdaAtomicOperation clao = spy(new CreateLambdaAtomicOperation(b, config));
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

  @Test
  void testDisableAutoAppPrefix() {
    // given
    CreateLambdaFunctionDescription description =
        new CreateLambdaFunctionDescription()
            .setS3bucket("s3://bucket")
            .setS3key("key/path")
            .setFunctionName("function-stack-detail-v001")
            .setTags(new HashMap<>()) // Initialize empty tags map
            .setDeadLetterConfig(new DeadLetterConfig().withTargetArn(""));
    description.setAppName("myapp");
    config.setSetMonikerTags(true);
    config.setPrefixApplicationNameToFunction(false);
    CreateLambdaAtomicOperation operation =
        spy(new CreateLambdaAtomicOperation(description, config));
    doNothing().when(operation).updateTaskStatus(anyString());
    AWSLambda lambdaClient = mock(AWSLambda.class);
    doReturn(lambdaClient).when(operation).getLambdaClient();

    CreateFunctionResult result =
        new CreateFunctionResult()
            .withFunctionName("function-stack-detail-v001")
            .withCodeSha256("sha256");
    doReturn(result).when(lambdaClient).createFunction(any(CreateFunctionRequest.class));

    // when
    operation.operate(null);

    // then
    ArgumentCaptor<CreateFunctionRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateFunctionRequest.class);
    verify(lambdaClient).createFunction(requestCaptor.capture());

    Map<String, String> tags = requestCaptor.getValue().getTags();
    assertThat(requestCaptor.getValue().getFunctionName()).isEqualTo("function-stack-detail-v001");
    assertThat(tags)
        .containsEntry(LambdaTagNamer.APPLICATION, "myapp")
        .containsEntry(LambdaTagNamer.CLUSTER, "function-stack-detail")
        .containsEntry(LambdaTagNamer.STACK, "stack")
        .containsEntry(LambdaTagNamer.DETAIL, "detail")
        .containsEntry(LambdaTagNamer.SEQUENCE, "1");
  }

  @Test
  void testAutoApplyTagsWhenEnabled() {
    // given
    CreateLambdaFunctionDescription description =
        new CreateLambdaFunctionDescription()
            .setS3bucket("s3://bucket")
            .setS3key("key/path")
            .setFunctionName("myapp-stack-detail-v001")
            .setTags(new HashMap<>()) // Initialize empty tags map
            .setDeadLetterConfig(new DeadLetterConfig().withTargetArn(""));
    description.setAppName("myapp");

    CreateLambdaAtomicOperation operation =
        spy(new CreateLambdaAtomicOperation(description, config));
    doNothing().when(operation).updateTaskStatus(anyString());
    AWSLambda lambdaClient = mock(AWSLambda.class);
    doReturn(lambdaClient).when(operation).getLambdaClient();

    CreateFunctionResult result =
        new CreateFunctionResult()
            .withFunctionName("myapp-stack-detail-v001")
            .withCodeSha256("sha256");
    doReturn(result).when(lambdaClient).createFunction(any(CreateFunctionRequest.class));

    // when
    operation.operate(null);

    // then
    ArgumentCaptor<CreateFunctionRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateFunctionRequest.class);
    verify(lambdaClient).createFunction(requestCaptor.capture());

    Map<String, String> tags = requestCaptor.getValue().getTags();
    assertThat(tags)
        .containsEntry(LambdaTagNamer.APPLICATION, "myapp")
        .containsEntry(LambdaTagNamer.CLUSTER, "myapp-stack-detail")
        .containsEntry(LambdaTagNamer.STACK, "stack")
        .containsEntry(LambdaTagNamer.DETAIL, "detail")
        .containsEntry(LambdaTagNamer.SEQUENCE, "1");
  }

  @Test
  void testNoAutoApplyTagsWhenDisabled() {
    // given
    CreateLambdaFunctionDescription description =
        new CreateLambdaFunctionDescription()
            .setS3bucket("s3://bucket")
            .setS3key("key/path")
            .setFunctionName("stack-detail-v001")
            .setDeadLetterConfig(new DeadLetterConfig().withTargetArn(""));
    description.setAppName("myapp");

    config.setSetMonikerTags(false);
    CreateLambdaAtomicOperation operation =
        spy(new CreateLambdaAtomicOperation(description, config));
    doNothing().when(operation).updateTaskStatus(anyString());
    AWSLambda lambdaClient = mock(AWSLambda.class);
    doReturn(lambdaClient).when(operation).getLambdaClient();

    CreateFunctionResult result =
        new CreateFunctionResult().withFunctionName("myapp-stack-detail-v001");
    doReturn(result).when(lambdaClient).createFunction(any(CreateFunctionRequest.class));

    // when
    operation.operate(null);

    // then
    ArgumentCaptor<CreateFunctionRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateFunctionRequest.class);
    verify(lambdaClient).createFunction(requestCaptor.capture());

    Map<String, String> tags = requestCaptor.getValue().getTags();
    assertThat(tags).isEmpty();
  }

  @Test
  void testNoAutoPrefixAppName() {
    // given
    Map<String, String> existingTags = new HashMap<>();
    existingTags.put("Environment", "production");
    existingTags.put("Team", "platform");

    CreateLambdaFunctionDescription description =
        new CreateLambdaFunctionDescription()
            .setS3bucket("s3://bucket")
            .setS3key("key/path")
            .setFunctionName("myapp-stack-v001")
            .setTags(existingTags)
            .setDeadLetterConfig(new DeadLetterConfig().withTargetArn(""));
    description.setAppName("myapp");

    config.setSetMonikerTags(true);
    CreateLambdaAtomicOperation operation =
        spy(new CreateLambdaAtomicOperation(description, config));
    doNothing().when(operation).updateTaskStatus(anyString());
    AWSLambda lambdaClient = mock(AWSLambda.class);
    doReturn(lambdaClient).when(operation).getLambdaClient();

    CreateFunctionResult result = new CreateFunctionResult().withFunctionName("myapp-stack-v001");
    doReturn(result).when(lambdaClient).createFunction(any(CreateFunctionRequest.class));

    // when
    operation.operate(null);

    // then
    ArgumentCaptor<CreateFunctionRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateFunctionRequest.class);
    verify(lambdaClient).createFunction(requestCaptor.capture());

    Map<String, String> tags = requestCaptor.getValue().getTags();
    assertThat(tags)
        .containsEntry("Environment", "production")
        .containsEntry("Team", "platform")
        .containsEntry(LambdaTagNamer.APPLICATION, "myapp")
        .containsEntry(LambdaTagNamer.CLUSTER, "myapp-stack")
        .containsEntry(LambdaTagNamer.STACK, "stack");
  }

  @Test
  void testAutoApplyTagsPreservesExistingTags() {
    // given
    Map<String, String> existingTags = new HashMap<>();
    existingTags.put("Environment", "production");
    existingTags.put("Team", "platform");

    CreateLambdaFunctionDescription description =
        new CreateLambdaFunctionDescription()
            .setS3bucket("s3://bucket")
            .setS3key("key/path")
            .setFunctionName("myapp-stack-v001")
            .setTags(existingTags)
            .setDeadLetterConfig(new DeadLetterConfig().withTargetArn(""));
    description.setAppName("myapp");

    config.setSetMonikerTags(true);
    CreateLambdaAtomicOperation operation =
        spy(new CreateLambdaAtomicOperation(description, config));
    doNothing().when(operation).updateTaskStatus(anyString());
    AWSLambda lambdaClient = mock(AWSLambda.class);
    doReturn(lambdaClient).when(operation).getLambdaClient();

    CreateFunctionResult result = new CreateFunctionResult().withFunctionName("myapp-stack-v001");
    doReturn(result).when(lambdaClient).createFunction(any(CreateFunctionRequest.class));

    // when
    operation.operate(null);

    // then
    ArgumentCaptor<CreateFunctionRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateFunctionRequest.class);
    verify(lambdaClient).createFunction(requestCaptor.capture());

    Map<String, String> tags = requestCaptor.getValue().getTags();
    assertThat(tags)
        .containsEntry("Environment", "production")
        .containsEntry("Team", "platform")
        .containsEntry(LambdaTagNamer.APPLICATION, "myapp")
        .containsEntry(LambdaTagNamer.CLUSTER, "myapp-stack")
        .containsEntry(LambdaTagNamer.STACK, "stack");
  }

  @Test
  void testAutoApplyTagsWithExistingMonikerTags() {
    // given
    Map<String, String> existingTags = new HashMap<>();
    existingTags.put(LambdaTagNamer.APPLICATION, "tagapp");
    existingTags.put(LambdaTagNamer.STACK, "tagstack");

    CreateLambdaFunctionDescription description =
        new CreateLambdaFunctionDescription()
            .setS3bucket("s3://bucket")
            .setS3key("key/path")
            .setFunctionName("oldapp-oldstack-v001")
            .setTags(existingTags)
            .setDeadLetterConfig(new DeadLetterConfig().withTargetArn(""));
    description.setAppName("myapp");
    config.setSetMonikerTags(true);
    CreateLambdaAtomicOperation operation =
        spy(new CreateLambdaAtomicOperation(description, config));
    doNothing().when(operation).updateTaskStatus(anyString());
    AWSLambda lambdaClient = mock(AWSLambda.class);
    doReturn(lambdaClient).when(operation).getLambdaClient();

    CreateFunctionResult result =
        new CreateFunctionResult().withFunctionName("myapp-oldstack-v001");
    doReturn(result).when(lambdaClient).createFunction(any(CreateFunctionRequest.class));

    // when
    operation.operate(null);

    // then
    ArgumentCaptor<CreateFunctionRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateFunctionRequest.class);
    verify(lambdaClient).createFunction(requestCaptor.capture());

    Map<String, String> tags = requestCaptor.getValue().getTags();
    // Existing tags should be preserved
    assertThat(tags).containsEntry(LambdaTagNamer.APPLICATION, "tagapp");
    assertThat(tags).containsEntry(LambdaTagNamer.STACK, "tagstack");
    // Cluster should be derived from tags
    assertThat(tags).containsEntry(LambdaTagNamer.CLUSTER, "tagapp-tagstack");
  }

  @Test
  void testAutoApplyTagsAlwaysSetsApplicationTag() {
    // given - no tags at all initially
    CreateLambdaFunctionDescription description =
        new CreateLambdaFunctionDescription()
            .setS3bucket("s3://bucket")
            .setS3key("key/path")
            .setFunctionName("randomname")
            .setTags(new HashMap<>()) // Initialize empty tags map
            .setDeadLetterConfig(new DeadLetterConfig().withTargetArn(""));
    description.setAppName("myapp");

    CreateLambdaAtomicOperation operation =
        spy(new CreateLambdaAtomicOperation(description, config));
    doNothing().when(operation).updateTaskStatus(anyString());
    AWSLambda lambdaClient = mock(AWSLambda.class);
    doReturn(lambdaClient).when(operation).getLambdaClient();

    CreateFunctionResult result = new CreateFunctionResult().withFunctionName("myapp-randomname");
    doReturn(result).when(lambdaClient).createFunction(any(CreateFunctionRequest.class));

    // when
    operation.operate(null);

    // then
    ArgumentCaptor<CreateFunctionRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateFunctionRequest.class);
    verify(lambdaClient).createFunction(requestCaptor.capture());

    Map<String, String> tags = requestCaptor.getValue().getTags();
    // Application tag should always be set
    assertThat(tags).containsEntry(LambdaTagNamer.APPLICATION, "myapp");
  }
}
