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

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.InstanceTemplateList;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;

public class InstanceTemplates {

  private final Compute.InstanceTemplates computeApi;
  private final GoogleNamedAccountCredentials credentials;
  private final GlobalGoogleComputeRequestFactory requestFactory;
  private static final String defaultView =
      "FULL"; // https://cloud.google.com/sdk/gcloud/reference/beta/compute/instance-templates/list

  InstanceTemplates(
      GoogleNamedAccountCredentials credentials,
      GoogleOperationPoller operationPoller,
      Registry registry) {
    this.computeApi = credentials.getCompute().instanceTemplates();
    this.credentials = credentials;
    this.requestFactory =
        new GlobalGoogleComputeRequestFactory(
            "instanceTemplates", credentials, operationPoller, registry);
  }

  public GoogleComputeOperationRequest<Compute.InstanceTemplates.Delete> delete(String name)
      throws IOException {

    Compute.InstanceTemplates.Delete request = computeApi.delete(credentials.getProject(), name);
    return requestFactory.wrapOperationRequest(request, "delete");
  }

  public GoogleComputeGetRequest<Compute.InstanceTemplates.Get, InstanceTemplate> get(String name)
      throws IOException {
    Compute.InstanceTemplates.Get request = computeApi.get(credentials.getProject(), name);
    return requestFactory.wrapGetRequest(request, "get");
  }

  public GoogleComputeOperationRequest<Compute.InstanceTemplates.Insert> insert(
      InstanceTemplate template) throws IOException {
    Compute.InstanceTemplates.Insert request =
        computeApi.insert(credentials.getProject(), template);
    return requestFactory.wrapOperationRequest(request, "insert");
  }

  public PaginatedComputeRequest<Compute.InstanceTemplates.List, InstanceTemplate> list() {
    return new PaginatedComputeRequestImpl<>(
        pageToken ->
            requestFactory.wrapRequest(
                computeApi
                    .list(credentials.getProject())
                    .setPageToken(pageToken)
                    .setView(defaultView),
                "list"),
        InstanceTemplateList::getNextPageToken,
        InstanceTemplateList::getItems);
  }
}
