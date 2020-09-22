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

import static com.google.common.collect.Lists.partition;

import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.util.Throwables;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeRequest;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.netflix.spectator.api.Registry;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.http.client.HttpResponseException;

final class BatchComputeRequestImpl<RequestT extends ComputeRequest<ResponseT>, ResponseT>
    implements BatchComputeRequest<RequestT, ResponseT> {

  // Platform-specified max to not overwhelm batch backends.
  @VisibleForTesting static final int MAX_BATCH_SIZE = 100;
  private static final Duration CONNECT_TIMEOUT = Duration.ofMinutes(2);
  private static final Duration READ_TIMEOUT = Duration.ofMinutes(2);

  private final Compute compute;
  private final Registry registry;
  private final String userAgent;
  private final ListeningExecutorService executor;
  private final List<QueuedRequest<RequestT, ResponseT>> queuedRequests;

  BatchComputeRequestImpl(
      Compute compute, Registry registry, String userAgent, ListeningExecutorService executor) {
    this.compute = compute;
    this.registry = registry;
    this.userAgent = userAgent;
    this.executor = executor;
    this.queuedRequests = new ArrayList<>();
  }

  @Override
  public void queue(
      GoogleComputeRequest<RequestT, ResponseT> request, JsonBatchCallback<ResponseT> callback) {
    queuedRequests.add(new QueuedRequest<>(request.getRequest(), callback));
  }

  @Override
  public void execute(String batchContext) throws IOException {
    if (queuedRequests.size() == 0) {
      return;
    }

    List<List<QueuedRequest<RequestT, ResponseT>>> requestPartitions =
        partition(queuedRequests, MAX_BATCH_SIZE);
    List<BatchRequest> queuedBatches = createBatchRequests(requestPartitions);

    var statusCode = "500";
    String success = "false";
    long start = registry.clock().monotonicTime();
    try {
      executeBatches(queuedBatches);
      success = "true";
      statusCode = "200";
    } catch (HttpResponseException e) {
      statusCode = Integer.toString(e.getStatusCode());
      throw e;
    } finally {
      long nanos = registry.clock().monotonicTime() - start;
      String status = statusCode.charAt(0) + "xx";
      Map<String, String> tags =
          ImmutableMap.of(
              "context", batchContext,
              "success", success,
              "status", status,
              "statusCode", statusCode);
      registry
          .timer(registry.createId("google.batchExecute", tags))
          .record(Duration.ofNanos(nanos));
      registry
          .counter(registry.createId("google.batchSize", tags))
          .increment(queuedRequests.size());
    }
  }

  private void executeBatches(List<BatchRequest> queuedBatches) throws IOException {
    if (queuedBatches.size() == 1) {
      queuedBatches.get(0).execute();
      return;
    }

    List<ListenableFuture<Void>> futures =
        queuedBatches.stream()
            .map(
                batchRequest ->
                    executor.submit(
                        (Callable<Void>)
                            () -> {
                              batchRequest.execute();
                              return null;
                            }))
            .collect(Collectors.toList());
    try {
      new FailFastFuture(futures, executor).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InterruptedIOException();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      Throwables.propagateIfPossible(cause, IOException.class);
      throw new RuntimeException(cause);
    }
  }

  private List<BatchRequest> createBatchRequests(
      List<List<QueuedRequest<RequestT, ResponseT>>> requestPartitions) throws IOException {

    List<BatchRequest> queuedBatches = new ArrayList<>();

    try {
      requestPartitions.forEach(
          partition -> {
            BatchRequest batch = newBatch();
            partition.forEach(
                qr -> wrapIOException(() -> qr.getRequest().queue(batch, qr.getCallback())));
            queuedBatches.add(batch);
          });
      return queuedBatches;
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }

  private BatchRequest newBatch() {
    return compute.batch(
        request -> {
          request.getHeaders().setUserAgent(userAgent);
          request.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
          request.setReadTimeout((int) READ_TIMEOUT.toMillis());
        });
  }

  @FunctionalInterface
  private interface IoExceptionRunnable {
    void run() throws IOException;
  }

  private static void wrapIOException(IoExceptionRunnable runnable) {
    try {
      runnable.run();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Value
  @AllArgsConstructor
  private static class QueuedRequest<RequestT extends ComputeRequest<ResponseT>, ResponseT> {
    private RequestT request;
    private JsonBatchCallback<ResponseT> callback;
  }

  private static class FailFastFuture extends AbstractFuture<Void> {

    private final AtomicInteger remainingFutures;

    FailFastFuture(List<ListenableFuture<Void>> futures, ExecutorService executor) {
      remainingFutures = new AtomicInteger(futures.size());
      for (ListenableFuture<Void> future : futures) {
        Futures.addCallback(
            future,
            new FutureCallback<Object>() {
              @Override
              public void onSuccess(Object result) {
                if (remainingFutures.decrementAndGet() == 0) {
                  set(null);
                }
              }

              @Override
              public void onFailure(Throwable t) {
                setException(t);
              }
            },
            executor);
      }
    }
  }
}
