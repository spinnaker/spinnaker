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

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.compute.ComputeRequest;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

final class BatchPaginatedComputeRequestImpl<
        ComputeRequestT extends ComputeRequest<ResponseT>, ResponseT, ItemT>
    implements BatchPaginatedComputeRequest<ComputeRequestT, ItemT> {

  private final Supplier<BatchComputeRequest<ComputeRequestT, ResponseT>> batchRequestSupplier;
  private final Map<PaginatedComputeRequestImpl<ComputeRequestT, ResponseT, ItemT>, String>
      nextPageTokens = new HashMap<>();
  private IOException exception;

  BatchPaginatedComputeRequestImpl(
      Supplier<BatchComputeRequest<ComputeRequestT, ResponseT>> batchRequestSupplier) {
    this.batchRequestSupplier = batchRequestSupplier;
  }

  @Override
  public void queue(PaginatedComputeRequest<ComputeRequestT, ItemT> request) {
    nextPageTokens.put(
        (PaginatedComputeRequestImpl<ComputeRequestT, ResponseT, ItemT>) request, "");
  }

  @Override
  public ImmutableSet<ItemT> execute(String batchContext) throws IOException {
    ImmutableSet.Builder<ItemT> results = ImmutableSet.builder();

    while (!nextPageTokens.isEmpty() && exception == null) {
      BatchComputeRequest<ComputeRequestT, ResponseT> pageRequest = batchRequestSupplier.get();
      for (Map.Entry<PaginatedComputeRequestImpl<ComputeRequestT, ResponseT, ItemT>, String> entry :
          nextPageTokens.entrySet()) {
        GoogleComputeRequest<ComputeRequestT, ResponseT> request =
            entry.getKey().requestGenerator.createRequest(entry.getValue());
        entry.getKey().requestModifier.accept(request.getRequest());
        pageRequest.queue(request, new PageCallback(entry.getKey(), results));
      }
      pageRequest.execute(batchContext);
    }

    if (exception != null) {
      throw exception;
    }

    return results.build();
  }

  private class PageCallback extends JsonBatchCallback<ResponseT> {

    private final PaginatedComputeRequestImpl<ComputeRequestT, ResponseT, ItemT> request;
    private final ImmutableSet.Builder<ItemT> results;

    private PageCallback(
        PaginatedComputeRequestImpl<ComputeRequestT, ResponseT, ItemT> request,
        ImmutableSet.Builder<ItemT> results) {
      this.request = request;
      this.results = results;
    }

    @Override
    public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
      nextPageTokens.remove(request);
      HttpResponseException newException =
          new HttpResponseException.Builder(e.getCode(), e.getMessage(), responseHeaders)
              .setMessage(e.getMessage())
              .build();
      if (exception == null) {
        exception = newException;
      } else {
        exception.addSuppressed(newException);
      }
    }

    @Override
    public void onSuccess(ResponseT response, HttpHeaders responseHeaders) {
      Optional.ofNullable(request.itemRetriever.getItems(response)).ifPresent(results::addAll);
      String nextPageToken = request.nextPageTokenRetriever.getNextPageToken(response);
      if (isNullOrEmpty(nextPageToken)) {
        nextPageTokens.remove(request);
      } else {
        nextPageTokens.put(request, nextPageToken);
      }
    }
  }
}
