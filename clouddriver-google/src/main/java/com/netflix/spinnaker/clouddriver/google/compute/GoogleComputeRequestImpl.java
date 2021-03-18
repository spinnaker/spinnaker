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

import static java.util.stream.Collectors.toList;

import com.google.api.services.compute.ComputeRequest;
import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.GoogleExecutor;
import com.netflix.spinnaker.clouddriver.google.security.AccountForClient;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

class GoogleComputeRequestImpl<RequestT extends ComputeRequest<ResponseT>, ResponseT>
    implements GoogleComputeRequest<RequestT, ResponseT> {

  private final RequestT request;
  private final Registry registry;
  private final String metricName;
  private final Map<String, String> tags;

  GoogleComputeRequestImpl(
      RequestT request, Registry registry, String metricName, Map<String, String> tags) {
    this.request = request;
    this.registry = registry;
    this.metricName = metricName;
    this.tags = tags;
  }

  @Override
  public ResponseT execute() throws IOException {
    return timeExecute(request);
  }

  private ResponseT timeExecute(RequestT request) throws IOException {
    return GoogleExecutor.timeExecute(
        registry, request, "google.api", metricName, getTimeExecuteTags(request));
  }

  private String[] getTimeExecuteTags(RequestT request) {
    String account = AccountForClient.getAccount(request.getAbstractGoogleClient());
    return ImmutableList.<String>builder()
        .add("account")
        .add(account)
        .addAll(flattenTags())
        .build()
        .toArray(new String[] {});
  }

  private List<String> flattenTags() {
    return tags.entrySet().stream()
        .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
        .collect(toList());
  }

  @Override
  public RequestT getRequest() {
    return request;
  }
}
