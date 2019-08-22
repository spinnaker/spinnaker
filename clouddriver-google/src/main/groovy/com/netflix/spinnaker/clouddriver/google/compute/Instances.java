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
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceList;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;

public final class Instances {

  private final Compute.Instances computeApi;
  private final GoogleNamedAccountCredentials credentials;
  private final GlobalGoogleComputeRequestFactory requestFactory;

  Instances(
      GoogleNamedAccountCredentials credentials,
      GoogleOperationPoller operationPoller,
      Registry registry) {
    this.computeApi = credentials.getCompute().instances();
    this.credentials = credentials;
    this.requestFactory =
        new GlobalGoogleComputeRequestFactory("instances", credentials, operationPoller, registry);
  }

  public PaginatedComputeRequest<Compute.Instances.List, Instance> list(String zone) {
    return new PaginatedComputeRequestImpl<>(
        pageToken ->
            requestFactory.wrapRequest(
                computeApi.list(credentials.getProject(), zone).setPageToken(pageToken), "list"),
        InstanceList::getNextPageToken,
        InstanceList::getItems);
  }
}
