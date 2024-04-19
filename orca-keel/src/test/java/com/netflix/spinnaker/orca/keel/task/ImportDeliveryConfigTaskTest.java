/*
 * Copyright 2024 OpsMx, Inc.
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

package com.netflix.spinnaker.orca.keel.task;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler;
import com.netflix.spinnaker.okhttp.SpinnakerRequestInterceptor;
import com.netflix.spinnaker.orca.KeelService;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.config.KeelConfiguration;
import com.netflix.spinnaker.orca.igor.ScmService;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

/*
 *  @see com.netflix.spinnaker.orca.keel.ImportDeliveryConfigTaskTests.kt already covers up few tests related to @see ImportDeliveryConfigTask.
 * This new java class is Introduced to improvise the API testing with the help of wiremock.
 * Test using wiremock would help in smooth migration to retrofit2.x along with the addition of {@link SpinnakerRetrofitErrorHandler}.
 * */
public class ImportDeliveryConfigTaskTest {

  private static KeelService keelService;
  private static ScmService scmService;

  private static final ObjectMapper objectMapper = new KeelConfiguration().keelObjectMapper();

  private StageExecution stage;

  private ImportDeliveryConfigTask importDeliveryConfigTask;

  private Map<String, Object> contextMap = new LinkedHashMap<>();

  private static final int keelPort = 8087;

  @BeforeAll
  static void setupOnce(WireMockRuntimeInfo wmRuntimeInfo) {
    OkClient okClient = new OkClient();
    RestAdapter.LogLevel retrofitLogLevel = RestAdapter.LogLevel.NONE;

    keelService =
        new RestAdapter.Builder()
            .setRequestInterceptor(new SpinnakerRequestInterceptor(true))
            .setEndpoint(wmRuntimeInfo.getHttpBaseUrl())
            .setClient(okClient)
            .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
            .setLogLevel(retrofitLogLevel)
            .setConverter(new JacksonConverter(objectMapper))
            .build()
            .create(KeelService.class);
  }

  @BeforeEach
  public void setup() {
    scmService = mock(ScmService.class);
    importDeliveryConfigTask = new ImportDeliveryConfigTask(keelService, scmService, objectMapper);

    PipelineExecutionImpl pipeline = new PipelineExecutionImpl(PIPELINE, "keeldemo");
    contextMap.put("repoType", "stash");
    contextMap.put("projectKey", "SPKR");
    contextMap.put("repositorySlug", "keeldemo");
    contextMap.put("directory", ".");
    contextMap.put("manifest", "spinnaker.yml");
    contextMap.put("ref", "refs/heads/master");
    contextMap.put("attempt", 1);
    contextMap.put("maxRetries", 5);

    stage = new StageExecutionImpl(pipeline, ExecutionType.PIPELINE.toString(), contextMap);
  }

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().port(keelPort)).build();

  private static void simulateFault(String url, String body, HttpStatus httpStatus) {
    wireMock.givenThat(
        WireMock.post(urlPathEqualTo(url))
            .willReturn(
                aResponse()
                    .withHeaders(HttpHeaders.noHeaders())
                    .withStatus(httpStatus.value())
                    .withBody(body)));
  }

  private static void simulateFault(String url, Fault fault) {
    wireMock.givenThat(WireMock.post(urlPathEqualTo(url)).willReturn(aResponse().withFault(fault)));
  }

  /**
   * This test is a positive case which verifies if the task returns {@link
   * ImportDeliveryConfigTask.SpringHttpError} on 4xx http error. Here the error body is mocked with
   * timestamps in supported Time Units, which will be parsed to exact same timestamp in the
   * method @see {@link ImportDeliveryConfigTask#handleRetryableFailures(SpinnakerHttpException,
   * ImportDeliveryConfigTask.ImportDeliveryConfigContext)} and results in successful assertions of
   * all the fields.
   *
   * <p>The cases when the timestamp results in accurate value, are when the units in : {@link
   * ChronoUnit#MILLIS} {@link ChronoUnit#SECONDS} {@link ChronoUnit#DAYS} {@link ChronoUnit#HOURS}
   * {@link ChronoUnit#HALF_DAYS} {@link ChronoUnit#MINUTES}
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("parameterizePositiveHttpErrorScenario")
  public void verifyPositiveHttpErrorScenarios(
      HttpStatus httpStatus, ImportDeliveryConfigTask.SpringHttpError httpError)
      throws JsonProcessingException {

    TaskResult expectedTaskResult =
        TaskResult.builder(ExecutionStatus.TERMINAL).context(Map.of("error", httpError)).build();

    // simulate SpringHttpError with http error status code
    simulateFault("/delivery-configs/", objectMapper.writeValueAsString(httpError), httpStatus);

    getDeliveryConfigManifest();

    var result = importDeliveryConfigTask.execute(stage);

    verifyGetDeliveryConfigManifestInvocations();

    assertThat(expectedTaskResult).isEqualTo(result);
  }

  /**
   * This test is a negative case which verifies if the task returns {@link
   * ImportDeliveryConfigTask.SpringHttpError} on 4xx http error. Here the error body is mocked with
   * timestamp in Time Units that are unsupported, which WILL NOT be parsed to exact timestamp in
   * the method @see {@link ImportDeliveryConfigTask#handleRetryableFailures(SpinnakerHttpException,
   * ImportDeliveryConfigTask.ImportDeliveryConfigContext)} and results in will contain incorrect
   * timestamp.
   *
   * <p>The cases where the timestamp will result in incorrect value, are when the units in : {@link
   * ChronoUnit#NANOS} {@link ChronoUnit#MICROS}
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("parameterizeNegativeHttpErrorScenario")
  public void verifyNegativeHttpErrorScenarios(
      HttpStatus httpStatus, ImportDeliveryConfigTask.SpringHttpError httpError)
      throws JsonProcessingException {

    // simulate SpringHttpError with http error status code
    simulateFault("/delivery-configs/", objectMapper.writeValueAsString(httpError), httpStatus);

    getDeliveryConfigManifest();

    var result = importDeliveryConfigTask.execute(stage);
    ImportDeliveryConfigTask.SpringHttpError actualHttpErrorBody =
        (ImportDeliveryConfigTask.SpringHttpError) result.getContext().get("error");

    verifyGetDeliveryConfigManifestInvocations();

    // assert all the values in the http error body are true except the timestamp in nanos
    assertThat(actualHttpErrorBody.getStatus()).isEqualTo(httpStatus.value());
    assertThat(actualHttpErrorBody.getError()).isEqualTo(httpStatus.getReasonPhrase());
    assertThat(actualHttpErrorBody.getMessage()).isEqualTo(httpStatus.name());
    assertThat(actualHttpErrorBody.getDetails())
        .isEqualTo(Map.of("exception", "Http Error occurred"));
    assertThat(actualHttpErrorBody.getTimestamp().getEpochSecond())
        .isEqualTo(httpError.getTimestamp().getEpochSecond());
    assertThat(actualHttpErrorBody.getTimestamp().getNano())
        .isNotEqualTo(httpError.getTimestamp().getNano());
  }

  /**
   * Test to verify when the response body doesn't have timestamp. Field will be initialized with
   * default value {@link Instant#now}
   */
  @Test
  public void testSpringHttpErrorWithoutTimestamp() throws JsonProcessingException {

    var httpStatus = HttpStatus.BAD_REQUEST;

    // Map of SpringHttpError is initialized without timestamp
    var httpError = mapOfSpringHttpError(httpStatus);

    // simulate SpringHttpError with http error status code
    simulateFault("/delivery-configs/", objectMapper.writeValueAsString(httpError), httpStatus);

    getDeliveryConfigManifest();

    var result = importDeliveryConfigTask.execute(stage);
    ImportDeliveryConfigTask.SpringHttpError actualHttpErrorBody =
        (ImportDeliveryConfigTask.SpringHttpError) result.getContext().get("error");

    verifyGetDeliveryConfigManifestInvocations();

    assertThat(actualHttpErrorBody.getStatus()).isEqualTo(httpStatus.value());
    assertThat(actualHttpErrorBody.getError()).isEqualTo(httpStatus.getReasonPhrase());
    assertThat(actualHttpErrorBody.getMessage()).isEqualTo(httpStatus.name());
    assertThat(actualHttpErrorBody.getDetails())
        .isEqualTo(Map.of("exception", "Http Error occurred"));

    // The timestamp field will have the current time, and hence only the instance type is verified
    assertThat(actualHttpErrorBody.getTimestamp()).isInstanceOf(Instant.class);
  }

  @Test
  public void testTaskResultWhenErrorBodyIsEmpty() {

    String expectedMessage =
        String.format(
            "Non-retryable HTTP response %s received from downstream service: %s",
            HttpStatus.BAD_REQUEST.value(),
            "HTTP 400 "
                + wireMock.baseUrl()
                + "/delivery-configs/: Status: 400, URL: "
                + wireMock.baseUrl()
                + "/delivery-configs/, Message: Bad Request");

    var errorMap = new HashMap<>();
    errorMap.put("message", expectedMessage);

    TaskResult terminal =
        TaskResult.builder(ExecutionStatus.TERMINAL).context(Map.of("error", errorMap)).build();

    // Simulate any 4xx http error with empty error response body
    String emptyBody = "";
    simulateFault("/delivery-configs/", emptyBody, HttpStatus.BAD_REQUEST);

    getDeliveryConfigManifest();

    var result = importDeliveryConfigTask.execute(stage);

    verifyGetDeliveryConfigManifestInvocations();

    assertThat(result).isEqualTo(terminal);
  }

  @Test
  public void testTaskResultWhenHttp5xxErrorIsThrown() {

    contextMap.put("attempt", (Integer) contextMap.get("attempt") + 1);
    contextMap.put(
        "errorFromLastAttempt",
        "Retryable HTTP response 500 received from downstream service: HTTP 500 "
            + wireMock.baseUrl()
            + "/delivery-configs/: Status: 500, URL: "
            + wireMock.baseUrl()
            + "/delivery-configs/, Message: Server Error");

    TaskResult running = TaskResult.builder(ExecutionStatus.RUNNING).context(contextMap).build();

    // Simulate any 5xx http error with empty error response body
    String emptyBody = "";
    simulateFault("/delivery-configs/", emptyBody, HttpStatus.INTERNAL_SERVER_ERROR);

    getDeliveryConfigManifest();

    var result = importDeliveryConfigTask.execute(stage);

    verifyGetDeliveryConfigManifestInvocations();

    assertThat(result).isEqualTo(running);
  }

  @Test
  public void testTaskResultWhenAPIFailsWithNetworkError() {

    contextMap.put("attempt", (Integer) contextMap.get("attempt") + 1);
    contextMap.put(
        "errorFromLastAttempt",
        String.format(
            "Network error talking to downstream service, attempt 1 of %s: Connection reset: Connection reset",
            contextMap.get("maxRetries")));

    TaskResult running = TaskResult.builder(ExecutionStatus.RUNNING).context(contextMap).build();

    // Simulate network failure
    simulateFault("/delivery-configs/", Fault.CONNECTION_RESET_BY_PEER);

    getDeliveryConfigManifest();

    var result = importDeliveryConfigTask.execute(stage);

    verifyGetDeliveryConfigManifestInvocations();

    assertThat(result).isEqualTo(running);
  }

  private static Stream<Arguments> parameterizePositiveHttpErrorScenario() {

    HttpStatus httpStatus = HttpStatus.BAD_REQUEST;

    // Initialize SpringHttpError with timestamp in milliseconds and HttpStatus 400 bad request.
    var httpErrorTimestampInMillis =
        makeSpringHttpError(httpStatus, Instant.now().truncatedTo(ChronoUnit.MILLIS));

    // Initialize SpringHttpError with timestamp in seconds and HttpStatus 400 bad request.
    var httpErrorTimestampInSeconds =
        makeSpringHttpError(httpStatus, Instant.now().truncatedTo(ChronoUnit.SECONDS));

    // Initialize SpringHttpError with timestamp in minutes and HttpStatus 400 bad request.
    var httpErrorTimestampInMinutes =
        makeSpringHttpError(httpStatus, Instant.now().truncatedTo(ChronoUnit.MINUTES));

    // Initialize SpringHttpError with timestamp in hours and HttpStatus 400 bad request.
    var httpErrorTimestampInHours =
        makeSpringHttpError(httpStatus, Instant.now().truncatedTo(ChronoUnit.HOURS));

    // Initialize SpringHttpError with timestamp in days and HttpStatus 400 bad request.
    var httpErrorTimestampInDays =
        makeSpringHttpError(httpStatus, Instant.now().truncatedTo(ChronoUnit.DAYS));

    // Initialize SpringHttpError with timestamp in half days and HttpStatus 400 bad request.
    var httpErrorTimestampInHalfDays =
        makeSpringHttpError(httpStatus, Instant.now().truncatedTo(ChronoUnit.HALF_DAYS));

    return Stream.of(
        arguments(
            named("http error with timestamp in " + ChronoUnit.MILLIS.name(), httpStatus),
            httpErrorTimestampInMillis),
        arguments(
            named("http error with timestamp in " + ChronoUnit.SECONDS.name(), httpStatus),
            httpErrorTimestampInSeconds),
        arguments(
            named("http error with timestamp in " + ChronoUnit.MINUTES.name(), httpStatus),
            httpErrorTimestampInMinutes),
        arguments(
            named("http error with timestamp in " + ChronoUnit.HOURS.name(), httpStatus),
            httpErrorTimestampInHours),
        arguments(
            named("http error with timestamp in " + ChronoUnit.DAYS.name(), httpStatus),
            httpErrorTimestampInDays),
        arguments(
            named("http error with timestamp in " + ChronoUnit.HALF_DAYS.name(), httpStatus),
            httpErrorTimestampInHalfDays));
  }

  private static Stream<Arguments> parameterizeNegativeHttpErrorScenario() {

    HttpStatus httpStatus = HttpStatus.BAD_REQUEST;

    // Initialize SpringHttpError with timestamp in milliseconds and HttpStatus 400 bad request.
    var httpErrorTimestampInNanos =
        makeSpringHttpError(httpStatus, Instant.now().truncatedTo(ChronoUnit.NANOS));

    // Initialize SpringHttpError with timestamp in seconds and HttpStatus 400 bad request.
    var httpErrorTimestampInMicros =
        makeSpringHttpError(httpStatus, Instant.now().truncatedTo(ChronoUnit.MICROS));

    return Stream.of(
        arguments(
            named("http error with timestamp in " + ChronoUnit.NANOS.name(), httpStatus),
            httpErrorTimestampInNanos),
        arguments(
            named("http error with timestamp in " + ChronoUnit.MICROS.name(), httpStatus),
            httpErrorTimestampInMicros));
  }

  private void getDeliveryConfigManifest() {
    when(scmService.getDeliveryConfigManifest(
            (String) contextMap.get("repoType"),
            (String) contextMap.get("projectKey"),
            (String) contextMap.get("repositorySlug"),
            (String) contextMap.get("directory"),
            (String) contextMap.get("manifest"),
            (String) contextMap.get("ref")))
        .thenReturn(
            Map.of(
                "name",
                "keeldemo-manifest",
                "application",
                "keeldemo",
                "artifacts",
                Collections.emptySet(),
                "environments",
                Collections.emptySet()));
  }

  private static ImportDeliveryConfigTask.SpringHttpError makeSpringHttpError(
      HttpStatus httpStatus, Instant timestamp) {

    return new ImportDeliveryConfigTask.SpringHttpError(
        httpStatus.getReasonPhrase(),
        httpStatus.value(),
        httpStatus.name(),
        timestamp,
        Map.of("exception", "Http Error occurred"));
  }

  private static Map<String, Object> mapOfSpringHttpError(HttpStatus httpStatus) {

    return Map.of(
        "error",
        httpStatus.getReasonPhrase(),
        "status",
        httpStatus.value(),
        "message",
        httpStatus.name(),
        "details",
        Map.of("exception", "Http Error occurred"));
  }

  private void verifyGetDeliveryConfigManifestInvocations() {
    verify(scmService, times(1))
        .getDeliveryConfigManifest(
            (String) contextMap.get("repoType"),
            (String) contextMap.get("projectKey"),
            (String) contextMap.get("repositorySlug"),
            (String) contextMap.get("directory"),
            (String) contextMap.get("manifest"),
            (String) contextMap.get("ref"));
  }
}
