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
 *
 */

package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.InstanceGroupManagerList;
import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import java.util.Optional;

public final class ZoneInstanceGroupManagers {

  private final Compute.InstanceGroupManagers computeApi;
  private final GoogleNamedAccountCredentials credentials;
  private final ZonalGoogleComputeRequestFactory requestFactory;

  ZoneInstanceGroupManagers(
      GoogleNamedAccountCredentials credentials,
      GoogleOperationPoller operationPoller,
      Registry registry) {
    this.computeApi = credentials.getCompute().instanceGroupManagers();
    this.credentials = credentials;
    this.requestFactory =
        new ZonalGoogleComputeRequestFactory(
            "instanceGroupManagers", credentials, operationPoller, registry);
  }

  public GoogleComputeGetRequest<Compute.InstanceGroupManagers.Get, InstanceGroupManager> get(
      String zone, String name) throws IOException {
    return requestFactory.wrapGetRequest(
        computeApi.get(credentials.getProject(), zone, name), "get", zone);
  }

  public PaginatedComputeRequest<Compute.InstanceGroupManagers.List, InstanceGroupManager> list(
      String zone) {
    return new PaginatedComputeRequestImpl<>(
        pageToken ->
            requestFactory.wrapRequest(
                computeApi.list(credentials.getProject(), zone).setPageToken(pageToken),
                "list",
                zone),
        InstanceGroupManagerList::getNextPageToken,
        response -> Optional.ofNullable(response.getItems()).orElseGet(ImmutableList::of));
  }
}
