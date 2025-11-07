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
import com.netflix.spinnaker.orca.pipeline.persistence.ReadReplicaRequirement;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator;
import com.netflix.spinnaker.orca.q.DummyTask;
import com.netflix.spinnaker.orca.q.RunTask;
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
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * It's potentially a better test to use spring to configure all available ExceptionHandlers. To be
 * the most future proof would involve bringing in the entire context via Main to make sure we
 * really get all of them. Since there are only a small number of ExceptionHandlers, let's speed up
 * / simplify the test and explicitly list them.
 */
class RunTaskHandlerExceptionHandlerTest {
  private static final String message = "{ \"message\": \"arbitrary message\" }";
  private static final String messageWithError =
      "{ \"message\": \"arbitrary message\", \"error\": \"error property\" }";
  private static final String messageWithException =
      "{ \"message\": \"arbitrary message\", \"exception\": \"exception property\" }";
  private static final String messageWithErrors =
      "{ \"message\": \"arbitrary message\", \"errors\": [\"error one\", \"error two\"] }";
  private static final String messageWithErrorAndErrors =
      "{ \"message\": \"arbitrary message\", \"error\": \"error property\", \"errors\": [\"error one\", \"error two\"] }";
  private static final String messageWithErrorAndErrorsAndMessages =
      "{ \"message\": \"arbitrary message\", \"error\": \"error property\", \"errors\": [\"error one\", \"error two\"], \"messages\": [\"message one\", \"message two\"] }";

  private SpinnakerServerExceptionHandler spinnakerServerExceptionHandler =
      new SpinnakerServerExceptionHandler();
  private DefaultExceptionHandler defaultExceptionHandler = new DefaultExceptionHandler();

  /** Put SpinnakerServerExceptionHandler first since its bean is marked as highest precedence */
  private List<ExceptionHandler> exceptionHandlers =
      List.of(spinnakerServerExceptionHandler, defaultExceptionHandler);

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

    when(executionRepository.retrieve(
            PIPELINE, runTaskMessage.getExecutionId(), ReadReplicaRequirement.UP_TO_DATE))
        .thenReturn(pipeline);
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
            .put("method", "GET")
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
    // irrespective of the exception details, http code 500 is non-retryable
    return Stream.of(
        makeSpinnakerHttpException(500, message),
        makeSpinnakerHttpException(500, messageWithError),
        makeSpinnakerHttpException(500, messageWithException),
        makeSpinnakerHttpException(500, messageWithErrors),
        makeSpinnakerHttpException(500, messageWithErrorAndErrors),
        makeSpinnakerHttpException(500, messageWithErrorAndErrorsAndMessages));
  }

  @Test
  void taskThrowsSpinnakerServerExceptionRetryable() {
    // given an arbitrary SpinnakerServerException that SpinnakerServerExceptionHandler considers
    // retryable
    SpinnakerServerException spinnakerServerException = makeSpinnakerHttpException(503, message);
    doThrow(spinnakerServerException).when(dummyTask).execute(any());

    // when
    runTaskHandler.handle(runTaskMessage);

    // verify that the exception was retried
    verify(queue).push(eq(runTaskMessage), any());

    // On retry, expect no exception info in the context
    assertThat(stageExecution.getContext().get("exception")).isNull();
  }

  @Test
  void taskThrowsSpinnakerHttpExceptionNonJsonResponse() {
    // given an arbitrary SpinnakerHttpException that SpinnakerServerExceptionHandler doesn't
    // consider retryable
    SpinnakerHttpException spinnakerHttpException =
        makeSpinnakerHttpException(500, "non-json response");
    doThrow(spinnakerHttpException).when(dummyTask).execute(any());

    // when
    runTaskHandler.handle(runTaskMessage);

    // verify that the exception wasn't retried
    verify(queue, never()).push(eq(runTaskMessage), any());

    // verify that exception handling has populated the stage context as
    // expected.  There's no implementation for this yet in
    // SpinnakerServerExceptionHandler since SpinnakerHttpException can't handle
    // non-json responses yet.  The expected details here match the behavior of
    // RetrofitExceptionHandler, when it existed.
    Map<String, Object> responseDetails =
        Map.of(
            "error", spinnakerHttpException.getMessage(),
            "errors", List.of(),
            "method", "GET",
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

  public static SpinnakerHttpException makeSpinnakerHttpException(int status, String message) {

    String url = "https://some-url";
    MediaType mediaType = MediaType.parse("application/json");
    ResponseBody body = ResponseBody.create(mediaType, message);

    Request request = new Request.Builder().url(url).build();

    okhttp3.Response rawResponse =
        new okhttp3.Response.Builder()
            .body(body)
            .code(status)
            .message(message)
            .protocol(Protocol.HTTP_1_1)
            .request(request)
            .build();

    // Simulating Retrofit Response.error
    retrofit2.Response<Object> response = retrofit2.Response.error(body, rawResponse);

    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build();

    return new SpinnakerHttpException(response, retrofit);
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
