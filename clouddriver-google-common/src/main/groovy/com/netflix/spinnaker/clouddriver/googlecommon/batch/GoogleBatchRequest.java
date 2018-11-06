/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.googlecommon.batch;

import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeRequest;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for sending batch requests to GCE.
 */
@Slf4j
public class GoogleBatchRequest {

  private static final int MAX_BATCH_SIZE = 100; // Platform specified max to not overwhelm batch backends.
  private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = (int) TimeUnit.MINUTES.toMillis(2);
  private static final int DEFAULT_READ_TIMEOUT_MILLIS = (int) TimeUnit.MINUTES.toMillis(2);

  private List<QueuedRequest> queuedRequests;
  private String clouddriverUserAgentApplicationName;
  private Compute compute;

  public GoogleBatchRequest(Compute compute, String clouddriverUserAgentApplicationName) {
    this.compute = compute;
    this.clouddriverUserAgentApplicationName = clouddriverUserAgentApplicationName;
    this.queuedRequests = new ArrayList<>();
  }

  public void execute() {
    if (queuedRequests.size() == 0) {
      log.debug("No requests queued in batch, exiting.");
      return;
    }

    List<BatchRequest> queuedBatches = new ArrayList<>();
    List<List<QueuedRequest>> requestPartitions = Lists.partition(queuedRequests, MAX_BATCH_SIZE);
    requestPartitions.forEach(requestPart -> {
      BatchRequest newBatch = newBatch();
      requestPart.forEach(qr -> {
        try {
          qr.getRequest().queue(newBatch, qr.getCallback());
        } catch (Exception ioe) {
          log.error("Queueing request {} in batch failed.", qr, ioe);
        }
      });
      queuedBatches.add(newBatch);
    });

    ExecutorService threadPool = new ForkJoinPool(10);
    try {
      threadPool.submit(() -> queuedBatches.stream().parallel().forEach(this::executeInternalBatch)).get();
    } catch (Exception e) {
      log.error("Executing queued batches failed.", e);
    }
    threadPool.shutdown();
  }

  private void executeInternalBatch(BatchRequest b) {
    try {
      b.execute();
    } catch (Exception e) {
      log.error("Executing batch {} failed.", b, e);
    }
  }

  private BatchRequest newBatch() {
    return compute.batch(
      new HttpRequestInitializer() {
        @Override
        public void initialize(HttpRequest request) {
          request.getHeaders().setUserAgent(clouddriverUserAgentApplicationName);
          request.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MILLIS);
          request.setReadTimeout(DEFAULT_READ_TIMEOUT_MILLIS);
        }
      }
    );
  }

  public void queue(ComputeRequest request, JsonBatchCallback callback) {
    queuedRequests.add(new QueuedRequest(request, callback));
  }

  public Integer size() {
    return queuedRequests.size();
  }

  @Data
  @AllArgsConstructor
  private static class QueuedRequest {
    private ComputeRequest request;
    private JsonBatchCallback callback;
  }
}
