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

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Autoscaler;
import com.google.api.services.compute.model.AutoscalerList;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;

public final class ZoneAutoscalers {

  private final Compute.Autoscalers computeApi;
  private final GoogleNamedAccountCredentials credentials;
  private final ZonalGoogleComputeRequestFactory requestFactory;

  ZoneAutoscalers(
      GoogleNamedAccountCredentials credentials,
      GoogleOperationPoller operationPoller,
      Registry registry) {
    this.computeApi = credentials.getCompute().autoscalers();
    this.credentials = credentials;
    this.requestFactory =
        new ZonalGoogleComputeRequestFactory("autoscalers", credentials, operationPoller, registry);
  }

  public GoogleComputeGetRequest<Compute.Autoscalers.Get, Autoscaler> get(String zone, String name)
      throws IOException {
    return requestFactory.wrapGetRequest(
        credentials.getCompute().autoscalers().get(credentials.getProject(), zone, name),
        "get",
        zone);
  }

  public PaginatedComputeRequest<Compute.Autoscalers.List, Autoscaler> list(String zone) {
    return new PaginatedComputeRequestImpl<>(
        pageToken ->
            requestFactory.wrapRequest(
                computeApi.list(credentials.getProject(), zone).setPageToken(pageToken),
                "list",
                zone),
        AutoscalerList::getNextPageToken,
        AutoscalerList::getItems);
  }
}
