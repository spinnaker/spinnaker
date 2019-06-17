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

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;

class ComputeOperationMockHttpTransport extends HttpTransport {

  private final MockLowLevelHttpResponse createOperationResponse;

  ComputeOperationMockHttpTransport(MockLowLevelHttpResponse createOperationResponse) {
    this.createOperationResponse = createOperationResponse;
  }

  @Override
  protected LowLevelHttpRequest buildRequest(String method, String url) {
    if (url.toLowerCase().contains("operation")) {
      return new MockLowLevelHttpRequest(url)
          .setResponse(
              new MockLowLevelHttpResponse()
                  .setStatusCode(200)
                  .setContent(
                      "" + "{" + "  \"name\":   \"opName\"," + "  \"status\": \"DONE\"" + "}"));
    } else {
      return new MockLowLevelHttpRequest(url).setResponse(createOperationResponse);
    }
  }
}
