/*
 * Copyright 2026 Harness, Inc. or its affiliates.
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
import com.amazonaws.services.lambda.model.*;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.CreateLambdaFunctionConfigurationDescription;
import com.netflix.spinnaker.clouddriver.lambda.names.LambdaTagNamer;
import com.netflix.spinnaker.clouddriver.lambda.provider.view.LambdaFunctionProvider;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class UpdateLambdaConfigurationAtomicOperationTest implements LambdaTestingDefaults {

  private LambdaFunctionProvider mockLambdaFunctionProvider;
  private AWSLambda mockLambdaClient;

  @BeforeEach
  void setup() {
    mockLambdaFunctionProvider = mock(LambdaFunctionProvider.class);
    mockLambdaClient = mock(AWSLambda.class);
  }

  @Test
  void testUpdateFunctionConfiguration() {
    // given
    CreateLambdaFunctionConfigurationDescription description =
        new CreateLambdaFunctionConfigurationDescription();
    description.setFunctionName(fName);
    description.setAppName("app1");
    description.setAccount(account);
    description.setRegion(region);
    description.setDescription("Updated description");

    LambdaFunction cachedFunction = getMockedFunctionDefintion();

    UpdateLambdaConfigurationAtomicOperation operation =
        spy(new UpdateLambdaConfigurationAtomicOperation(description, false));
    doNothing().when(operation).updateTaskStatus(anyString());
    doReturn(mockLambdaClient).when(operation).getLambdaClient();
    operation.lambdaFunctionProvider = mockLambdaFunctionProvider;

    when(mockLambdaFunctionProvider.getFunction(account, region, fName))
        .thenReturn(cachedFunction);

    UpdateFunctionConfigurationResult result = new UpdateFunctionConfigurationResult();
    result.setFunctionArn(functionArn);
    when(mockLambdaClient.updateFunctionConfiguration(any(UpdateFunctionConfigurationRequest.class)))
        .thenReturn(result);

    when(mockLambdaClient.listTags(any(ListTagsRequest.class)))
        .thenReturn(new ListTagsResult().withTags(Collections.emptyMap()));

    // when
    UpdateFunctionConfigurationResult output = operation.operate(null);

    // then
    assertThat(output).isNotNull();
    assertThat(output.getFunctionArn()).isEqualTo(functionArn);
    verify(mockLambdaClient).updateFunctionConfiguration(any(UpdateFunctionConfigurationRequest.class));
  }

  @Test
  void testAutoApplyTagsWhenEnabled() {
    // given
    CreateLambdaFunctionConfigurationDescription description =
        new CreateLambdaFunctionConfigurationDescription();
    description.setFunctionName("myapp-stack-detail-v001");
    description.setAppName("myapp");
    description.setAccount(account);
    description.setRegion(region);
    description.setTags(new HashMap<>()); // Initialize empty tags map

    LambdaFunction cachedFunction = new LambdaFunction();
    cachedFunction.setFunctionName("myapp-stack-detail-v001");
    cachedFunction.setFunctionArn(functionArn);
    cachedFunction.setTargetGroups(Collections.emptyList());

    UpdateLambdaConfigurationAtomicOperation operation =
        spy(new UpdateLambdaConfigurationAtomicOperation(description, true));
    doNothing().when(operation).updateTaskStatus(anyString());
    doReturn(mockLambdaClient).when(operation).getLambdaClient();
    operation.lambdaFunctionProvider = mockLambdaFunctionProvider;

    when(mockLambdaFunctionProvider.getFunction(account, region, "myapp-stack-detail-v001"))
        .thenReturn(cachedFunction);

    UpdateFunctionConfigurationResult result = new UpdateFunctionConfigurationResult();
    result.setFunctionArn(functionArn);
    when(mockLambdaClient.updateFunctionConfiguration(any(UpdateFunctionConfigurationRequest.class)))
        .thenReturn(result);

    when(mockLambdaClient.listTags(any(ListTagsRequest.class)))
        .thenReturn(new ListTagsResult().withTags(Collections.emptyMap()));

    // when
    operation.operate(null);

    // then
    ArgumentCaptor<TagResourceRequest> tagRequestCaptor =
        ArgumentCaptor.forClass(TagResourceRequest.class);
    verify(mockLambdaClient).tagResource(tagRequestCaptor.capture());

    Map<String, String> tags = tagRequestCaptor.getValue().getTags();
    assertThat(tags).containsEntry(LambdaTagNamer.APPLICATION, "myapp");
    assertThat(tags).containsEntry(LambdaTagNamer.CLUSTER, "myapp-stack-detail");
    assertThat(tags).containsEntry(LambdaTagNamer.STACK, "stack");
    assertThat(tags).containsEntry(LambdaTagNamer.DETAIL, "detail");
    assertThat(tags).containsEntry(LambdaTagNamer.SEQUENCE, "1");
  }

  @Test
  void testNoAutoApplyTagsWhenDisabled() {
    // given
    CreateLambdaFunctionConfigurationDescription description =
        new CreateLambdaFunctionConfigurationDescription();
    description.setFunctionName("stack-detail-v001");
    description.setAppName("myapp");
    description.setAccount(account);
    description.setRegion(region);

    LambdaFunction cachedFunction = new LambdaFunction();
    cachedFunction.setFunctionName("myapp-stack-detail-v001");
    cachedFunction.setFunctionArn(functionArn);
    cachedFunction.setTargetGroups(Collections.emptyList());

    UpdateLambdaConfigurationAtomicOperation operation =
        spy(new UpdateLambdaConfigurationAtomicOperation(description, false));
    doNothing().when(operation).updateTaskStatus(anyString());
    doReturn(mockLambdaClient).when(operation).getLambdaClient();
    operation.lambdaFunctionProvider = mockLambdaFunctionProvider;

    when(mockLambdaFunctionProvider.getFunction(account, region, "stack-detail-v001"))
        .thenReturn(cachedFunction);

    UpdateFunctionConfigurationResult result = new UpdateFunctionConfigurationResult();
    result.setFunctionArn(functionArn);
    when(mockLambdaClient.updateFunctionConfiguration(any(UpdateFunctionConfigurationRequest.class)))
        .thenReturn(result);

    when(mockLambdaClient.listTags(any(ListTagsRequest.class)))
        .thenReturn(new ListTagsResult().withTags(Collections.emptyMap()));

    // when
    operation.operate(null);

    // then - no tags should be applied
    verify(mockLambdaClient, never()).tagResource(any(TagResourceRequest.class));
    verify(mockLambdaClient, never()).untagResource(any(UntagResourceRequest.class));
  }

  @Test
  void testAutoApplyTagsPreservesExistingTags() {
    // given
    Map<String, String> existingTags = new HashMap<>();
    existingTags.put("Environment", "production");
    existingTags.put("Team", "platform");

    CreateLambdaFunctionConfigurationDescription description =
        new CreateLambdaFunctionConfigurationDescription();
    description.setFunctionName("myapp-stack-v001");
    description.setAppName("myapp");
    description.setAccount(account);
    description.setRegion(region);
    description.setTags(existingTags);

    LambdaFunction cachedFunction = new LambdaFunction();
    cachedFunction.setFunctionName("myapp-stack-v001");
    cachedFunction.setFunctionArn(functionArn);
    cachedFunction.setTargetGroups(Collections.emptyList());

    UpdateLambdaConfigurationAtomicOperation operation =
        spy(new UpdateLambdaConfigurationAtomicOperation(description, true));
    doNothing().when(operation).updateTaskStatus(anyString());
    doReturn(mockLambdaClient).when(operation).getLambdaClient();
    operation.lambdaFunctionProvider = mockLambdaFunctionProvider;

    when(mockLambdaFunctionProvider.getFunction(account, region, "myapp-stack-v001"))
        .thenReturn(cachedFunction);

    UpdateFunctionConfigurationResult result = new UpdateFunctionConfigurationResult();
    result.setFunctionArn(functionArn);
    when(mockLambdaClient.updateFunctionConfiguration(any(UpdateFunctionConfigurationRequest.class)))
        .thenReturn(result);

    // Mock existing tags on the function
    Map<String, String> existingFunctionTags = new HashMap<>();
    existingFunctionTags.put("OldTag", "oldValue");
    when(mockLambdaClient.listTags(any(ListTagsRequest.class)))
        .thenReturn(new ListTagsResult().withTags(existingFunctionTags));

    // when
    operation.operate(null);

    // then
    ArgumentCaptor<TagResourceRequest> tagRequestCaptor =
        ArgumentCaptor.forClass(TagResourceRequest.class);
    verify(mockLambdaClient).tagResource(tagRequestCaptor.capture());

    Map<String, String> tags = tagRequestCaptor.getValue().getTags();
    assertThat(tags).containsEntry("Environment", "production");
    assertThat(tags).containsEntry("Team", "platform");
    assertThat(tags).containsEntry(LambdaTagNamer.APPLICATION, "myapp");
    assertThat(tags).containsEntry(LambdaTagNamer.CLUSTER, "myapp-stack");
    assertThat(tags).containsEntry(LambdaTagNamer.STACK, "stack");
  }

  @Test
  void testUpdateReplacesAllExistingTags() {
    // given
    Map<String, String> newTags = new HashMap<>();
    newTags.put("NewTag", "newValue");

    CreateLambdaFunctionConfigurationDescription description =
        new CreateLambdaFunctionConfigurationDescription();
    description.setFunctionName(fName);
    description.setAppName("app1");
    description.setAccount(account);
    description.setRegion(region);
    description.setTags(newTags);

    LambdaFunction cachedFunction = new LambdaFunction();
    cachedFunction.setFunctionName(fName);
    cachedFunction.setFunctionArn(functionArn);
    cachedFunction.setTargetGroups(Collections.emptyList());

    UpdateLambdaConfigurationAtomicOperation operation =
        spy(new UpdateLambdaConfigurationAtomicOperation(description, false));
    doNothing().when(operation).updateTaskStatus(anyString());
    doReturn(mockLambdaClient).when(operation).getLambdaClient();
    operation.lambdaFunctionProvider = mockLambdaFunctionProvider;

    when(mockLambdaFunctionProvider.getFunction(account, region, fName))
        .thenReturn(cachedFunction);

    UpdateFunctionConfigurationResult result = new UpdateFunctionConfigurationResult();
    result.setFunctionArn(functionArn);
    when(mockLambdaClient.updateFunctionConfiguration(any(UpdateFunctionConfigurationRequest.class)))
        .thenReturn(result);

    // Mock existing tags that should be removed
    Map<String, String> existingTags = new HashMap<>();
    existingTags.put("OldTag1", "value1");
    existingTags.put("OldTag2", "value2");
    when(mockLambdaClient.listTags(any(ListTagsRequest.class)))
        .thenReturn(new ListTagsResult().withTags(existingTags));

    // when
    operation.operate(null);

    // then - old tags should be removed, new ones added
    ArgumentCaptor<UntagResourceRequest> untagRequestCaptor =
        ArgumentCaptor.forClass(UntagResourceRequest.class);
    verify(mockLambdaClient).untagResource(untagRequestCaptor.capture());

    assertThat(untagRequestCaptor.getValue().getTagKeys()).contains("OldTag1", "OldTag2");

    ArgumentCaptor<TagResourceRequest> tagRequestCaptor =
        ArgumentCaptor.forClass(TagResourceRequest.class);
    verify(mockLambdaClient).tagResource(tagRequestCaptor.capture());

    Map<String, String> appliedTags = tagRequestCaptor.getValue().getTags();
    assertThat(appliedTags).containsEntry("NewTag", "newValue");
    assertThat(appliedTags).doesNotContainKey("OldTag1");
    assertThat(appliedTags).doesNotContainKey("OldTag2");
  }

  @Test
  void testAutoApplyTagsWithExistingMonikerTags() {
    // given
    Map<String, String> existingTags = new HashMap<>();
    existingTags.put(LambdaTagNamer.APPLICATION, "tagapp");
    existingTags.put(LambdaTagNamer.STACK, "tagstack");

    CreateLambdaFunctionConfigurationDescription description =
        new CreateLambdaFunctionConfigurationDescription();
    description.setFunctionName("oldapp-oldstack-v001");
    description.setAppName("myapp");
    description.setAccount(account);
    description.setRegion(region);
    description.setTags(existingTags);

    LambdaFunction cachedFunction = new LambdaFunction();
    cachedFunction.setFunctionName("oldapp-oldstack-v001");
    cachedFunction.setFunctionArn(functionArn);
    cachedFunction.setTargetGroups(Collections.emptyList());

    UpdateLambdaConfigurationAtomicOperation operation =
        spy(new UpdateLambdaConfigurationAtomicOperation(description, true));
    doNothing().when(operation).updateTaskStatus(anyString());
    doReturn(mockLambdaClient).when(operation).getLambdaClient();
    operation.lambdaFunctionProvider = mockLambdaFunctionProvider;

    when(mockLambdaFunctionProvider.getFunction(account, region, "oldapp-oldstack-v001"))
        .thenReturn(cachedFunction);

    UpdateFunctionConfigurationResult result = new UpdateFunctionConfigurationResult();
    result.setFunctionArn(functionArn);
    when(mockLambdaClient.updateFunctionConfiguration(any(UpdateFunctionConfigurationRequest.class)))
        .thenReturn(result);

    when(mockLambdaClient.listTags(any(ListTagsRequest.class)))
        .thenReturn(new ListTagsResult().withTags(Collections.emptyMap()));

    // when
    operation.operate(null);

    // then
    ArgumentCaptor<TagResourceRequest> tagRequestCaptor =
        ArgumentCaptor.forClass(TagResourceRequest.class);
    verify(mockLambdaClient).tagResource(tagRequestCaptor.capture());

    Map<String, String> tags = tagRequestCaptor.getValue().getTags();
    // Existing tags should be preserved
    assertThat(tags).containsEntry(LambdaTagNamer.APPLICATION, "tagapp");
    assertThat(tags).containsEntry(LambdaTagNamer.STACK, "tagstack");
    // Cluster should be derived from tags
    assertThat(tags).containsEntry(LambdaTagNamer.CLUSTER, "tagapp-tagstack");
  }

  @Test
  void testAutoApplyTagsAlwaysSetsApplicationTag() {
    // given - no tags at all initially
    CreateLambdaFunctionConfigurationDescription description =
        new CreateLambdaFunctionConfigurationDescription();
    description.setFunctionName("randomname");
    description.setAppName("myapp");
    description.setAccount(account);
    description.setRegion(region);
    description.setTags(new HashMap<>()); // Initialize empty tags map

    LambdaFunction cachedFunction = new LambdaFunction();
    cachedFunction.setFunctionName("myapp-randomname");
    cachedFunction.setFunctionArn(functionArn);
    cachedFunction.setTargetGroups(Collections.emptyList());

    UpdateLambdaConfigurationAtomicOperation operation =
        spy(new UpdateLambdaConfigurationAtomicOperation(description, true));
    doNothing().when(operation).updateTaskStatus(anyString());
    doReturn(mockLambdaClient).when(operation).getLambdaClient();
    operation.lambdaFunctionProvider = mockLambdaFunctionProvider;

    when(mockLambdaFunctionProvider.getFunction(account, region, "randomname"))
        .thenReturn(cachedFunction);

    UpdateFunctionConfigurationResult result = new UpdateFunctionConfigurationResult();
    result.setFunctionArn(functionArn);
    when(mockLambdaClient.updateFunctionConfiguration(any(UpdateFunctionConfigurationRequest.class)))
        .thenReturn(result);

    when(mockLambdaClient.listTags(any(ListTagsRequest.class)))
        .thenReturn(new ListTagsResult().withTags(Collections.emptyMap()));

    // when
    operation.operate(null);

    // then
    ArgumentCaptor<TagResourceRequest> tagRequestCaptor =
        ArgumentCaptor.forClass(TagResourceRequest.class);
    verify(mockLambdaClient).tagResource(tagRequestCaptor.capture());

    Map<String, String> tags = tagRequestCaptor.getValue().getTags();
    // Application tag should always be set
    assertThat(tags).containsEntry(LambdaTagNamer.APPLICATION, "myapp");
  }
}
