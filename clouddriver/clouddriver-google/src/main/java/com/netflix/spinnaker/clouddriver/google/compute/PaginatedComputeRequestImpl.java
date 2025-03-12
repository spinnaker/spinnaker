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

import static com.google.api.client.util.Strings.isNullOrEmpty;

import com.google.api.services.compute.ComputeRequest;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;

final class PaginatedComputeRequestImpl<
        RequestT extends ComputeRequest<ResponseT>, ResponseT, ItemT>
    implements PaginatedComputeRequest<RequestT, ItemT> {

  @FunctionalInterface
  interface RequestGenerator<RequestT extends ComputeRequest<ResponseT>, ResponseT> {
    GoogleComputeRequest<RequestT, ResponseT> createRequest(String pageToken) throws IOException;
  }

  @FunctionalInterface
  interface NextPageTokenRetriever<ResponseT> {
    String getNextPageToken(ResponseT response);
  }

  @FunctionalInterface
  interface ItemRetriever<ResponseT, ItemT> {
    @Nullable
    List<ItemT> getItems(ResponseT response);
  }

  final RequestGenerator<RequestT, ResponseT> requestGenerator;
  final NextPageTokenRetriever<ResponseT> nextPageTokenRetriever;
  final ItemRetriever<ResponseT, ItemT> itemRetriever;
  final Consumer<RequestT> requestModifier;

  PaginatedComputeRequestImpl(
      RequestGenerator<RequestT, ResponseT> requestGenerator,
      NextPageTokenRetriever<ResponseT> nextPageTokenRetriever,
      ItemRetriever<ResponseT, ItemT> itemRetriever) {
    this(requestGenerator, nextPageTokenRetriever, itemRetriever, request -> {});
  }

  private PaginatedComputeRequestImpl(
      RequestGenerator<RequestT, ResponseT> requestGenerator,
      NextPageTokenRetriever<ResponseT> nextPageTokenRetriever,
      ItemRetriever<ResponseT, ItemT> itemRetriever,
      Consumer<RequestT> requestModifier) {
    this.requestGenerator = requestGenerator;
    this.nextPageTokenRetriever = nextPageTokenRetriever;
    this.itemRetriever = itemRetriever;
    this.requestModifier = requestModifier;
  }

  @Override
  public void execute(Consumer<List<ItemT>> pageConsumer) throws IOException {

    String pageToken = "";
    do {
      GoogleComputeRequest<RequestT, ResponseT> request = requestGenerator.createRequest(pageToken);
      requestModifier.accept(request.getRequest());
      ResponseT response = request.execute();
      Optional.ofNullable(itemRetriever.getItems(response)).ifPresent(pageConsumer);
      pageToken = nextPageTokenRetriever.getNextPageToken(response);
    } while (!isNullOrEmpty(pageToken));
  }

  @Override
  public PaginatedComputeRequest<RequestT, ItemT> withRequestModifier(
      Consumer<RequestT> requestModifier) {
    return new PaginatedComputeRequestImpl<>(
        requestGenerator, nextPageTokenRetriever, itemRetriever, requestModifier);
  }
}
