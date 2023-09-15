/*
 * Copyright 2023 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.q.handler;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.orca.TaskResolver;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskExecutionInterceptor;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution;
import com.netflix.spinnaker.orca.exceptions.DefaultExceptionHandler;
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler;
import com.netflix.spinnaker.orca.lock.RetriableLock;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.TaskExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator;
import com.netflix.spinnaker.orca.q.DummyTask;
import com.netflix.spinnaker.orca.q.RunTask;
import com.netflix.spinnaker.orca.retrofit.exceptions.RetrofitExceptionHandler;
import com.netflix.spinnaker.orca.retrofit.exceptions.SpinnakerServerExceptionHandler;
import com.netflix.spinnaker.q.Queue;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.converter.GsonConverter;
import retrofit.mime.TypedByteArray;
import retrofit.mime.TypedString;

/**
 * It's potentially a better test to use spring to configure all available ExceptionHandlers. To be
 * the most future proof would involve bringing in the entire context via Main to make sure we
 * really get all of them. Since there are only a small number of ExceptionHandlers, let's speed up
 * / simplify the test and explicitly list them.
 */
class RunTaskHandlerExceptionHandlerTest {
  private static final String URL = "https://some-url";

  private static final Response response =
      new Response(
          URL,
          500,
          "arbitrary reason",
          List.of(),
          new TypedString("{ message: \"arbitrary message\" }"));

  private static final Response response503 =
      new Response(
          URL,
          503,
          "arbitrary reason",
          List.of(),
          new TypedString("{ message: \"arbitrary message\" }"));

  private static final Response responseWithError =
      new Response(
          URL,
          500,
          "arbitrary reason",
          List.of(),
          new TypedString("{ message: \"arbitrary message\", error: \"error property\" }"));

  private static final Response responseWithException =
      new Response(
          URL,
          500,
          "arbitrary reason",
          List.of(),
          new TypedString("{ message: \"arbitrary message\", exception: \"exception property\" }"));

  private static final Response responseWithErrors =
      new Response(
          URL,
          500,
          "arbitrary reason",
          List.of(),
          new TypedString(
              "{ message: \"arbitrary message\", errors: [\"error one\", \"error two\"] }"));

  private static final Response responseWithErrorAndErrors =
      new Response(
          URL,
          500,
          "arbitrary reason",
          List.of(),
          new TypedString(
              "{ message: \"arbitrary message\", error: \"error property\", errors: [\"error one\", \"error two\"] }"));

  private static final Response responseWithErrorAndErrorsAndMessages =
      new Response(
          URL,
          500,
          "arbitrary reason",
          List.of(),
          new TypedString(
              "{ message: \"arbitrary message\", error: \"error property\", errors: [\"error one\", \"error two\"], messages: [\"message one\", \"message two\"] }"));

  private RetrofitExceptionHandler retrofitExceptionHandler = new RetrofitExceptionHandler();
  private SpinnakerServerExceptionHandler spinnakerServerExceptionHandler =
      new SpinnakerServerExceptionHandler();
  private DefaultExceptionHandler defaultExceptionHandler = new DefaultExceptionHandler();

  /** Put RetrofitExceptionHandler first since its bean is marked as highest precedence */
  private List<ExceptionHandler> exceptionHandlers =
      List.of(retrofitExceptionHandler, spinnakerServerExceptionHandler, defaultExceptionHandler);

  private Queue queue = mock(Queue.class);

  private ExecutionRepository executionRepository = mock(ExecutionRepository.class);

  private DummyTask dummyTask = mock(DummyTask.class);

  private Collection<Task> tasks = List.of(dummyTask);

  private TasksProvider tasksProvider = new TasksProvider(tasks);

  private TaskResolver taskResolver;

  private Clock clock =
      Clock.fixed(Instant.now().truncatedTo(ChronoUnit.MILLIS), ZoneId.systemDefault());

  private List<TaskExecutionInterceptor> taskExecutionInterceptors = List.of();

  private RunTaskHandler runTaskHandler;

  StageExecution stageExecution;

  private RunTask runTaskMessage;

  RetriableLock retriableLock = mock(RetriableLock.class);

  @BeforeEach
  void setup() {
    // Set up enough scaffolding so it's possible for individual tests to mock
    // the behavior of our dummy task

    // Without this, the TaskResolver constructor throws an exception
    doReturn(dummyTask.getClass()).when(dummyTask).getExtensionClass();
    taskResolver = new TaskResolver(tasksProvider);

    DynamicConfigService dynamicConfigService = mock(DynamicConfigService.class);
    doReturn(0)
        .when(dynamicConfigService)
        .getConfig(any(), eq("tasks.warningInvocationTimeMs"), any());
    doReturn(0L)
        .when(dynamicConfigService)
        .getConfig(any(), eq("tasks.global.backOffPeriod"), any());

    // Always get the lock
    doAnswer(
            invocation -> {
              Runnable runnable = invocation.getArgument(1);
              runnable.run();
              return true;
            })
        .when(retriableLock)
        .lock(any(RetriableLock.RetriableLockOptions.class), any(Runnable.class));

    runTaskHandler =
        new RunTaskHandler(
            queue,
            executionRepository,
            mock(StageNavigator.class),
            mock(StageDefinitionBuilderFactory.class),
            mock(ContextParameterProcessor.class),
            taskResolver,
            clock,
            exceptionHandlers,
            taskExecutionInterceptors,
            new NoopRegistry(),
            dynamicConfigService,
            retriableLock);

    PipelineExecutionImpl pipeline = new PipelineExecutionImpl(PIPELINE, "test-application");

    // StageExecutionImpl requires a mutable map
    Map<String, Object> stageContext = new HashMap<>();
    TaskExecution taskExecution = new TaskExecutionImpl();
    String taskId = "1";
    taskExecution.setId(taskId);
    taskExecution.setStartTime(clock.instant().toEpochMilli());
    taskExecution.setImplementingClass("");

    stageExecution = new StageExecutionImpl(pipeline, "stage-type", "stage-name", stageContext);
    stageExecution.setTasks(List.of(taskExecution));
    pipeline.getStages().add(stageExecution);

    runTaskMessage =
        new RunTask(
            pipeline.getType(),
            pipeline.getId(),
            "foo",
            stageExecution.getId(),
            taskId,
            DummyTask.class);

    when(executionRepository.retrieve(PIPELINE, runTaskMessage.getExecutionId()))
        .thenReturn(pipeline);
  }

  @ParameterizedTest(name = "{index} => taskThrowsRetrofitErrorNoRetry {0}")
  @MethodSource("nonRetryableRetrofitErrors")
  void taskThrowsRetrofitErrorNoRetry(RetrofitError retrofitError) {
    // given an arbitrary RetrofitError that RetrofitExceptionHandler doesn't consider retryable
    doThrow(retrofitError).when(dummyTask).execute(any());

    // when
    runTaskHandler.handle(runTaskMessage);

    // verify that the exception wasn't retried
    verify(queue, never()).push(eq(runTaskMessage), any());

    // verify that exception handling has populated the stage context as
    // expected.  This duplicates some logic in RetrofitExceptionHandler, but at
    // least it helps detect future changes.
    Map<String, Object> responseBody = (Map<String, Object>) retrofitError.getBodyAs(Map.class);
    Response response = retrofitError.getResponse();
    String responseBodyString = new String(((TypedByteArray) response.getBody()).getBytes());
    String error = (String) responseBody.getOrDefault("error", response.getReason());
    List<String> errors =
        (List<String>)
            responseBody.getOrDefault("errors", responseBody.getOrDefault("messages", List.of()));
    String message = (String) responseBody.get("message");
    if (errors.isEmpty() && (message != null)) {
      errors = List.of(message);
    }

    ImmutableMap.Builder<String, Object> builder =
        ImmutableMap.<String, Object>builder()
            .put("responseBody", responseBodyString)
            .put("kind", RetrofitError.Kind.HTTP)
            .put("error", error)
            .put("errors", errors)
            .put("url", response.getUrl())
            .put("status", response.getStatus());

    Object exception = responseBody.get("exception");
    if (exception != null) {
      builder.put("rootException", exception);
    }

    Map<String, Object> responseDetails = builder.build();

    ExceptionHandler.Response expectedResponse =
        new ExceptionHandler.Response(
            "RetrofitError", "unspecified", responseDetails, false /* shouldRetry */);

    compareResponse(
        expectedResponse, (ExceptionHandler.Response) stageExecution.getContext().get("exception"));
  }

  private static Stream<RetrofitError> nonRetryableRetrofitErrors() {
    return Stream.of(
        makeRetrofitError(response),
        makeRetrofitError(responseWithError),
        makeRetrofitError(responseWithException),
        makeRetrofitError(responseWithErrors),
        makeRetrofitError(responseWithErrorAndErrors),
        makeRetrofitError(responseWithErrorAndErrorsAndMessages));
  }

  @Test
  void taskThrowsRetrofitErrorRetryable() {
    // given an arbitrary RetrofitError that RetrofitExceptionHandler considers retryable
    RetrofitError retrofitError = makeRetrofitError(response503);
    doThrow(retrofitError).when(dummyTask).execute(any());

    // when
    runTaskHandler.handle(runTaskMessage);

    // verify that the exception was retried
    verify(queue).push(eq(runTaskMessage), any());

    // On retry, expect no exception info in the context
    assertThat(stageExecution.getContext().get("exception")).isNull();
  }

  @Test
  void taskThrowsRetrofitErrorNonJsonResponse() {
    // given an arbitrary RetrofitError that RetrofitExceptionHandler doesn't consider retryable
    Response response =
        new Response(URL, 500, "arbitrary reason", List.of(), new TypedString("non-json response"));
    RetrofitError retrofitError = makeRetrofitError(response);
    doThrow(retrofitError).when(dummyTask).execute(any());

    // when
    runTaskHandler.handle(runTaskMessage);

    // verify that the exception wasn't retried
    verify(queue, never()).push(eq(runTaskMessage), any());

    // verify that exception handling has populated the stage context as
    // expected.  This duplicates some logic in RetrofitExceptionHandler, but at
    // least it helps detect future changes.
    Map<String, Object> responseDetails =
        Map.of(
            "error", response.getReason(),
            "errors", List.of(),
            "responseBody", "non-json response",
            "kind", RetrofitError.Kind.HTTP,
            "url", response.getUrl(),
            "status", response.getStatus());

    ExceptionHandler.Response expectedResponse =
        new ExceptionHandler.Response(
            "RetrofitError", "unspecified", responseDetails, false /* shouldRetry */);

    compareResponse(
        expectedResponse, (ExceptionHandler.Response) stageExecution.getContext().get("exception"));
  }

  @ParameterizedTest(name = "{index} => taskThrowsSpinnakerHttpExceptionNoRetry {0}")
  @MethodSource("nonRetryableSpinnakerHttpExceptions")
  void taskThrowsSpinnakerHttpExceptionNoRetry(SpinnakerHttpException spinnakerHttpException) {
    // given an arbitrary SpinnakerHttpException that SpinnakerServerExceptionHandler doesn't
    // consider retryable
    doThrow(spinnakerHttpException).when(dummyTask).execute(any());

    // when
    runTaskHandler.handle(runTaskMessage);

    // verify that the exception wasn't retried
    verify(queue, never()).push(eq(runTaskMessage), any());

    // verify that exception handling has populated the stage context as
    // expected.  This duplicates some logic in SpinnakerServerExceptionHandler,
    // but at least it helps detect future changes.
    Map<String, Object> responseBody = spinnakerHttpException.getResponseBody();
    String error = (String) responseBody.getOrDefault("error", spinnakerHttpException.getReason());
    List<String> errors =
        (List<String>)
            responseBody.getOrDefault("errors", responseBody.getOrDefault("messages", List.of()));
    String message = (String) responseBody.get("message");
    if (errors.isEmpty()) {
      errors = List.of(spinnakerHttpException.getMessage());
    }

    ImmutableMap.Builder<String, Object> builder =
        ImmutableMap.<String, Object>builder()
            .put("kind", "HTTP")
            .put("error", error)
            .put("errors", errors)
            .put("url", spinnakerHttpException.getUrl())
            .put("status", spinnakerHttpException.getResponseCode());

    Object exception = responseBody.get("exception");
    if (exception != null) {
      builder.put("rootException", exception);
    } else {
      builder.put("stackTrace", Throwables.getStackTraceAsString(spinnakerHttpException));
    }

    Map<String, Object> responseDetails = builder.build();

    ExceptionHandler.Response expectedResponse =
        new ExceptionHandler.Response(
            "SpinnakerHttpException", "unspecified", responseDetails, false /* shouldRetry */);

    compareResponse(
        expectedResponse, (ExceptionHandler.Response) stageExecution.getContext().get("exception"));
  }

  private static Stream<SpinnakerHttpException> nonRetryableSpinnakerHttpExceptions() {
    return nonRetryableRetrofitErrors()
        .map(retrofitError -> makeSpinnakerHttpException(retrofitError));
  }

  @Test
  void taskThrowsSpinnakerServerExceptionRetryable() {
    // given an arbitrary SpinnakerServerException that SpinnakerServerExceptionHandler considers
    // retryable
    SpinnakerServerException spinnakerServerException =
        makeSpinnakerHttpException(makeRetrofitError(response503));
    doThrow(spinnakerServerException).when(dummyTask).execute(any());

    // when
    runTaskHandler.handle(runTaskMessage);

    // verify that the exception was retried
    verify(queue).push(eq(runTaskMessage), any());

    // On retry, expect no exception info in the context
    assertThat(stageExecution.getContext().get("exception")).isNull();
  }

  @Disabled("until the SpinnakerHttpException constructor can handle non-json responses")
  @Test
  void taskThrowsSpinnakerHttpExceptionNonJsonResponse() {
    // given an arbitrary SpinnakerHttpException that SpinnakerServerExceptionHandler doesn't
    // consider retryable
    Response response =
        new Response(URL, 500, "arbitrary reason", List.of(), new TypedString("non-json response"));
    SpinnakerHttpException spinnakerHttpException =
        makeSpinnakerHttpException(makeRetrofitError(response));
    doThrow(spinnakerHttpException).when(dummyTask).execute(any());

    // when
    runTaskHandler.handle(runTaskMessage);

    // verify that the exception wasn't retried
    verify(queue, never()).push(eq(runTaskMessage), any());

    // verify that exception handling has populated the stage context as
    // expected.  There's no implementation for this yet in
    // SpinnakerServerExceptionHandler since SpinnakerHttpException can't handle
    // non-json responses yet.  The expected details here matches the current
    // behavior of RetrofitExceptionHandler.
    Map<String, Object> responseDetails =
        Map.of(
            "error", spinnakerHttpException.getReason(),
            "errors", List.of(),
            "responseBody", "non-json response",
            "kind", "HTTP",
            "url", spinnakerHttpException.getUrl(),
            "status", spinnakerHttpException.getResponseCode());

    ExceptionHandler.Response expectedResponse =
        new ExceptionHandler.Response(
            "SpinnakerHttpException", "unspecified", responseDetails, false /* shouldRetry */);

    compareResponse(
        expectedResponse, (ExceptionHandler.Response) stageExecution.getContext().get("exception"));
  }

  private void compareResponse(
      ExceptionHandler.Response expectedResponse, ExceptionHandler.Response actualResponse) {
    assertThat(actualResponse).isNotNull();
    // Don't look at overall equality since that includes a date
    assertThat(actualResponse.getExceptionType()).isEqualTo(expectedResponse.getExceptionType());
    assertThat(actualResponse.getOperation()).isEqualTo(expectedResponse.getOperation());
    assertThat(actualResponse.getDetails()).isEqualTo(expectedResponse.getDetails());
    assertThat(actualResponse.isShouldRetry()).isEqualTo(expectedResponse.isShouldRetry());
  }

  private static SpinnakerHttpException makeSpinnakerHttpException(RetrofitError retrofitError) {
    return new SpinnakerHttpException(retrofitError);
  }

  private static RetrofitError makeRetrofitError(Response response) {
    return RetrofitError.httpError(URL, response, new GsonConverter(new Gson()), String.class);
  }

  static class TasksProvider implements ObjectProvider<Collection<Task>> {

    final Collection<Task> tasks;

    TasksProvider(Collection<Task> tasks) {
      this.tasks = tasks;
    }

    @Override
    public @NotNull Collection<Task> getObject(Object... args) throws BeansException {
      return tasks;
    }

    @Override
    public Collection<Task> getIfAvailable() throws BeansException {
      return tasks;
    }

    @Override
    public Collection<Task> getIfUnique() throws BeansException {
      return tasks;
    }

    @Override
    public @NotNull Collection<Task> getObject() throws BeansException {
      return tasks;
    }
  }
}
