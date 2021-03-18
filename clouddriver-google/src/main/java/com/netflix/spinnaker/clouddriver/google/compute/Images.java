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
import com.google.api.services.compute.model.Image;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;

public class Images {

  private final GoogleNamedAccountCredentials credentials;
  private final GlobalGoogleComputeRequestFactory requestFactory;

  public Images(
      GoogleNamedAccountCredentials credentials,
      GoogleOperationPoller operationPoller,
      Registry registry) {
    this.credentials = credentials;
    this.requestFactory =
        new GlobalGoogleComputeRequestFactory("images", credentials, operationPoller, registry);
  }

  public GoogleComputeGetRequest<Compute.Images.Get, Image> get(String project, String image)
      throws IOException {
    Compute.Images.Get request = credentials.getCompute().images().get(project, image);
    return requestFactory.wrapGetRequest(request, "get");
  }
}
