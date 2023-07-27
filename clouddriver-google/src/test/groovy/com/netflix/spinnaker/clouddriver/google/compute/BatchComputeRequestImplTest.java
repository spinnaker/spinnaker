/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.Compute.Images.Get;
import com.google.api.services.compute.model.Image;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Timer;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.http.client.HttpResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BatchComputeRequestImplTest {

  private static final String USER_AGENT = "spinnaker-test";
  private static final String MIME_BOUNDARY = "batch_foobarbaz";
  private static final String MIME_PART_START = "--batch_foobarbaz\n";
  private static final String MIME_END = "--batch_foobarbaz--\n";
  private static final String BATCH_CONTENT_TYPE = "multipart/mixed; boundary=" + MIME_BOUNDARY;

  private Registry registry;

  @BeforeEach
  public void setUp() {
    registry = new DefaultRegistry();
  }

  @Test
  public void exitsEarlyWithNoRequests() throws IOException {

    Compute compute = computeWithResponses();

    BatchComputeRequest<Get, Image> batchRequest =
        new BatchComputeRequestImpl<>(
            compute, registry, USER_AGENT, MoreExecutors.newDirectExecutorService());

    batchRequest.execute("batchContext");
  }

  @Test
  public void singleRequest() throws IOException {

    Compute compute = computeWithResponses(() -> successBatchResponse(1));

    BatchComputeRequest<Get, Image> batchRequest =
        new BatchComputeRequestImpl<>(
            compute, registry, USER_AGENT, MoreExecutors.newDirectExecutorService());

    CountResponses responses = new CountResponses();
    batchRequest.queue(request(compute), responses);

    batchRequest.execute("batchContext");

    assertThat(responses.successes).hasValue(1);
    assertThat(responses.failures).hasValue(0);
  }

  @Test
  public void singleBatch() throws IOException {

    Compute compute =
        computeWithResponses(() -> successBatchResponse(BatchComputeRequestImpl.MAX_BATCH_SIZE));

    BatchComputeRequest<Get, Image> batchRequest =
        new BatchComputeRequestImpl<>(
            compute, registry, USER_AGENT, MoreExecutors.newDirectExecutorService());

    CountResponses responses = new CountResponses();
    for (int i = 0; i < BatchComputeRequestImpl.MAX_BATCH_SIZE; ++i) {
      batchRequest.queue(request(compute), responses);
    }

    batchRequest.execute("batchContext");

    assertThat(responses.successes).hasValue(BatchComputeRequestImpl.MAX_BATCH_SIZE);
    assertThat(responses.failures).hasValue(0);
  }

  @Test
  public void multipleBatches() throws IOException {

    Compute compute =
        computeWithResponses(
            () -> successBatchResponse(BatchComputeRequestImpl.MAX_BATCH_SIZE),
            () -> successBatchResponse(BatchComputeRequestImpl.MAX_BATCH_SIZE),
            () -> successBatchResponse(37));

    BatchComputeRequest<Get, Image> batchRequest =
        new BatchComputeRequestImpl<>(
            compute, registry, USER_AGENT, MoreExecutors.newDirectExecutorService());

    CountResponses responses = new CountResponses();
    for (int i = 0; i < BatchComputeRequestImpl.MAX_BATCH_SIZE * 2 + 37; ++i) {
      batchRequest.queue(request(compute), responses);
    }

    batchRequest.execute("batchContext");

    assertThat(responses.successes).hasValue(BatchComputeRequestImpl.MAX_BATCH_SIZE * 2 + 37);
    assertThat(responses.failures).hasValue(0);
  }

  @Test
  public void handlesErrors() throws IOException {

    StringBuilder responseContent = new StringBuilder();
    appendSuccessResponse(responseContent);
    appendSuccessResponse(responseContent);
    appendSuccessResponse(responseContent);
    appendFailureResponse(responseContent); // FAILURE!
    appendSuccessResponse(responseContent);
    responseContent.append(MIME_END);

    Compute compute = computeWithResponses(() -> batchResponse(responseContent.toString()));

    BatchComputeRequest<Get, Image> batchRequest =
        new BatchComputeRequestImpl<>(
            compute, registry, USER_AGENT, MoreExecutors.newDirectExecutorService());

    CountResponses responses = new CountResponses();
    for (int i = 0; i < 5; ++i) {
      batchRequest.queue(request(compute), responses);
    }

    batchRequest.execute("batchContext");

    assertThat(responses.successes).hasValue(4);
    assertThat(responses.failures).hasValue(1);
  }

  @Test
  public void propagatesFirstException() throws IOException {

    Compute compute =
        computeWithResponses(
            () -> successBatchResponse(BatchComputeRequestImpl.MAX_BATCH_SIZE),
            () -> {
              throw new IOException("first exception");
            },
            () -> {
              throw new IOException("second exception");
            },
            () -> {
              try {
                Thread.sleep(Long.MAX_VALUE);
                throw new AssertionError("slept forever");
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            });

    BatchComputeRequest<Get, Image> batchRequest =
        new BatchComputeRequestImpl<>(
            compute, registry, USER_AGENT, MoreExecutors.newDirectExecutorService());

    CountResponses responses = new CountResponses();
    for (int i = 0; i < BatchComputeRequestImpl.MAX_BATCH_SIZE * 3; ++i) {
      batchRequest.queue(request(compute), responses);
    }

    Throwable throwable = catchThrowable(() -> batchRequest.execute("batchContext"));

    assertThat(throwable).isInstanceOf(IOException.class).hasMessage("first exception");
  }

  @Test
  public void successMetrics() throws IOException {

    Compute compute =
        computeWithResponses(
            () -> successBatchResponse(BatchComputeRequestImpl.MAX_BATCH_SIZE),
            () -> successBatchResponse(BatchComputeRequestImpl.MAX_BATCH_SIZE),
            () -> successBatchResponse(37));

    BatchComputeRequest<Get, Image> batchRequest =
        new BatchComputeRequestImpl<>(
            compute, registry, USER_AGENT, MoreExecutors.newDirectExecutorService());

    CountResponses responses = new CountResponses();
    for (int i = 0; i < BatchComputeRequestImpl.MAX_BATCH_SIZE * 2 + 37; ++i) {
      batchRequest.queue(request(compute), responses);
    }

    batchRequest.execute("batchContext");

    assertThat(registry.timers()).hasSize(1);
    Timer timer = registry.timers().findFirst().orElseThrow(AssertionError::new);
    assertThat(timer.id().name()).isEqualTo("google.batchExecute");
    assertThat(timer.id().tags())
        .contains(
            tag("context", "batchContext"),
            tag("success", "true"),
            tag("status", "2xx"),
            tag("statusCode", "200"));

    assertThat(registry.counters()).hasSize(1);
    Counter counter = registry.counters().findFirst().orElseThrow(AssertionError::new);
    assertThat(counter.id().name()).isEqualTo("google.batchSize");
    assertThat(counter.id().tags())
        .contains(
            tag("context", "batchContext"),
            tag("success", "true"),
            tag("status", "2xx"),
            tag("statusCode", "200"));
    assertThat(counter.actualCount()).isEqualTo(BatchComputeRequestImpl.MAX_BATCH_SIZE * 2 + 37);
  }

  @Test
  public void errorMetrics() throws IOException {

    Compute compute =
        computeWithResponses(
            () -> {
              throw new IOException("uh oh");
            });

    BatchComputeRequest<Get, Image> batchRequest =
        new BatchComputeRequestImpl<>(
            compute, registry, USER_AGENT, MoreExecutors.newDirectExecutorService());

    CountResponses responses = new CountResponses();
    for (int i = 0; i < 55; ++i) {
      batchRequest.queue(request(compute), responses);
    }

    assertThatIOException().isThrownBy(() -> batchRequest.execute("batchContext"));

    assertThat(registry.timers()).hasSize(1);
    Timer timer = registry.timers().findFirst().orElseThrow(AssertionError::new);
    assertThat(timer.id().name()).isEqualTo("google.batchExecute");
    assertThat(timer.id().tags())
        .contains(
            tag("context", "batchContext"),
            tag("success", "false"),
            tag("status", "5xx"),
            tag("statusCode", "500"));

    assertThat(registry.counters()).hasSize(1);
    Counter counter = registry.counters().findFirst().orElseThrow(AssertionError::new);
    assertThat(counter.id().name()).isEqualTo("google.batchSize");
    assertThat(counter.id().tags())
        .contains(
            tag("context", "batchContext"),
            tag("success", "false"),
            tag("status", "5xx"),
            tag("statusCode", "500"));
    assertThat(counter.actualCount()).isEqualTo(55);
  }

  @Test
  public void httpErrorMetrics() throws IOException {

    Compute compute =
        computeWithResponses(
            () -> {
              throw new HttpResponseException(404, "uh oh");
            });

    BatchComputeRequest<Get, Image> batchRequest =
        new BatchComputeRequestImpl<>(
            compute, registry, USER_AGENT, MoreExecutors.newDirectExecutorService());

    CountResponses responses = new CountResponses();
    for (int i = 0; i < 55; ++i) {
      batchRequest.queue(request(compute), responses);
    }

    assertThatIOException().isThrownBy(() -> batchRequest.execute("batchContext"));

    assertThat(registry.timers()).hasSize(1);
    Timer timer = registry.timers().findFirst().orElseThrow(AssertionError::new);
    assertThat(timer.id().name()).isEqualTo("google.batchExecute");
    assertThat(timer.id().tags())
        .contains(
            tag("context", "batchContext"),
            tag("success", "false"),
            tag("status", "4xx"),
            tag("statusCode", "404"));

    assertThat(registry.counters()).hasSize(1);
    Counter counter = registry.counters().findFirst().orElseThrow(AssertionError::new);
    assertThat(counter.id().name()).isEqualTo("google.batchSize");
    assertThat(counter.id().tags())
        .contains(
            tag("context", "batchContext"),
            tag("success", "false"),
            tag("status", "4xx"),
            tag("statusCode", "404"));
    assertThat(counter.actualCount()).isEqualTo(55);
  }

  private static GoogleComputeRequest<Compute.Images.Get, Image> request(Compute compute)
      throws IOException {
    return new GoogleComputeRequestImpl<>(
        compute.images().get("project", "image-name"),
        new DefaultRegistry(),
        /* metricName= */ "google.api",
        /* tags= */ ImmutableMap.of());
  }

  @FunctionalInterface
  private interface ResponseSupplier {

    LowLevelHttpResponse getResponse() throws IOException;
  }

  private static Compute computeWithResponses(ResponseSupplier... responses) {
    return new Compute(
        responses(responses),
        JacksonFactory.getDefaultInstance(),
        /* httpRequestInitializer= */ null);
  }

  private static HttpTransport responses(ResponseSupplier... responses) {
    return new HttpTransport() {
      private AtomicInteger requests = new AtomicInteger(0);

      @Override
      protected LowLevelHttpRequest buildRequest(String method, String url) {
        int requestNum = requests.getAndIncrement();
        ResponseSupplier response;
        if (requestNum < responses.length) {
          response = responses[requestNum];
        } else {
          response =
              () ->
                  new MockLowLevelHttpResponse()
                      .setStatusCode(500)
                      .setContent("Sent more requests than expected.");
        }
        return new LowLevelHttpRequest() {
          @Override
          public void addHeader(String name, String value) {}

          @Override
          public LowLevelHttpResponse execute() throws IOException {
            return response.getResponse();
          }
        };
      }
    };
  }

  private static MockLowLevelHttpResponse successBatchResponse(int responses) {
    return batchResponse(successBatchResponseContent(responses));
  }

  private static MockLowLevelHttpResponse batchResponse(String content) {
    return new MockLowLevelHttpResponse()
        .setStatusCode(200)
        .addHeader("Content-Type", BATCH_CONTENT_TYPE)
        .setContent(content);
  }

  private static String successBatchResponseContent(int responses) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < responses; ++i) {
      appendSuccessResponse(sb);
    }
    return sb.append(MIME_END).toString();
  }

  private static void appendSuccessResponse(StringBuilder sb) {
    sb.append(MIME_PART_START)
        .append("Content-Type: application/http\n")
        .append('\n')
        .append("HTTP/1.1 200 OK\n")
        .append("Content-Type: application/json\n")
        .append("\n")
        .append("{\"name\":\"foobar\"}\n\n");
  }

  private static void appendFailureResponse(StringBuilder sb) {
    sb.append(MIME_PART_START)
        .append("Content-Type: application/http\n")
        .append('\n')
        .append("HTTP/1.1 500 Really Bad Error\n")
        .append("Content-Type: application/json\n")
        .append("\n")
        .append("{}\n\n");
  }

  private static class CountResponses extends JsonBatchCallback<Image> {
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger failures = new AtomicInteger();

    @Override
    public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
      failures.incrementAndGet();
    }

    @Override
    public void onSuccess(Image image, HttpHeaders responseHeaders) {
      successes.incrementAndGet();
    }
  }

  private static Tag tag(String key, String value) {
    return new BasicTag(key, value);
  }
}
