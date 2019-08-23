/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.compute.ComputeRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Queues several {@link GoogleComputeGetRequest requests} into a single batched request and returns
 * the first found object, if any.
 */
@Slf4j
public final class GetFirstBatchComputeRequest<
    RequestT extends ComputeRequest<ResponseT>, ResponseT> {

  private final BatchComputeRequest<RequestT, ResponseT> delegate;
  private final Callback<ResponseT> callback;

  private GetFirstBatchComputeRequest(BatchComputeRequest<RequestT, ResponseT> delegate) {
    this.delegate = delegate;
    this.callback = new Callback<>();
  }

  public static <RequestT extends ComputeRequest<ResponseT>, ResponseT>
      GetFirstBatchComputeRequest<RequestT, ResponseT> create(
          BatchComputeRequest<RequestT, ResponseT> batchRequest) {
    return new GetFirstBatchComputeRequest<>(batchRequest);
  }

  public void queue(GoogleComputeGetRequest<RequestT, ResponseT> request) {
    delegate.queue(request, callback);
  }

  public Optional<ResponseT> execute(String batchContext) throws IOException {

    delegate.execute(batchContext);

    if (callback.response != null) {
      if (!callback.exceptions.isEmpty()) {
        logIgnoredExceptions();
      }
      return Optional.of(callback.response);
    }

    if (!callback.exceptions.isEmpty()) {
      HttpResponseException e = callback.exceptions.get(0);
      callback.exceptions.subList(1, callback.exceptions.size()).forEach(e::addSuppressed);
      throw e;
    }

    return Optional.empty();
  }

  private void logIgnoredExceptions() {
    callback.exceptions.forEach(
        e ->
            log.warn(
                "Error in batch response, but ignoring because a valid response was found", e));
  }

  private static class Callback<T> extends JsonBatchCallback<T> {

    T response;
    List<HttpResponseException> exceptions = new ArrayList<>();

    @Override
    public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
      if (e.getCode() != 404) {
        exceptions.add(
            new HttpResponseException.Builder(e.getCode(), e.getMessage(), responseHeaders)
                .setMessage(e.getMessage())
                .build());
      }
    }

    @Override
    public synchronized void onSuccess(T response, HttpHeaders responseHeaders) {
      if (this.response == null) {
        this.response = response;
      }
    }
  }
}
