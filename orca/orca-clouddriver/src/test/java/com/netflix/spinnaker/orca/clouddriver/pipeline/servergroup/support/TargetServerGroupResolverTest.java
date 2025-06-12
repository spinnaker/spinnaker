/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerConversionException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import groovy.lang.Closure;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class TargetServerGroupResolverTest {

  @RegisterExtension
  static WireMockExtension wmOort =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static OortService oortService;
  static TargetServerGroupResolver targetServerGroupResolver;
  static ObjectMapper mapper = new ObjectMapper();

  @BeforeAll
  public static void setup() {
    oortService =
        new Retrofit.Builder()
            .baseUrl(wmOort.baseUrl())
            .client(new OkHttpClient())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(OortService.class);

    RetrySupport realRetrySupport = new RetrySupport();
    RetrySupport retrySupportMock = mock(RetrySupport.class);
    when(retrySupportMock.retry(any(), anyInt(), any(Duration.class), anyBoolean()))
        .thenAnswer(
            invocation -> {
              return realRetrySupport.retry(
                  invocation.getArgument(0),
                  invocation.getArgument(1),
                  Duration.ofMillis(1) /* to reduce test time */,
                  invocation.getArgument(3));
            });

    targetServerGroupResolver = new TargetServerGroupResolver();
    targetServerGroupResolver.setMapper(mapper);
    targetServerGroupResolver.setOortService(oortService);
    targetServerGroupResolver.setRetrySupport(retrySupportMock);
  }

  @ParameterizedTest(name = "{index} ==> when the response is {0}")
  // Another kind of invalid is something that deserializes into a list, but from which it's not
  // possible to construct  a TargetServerGroup from the appropriate element.
  // That's a different test though, as it doesn't generate a conversion error.
  @ValueSource(strings = {"non-json response", "[ \"list-element\": 5 ]"})
  public void resolveByParams_byTarget_throws_ConversionError_with_invalid_response(
      String invalidResponse) {

    wmOort.stubFor(
        WireMock.get(
                urlEqualTo(
                    "/applications/test/clusters/testCreds/test-app/abc/north-pole/serverGroups/target/current_asg"))
            .willReturn(aResponse().withStatus(200).withBody(invalidResponse)));

    TargetServerGroup.Params params = new TargetServerGroup.Params();
    params.setCloudProvider("abc");
    params.setCluster("test-app");
    params.setCredentials("testCreds");
    params.setLocations(
        List.of(new Location(Map.of("type", Location.Type.REGION, "value", "north-pole"))));
    params.setTarget(TargetServerGroup.Params.Target.current_asg);

    Throwable thrown = catchThrowable(() -> targetServerGroupResolver.resolveByParams(params));
    assertThat(thrown).isInstanceOf(SpinnakerConversionException.class);
    assertThat(thrown.getMessage()).contains("Failed to process response body");
  }

  @ParameterizedTest(name = "{index} ==> when the response is {0}")
  // Another kind of invalid is something that deserializes into a list, but from which it's not
  // possible to construct  a TargetServerGroup from the appropriate element.
  // That's a different test though, as it doesn't generate a conversion error.
  @ValueSource(strings = {"non-json response", "{ \"some-property\": 5 }"})
  public void resolveByParams_byServerGroupName_throws_ConversionError_with_invalid_response(
      String invalidResponse) {

    wmOort.stubFor(
        WireMock.get(
                urlEqualTo(
                    "/applications/test/clusters/testCreds/test-app/gce/serverGroups/test-app-v010"))
            .willReturn(aResponse().withStatus(200).withBody(invalidResponse)));

    TargetServerGroup.Params params = new TargetServerGroup.Params();
    params.setCloudProvider("gce");
    params.setServerGroupName("test-app-v010");
    params.setCredentials("testCreds");
    params.setLocations(
        List.of(new Location(Map.of("type", Location.Type.REGION, "value", "north-pole"))));

    Throwable thrown = catchThrowable(() -> targetServerGroupResolver.resolveByParams(params));
    assertThat(thrown).isInstanceOf(SpinnakerConversionException.class);
    assertThat(thrown.getMessage()).contains("Failed to process response body");
  }

  @Test
  public void shouldResolveToTargetServerGroups() throws JsonProcessingException {
    // Case 1: resolve using current_asg target
    ServerGroup sg1 = new ServerGroup();
    sg1.setName("test-app-v010");
    sg1.setRegion("north-pole");
    ServerGroup.Asg asg1 = new ServerGroup.Asg();
    asg1.setSuspendedProcesses(List.of());
    sg1.setAsg(asg1);

    wmOort.stubFor(
        WireMock.get(
                urlEqualTo(
                    "/applications/test/clusters/testCreds/test-app/abc/north-pole/serverGroups/target/current_asg"))
            .willReturn(aResponse().withStatus(200).withBody(mapper.writeValueAsString(sg1))));

    TargetServerGroup.Params params1 = new TargetServerGroup.Params();
    params1.setCloudProvider("abc");
    params1.setCluster("test-app");
    params1.setCredentials("testCreds");
    params1.setLocations(List.of(new Location(Location.Type.REGION, "north-pole")));
    params1.setTarget(TargetServerGroup.Params.Target.current_asg);

    List<TargetServerGroup> tsgs1 = targetServerGroupResolver.resolveByParams(params1);
    assertThat(tsgs1.size()).isEqualTo(1);
    assertThat(tsgs1.get(0).getLocation()).isNotNull();
    assertThat(tsgs1.get(0).getLocation().getType()).isEqualTo(Location.Type.REGION);
    assertThat(tsgs1.get(0).getLocation().getValue()).isEqualTo("north-pole");

    // Case 2: resolve using serverGroupName
    ServerGroup sg2 = new ServerGroup();
    sg2.setName("test-app-v010");
    sg2.setRegion("north-pole");
    ServerGroup.Asg asg2 = new ServerGroup.Asg();
    asg2.setSuspendedProcesses(List.of());
    sg2.setAsg(asg2);

    wmOort.stubFor(
        WireMock.get(
                urlEqualTo(
                    "/applications/test/clusters/testCreds/test-app/gce/serverGroups/test-app-v010"))
            .willReturn(
                aResponse().withStatus(200).withBody(mapper.writeValueAsString(List.of(sg2)))));

    TargetServerGroup.Params params2 = new TargetServerGroup.Params();
    params2.setCloudProvider("gce");
    params2.setServerGroupName("test-app-v010");
    params2.setCredentials("testCreds");
    params2.setLocations(List.of(new Location(Location.Type.REGION, "north-pole")));

    List<TargetServerGroup> tsgs2 = targetServerGroupResolver.resolveByParams(params2);
    assertThat(tsgs2.size()).isEqualTo(1);
    assertThat(tsgs2.get(0).getLocation()).isNotNull();
    assertThat(tsgs2.get(0).getLocation().getType()).isEqualTo(Location.Type.REGION);
    assertThat(tsgs2.get(0).getLocation().getValue()).isEqualTo("north-pole");

    // Case 3: null params returns empty list
    List<TargetServerGroup> tsgs3 = targetServerGroupResolver.resolveByParams(null);
    assertThat(tsgs3.size()).isEqualTo(0);

    // Case 4: empty params returns empty list
    List<TargetServerGroup> tsgs4 =
        targetServerGroupResolver.resolveByParams(new TargetServerGroup.Params());
    assertThat(tsgs4.size()).isEqualTo(0);
  }

  @Test
  public void shouldResolveTargetRefsFromPreviousDTSGStage() {
    TargetServerGroup want =
        new TargetServerGroup(Map.of("name", "testTSG", "region", "north-pole"));
    TargetServerGroup decoy =
        new TargetServerGroup(Map.of("name", "testTSG", "region", "south-pole"));

    StageExecutionImpl commonParent = new StageExecutionImpl();
    commonParent.setRefId("1");

    StageExecutionImpl dtsgStage = new StageExecutionImpl();
    dtsgStage.setRefId("1<1");
    dtsgStage.setParentStageId(commonParent.getId());
    dtsgStage.setType(DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE);
    dtsgStage.setContext(
        new HashMap<>() {
          {
            put("targetReferences", Arrays.asList(decoy, want));
          }
        });

    StageExecutionImpl thirdStage = new StageExecutionImpl();
    thirdStage.setRefId("1<2");
    thirdStage.setParentStageId(commonParent.getId());
    thirdStage.setRequisiteStageRefIds(Arrays.asList("1<1"));

    StageExecutionImpl stageLookingForRefs = new StageExecutionImpl();
    stageLookingForRefs.setRefId("1<2<1");
    stageLookingForRefs.setParentStageId(thirdStage.getId());
    stageLookingForRefs.setContext(
        new HashMap<>() {
          {
            put("region", "north-pole");
          }
        });

    PipelineExecutionImpl pipelineExecution =
        new PipelineExecutionImpl(ExecutionType.PIPELINE, "test");

    pipelineExecution.getStages().add(commonParent);
    pipelineExecution.getStages().add(dtsgStage);
    pipelineExecution.getStages().add(thirdStage);
    pipelineExecution.getStages().add(stageLookingForRefs);

    // Set back-references
    commonParent.setExecution(pipelineExecution);
    dtsgStage.setExecution(pipelineExecution);
    thirdStage.setExecution(pipelineExecution);
    stageLookingForRefs.setExecution(pipelineExecution);

    // When
    TargetServerGroup result = TargetServerGroupResolver.fromPreviousStage(stageLookingForRefs);

    // Then
    assertThat(result).isEqualTo(want);

    // When
    stageLookingForRefs.setContext(
        new HashMap<>() {
          {
            put("region", "east-1"); // doesn't exist.
          }
        });
    Throwable thrown =
        catchThrowable(() -> TargetServerGroupResolver.fromPreviousStage(stageLookingForRefs));

    // Then
    assertThat(thrown).isInstanceOf(TargetServerGroup.NotFoundException.class);
    assertThat(thrown)
        .hasMessageContaining(
            "No targets found on matching any location in [east-1] in target server groups");
  }

  @Test
  void shouldResolveTargetRefsFromDirectlyPrecedingDTSGStageIfThereAreMoreThanOne() {
    TargetServerGroup want =
        new TargetServerGroup(Map.of("name", "i-want-this-one", "region", "us-west-2"));
    TargetServerGroup decoy =
        new TargetServerGroup(Map.of("name", "not-this-one", "region", "us-west-2"));

    // Stage "1" with sub-stages
    StageExecutionImpl stage1 = new StageExecutionImpl();
    stage1.setRefId("1");

    StageExecutionImpl stage1_1 = new StageExecutionImpl();
    stage1_1.setRefId("1<1");
    stage1_1.setParentStageId(stage1.getId());
    stage1_1.setType(DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE);
    stage1_1.setContext(Map.of("targetReferences", List.of(decoy)));

    StageExecutionImpl stage1_2 = new StageExecutionImpl();
    stage1_2.setRefId("1<2");
    stage1_2.setParentStageId(stage1.getId());
    stage1_2.setRequisiteStageRefIds(List.of("1<1"));
    stage1_2.setContext(Map.of("region", "us-west-2"));

    // Stage "2" with sub-stages
    StageExecutionImpl stage2 = new StageExecutionImpl();
    stage2.setRefId("2");
    stage2.setRequisiteStageRefIds(List.of("1"));

    StageExecutionImpl stage2_1 = new StageExecutionImpl();
    stage2_1.setRefId("2<1");
    stage2_1.setParentStageId(stage2.getId());
    stage2_1.setType(DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE);
    stage2_1.setContext(Map.of("targetReferences", List.of(want)));

    StageExecutionImpl stage2_2 = new StageExecutionImpl();
    stage2_2.setRefId("2<2");
    stage2_2.setParentStageId(stage2.getId());
    stage2_2.setRequisiteStageRefIds(List.of("2<1"));
    stage2_2.setContext(Map.of("region", "us-west-2"));

    PipelineExecutionImpl pipeline = new PipelineExecutionImpl(ExecutionType.PIPELINE, "test");
    pipeline.getStages().add(stage1);
    pipeline.getStages().add(stage1_1);
    pipeline.getStages().add(stage1_2);
    pipeline.getStages().add(stage2);
    pipeline.getStages().add(stage2_1);
    pipeline.getStages().add(stage2_2);

    // Set back-references
    stage1.setExecution(pipeline);
    stage1_1.setExecution(pipeline);
    stage1_2.setExecution(pipeline);
    stage2.setExecution(pipeline);
    stage2_1.setExecution(pipeline);
    stage2_2.setExecution(pipeline);

    StageExecution stageLookingForRefs = pipeline.stageByRef("2<2");

    // when
    TargetServerGroup got = TargetServerGroupResolver.fromPreviousStage(stageLookingForRefs);

    // then
    assertThat(got).isEqualTo(want);
  }

  private static class TestCase {
    final Exception exception;
    final boolean expectNull;
    final int expectedInvocationCount;

    TestCase(Exception exception, boolean expectNull, int expectedInvocationCount) {
      this.exception = exception;
      this.expectNull = expectNull;
      this.expectedInvocationCount = expectedInvocationCount;
    }
  }

  static Stream<TestCase> provideTestCases() {
    return Stream.of(
        new TestCase(
            new IllegalStateException("should retry"),
            false,
            TargetServerGroupResolver.NUM_RETRIES),
        new TestCase(makeSpinnakerHttpException(400), false, 1),
        new TestCase(makeSpinnakerHttpException(404), true, 1),
        new TestCase(makeSpinnakerHttpException(500), false, TargetServerGroupResolver.NUM_RETRIES),
        new TestCase(makeSpinnakerHttpException(429), false, TargetServerGroupResolver.NUM_RETRIES),
        new TestCase(makeSpinnakerConversionException(), false, 1),
        new TestCase(
            makeSpinnakerNetworkException(), false, TargetServerGroupResolver.NUM_RETRIES));
  }

  @ParameterizedTest
  @MethodSource("provideTestCases")
  void should_retry_on_non_404_400_and_conversion_errors(TestCase testCase) throws Exception {
    AtomicInteger invocationCount = new AtomicInteger();
    Closure<Object> fetchClosure =
        new Closure<>(null) {
          @SneakyThrows
          public Object call() {
            invocationCount.incrementAndGet();
            throw testCase.exception;
          }
        };

    Object capturedResult;
    try {
      capturedResult = targetServerGroupResolver.fetchWithRetries(fetchClosure);
    } catch (Exception e) {
      capturedResult = e;
    }
    if (testCase.expectNull) assertThat(capturedResult).isNull();
    else assertThat(capturedResult).isInstanceOf(testCase.exception.getClass());

    assertThat(invocationCount.get()).isEqualTo(testCase.expectedInvocationCount);
  }

  public static SpinnakerHttpException makeSpinnakerHttpException(int status) {
    String url = "https://some-url";
    retrofit2.Response retrofit2Response =
        retrofit2.Response.error(
            status,
            ResponseBody.create(
                MediaType.parse("application/json"), "{ \"message\": \"arbitrary message\" }"));

    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build();

    return new SpinnakerHttpException(retrofit2Response, retrofit);
  }

  public static SpinnakerConversionException makeSpinnakerConversionException() {
    Request request = new Request.Builder().url("http://some-url").build();
    return new SpinnakerConversionException(
        "arbitrary message", new RuntimeException("arbitrary message"), request);
  }

  public static SpinnakerNetworkException makeSpinnakerNetworkException() {
    Request request = new Request.Builder().url("http://some-url").build();
    return new SpinnakerNetworkException(new RuntimeException("arbitrary message"), request);
  }
}
