/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.echo.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Client;
import retrofit.client.OkClient;

@Component
public class RestClientFactory {

  @Autowired private OkHttpClientFactory httpClientFactory;

  public OkHttpClientFactory getHttpClientFactory() {
    return httpClientFactory;
  }

  public void setHttpClientFactory(OkHttpClientFactory httpClientFactory) {
    this.httpClientFactory = httpClientFactory;
  }

  public Client getClient(Boolean insecure) {
    if (insecure) {
      return new OkClient(httpClientFactory.getInsecureClient());
    } else {
      return new OkClient();
    }
  }
}
