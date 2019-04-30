/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.igor.gcb;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudbuild.v1.CloudBuild;
import com.google.api.services.storage.Storage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Factory for calling CloudBuild client library code to create CloudBuild objects */
@Component
@ConditionalOnProperty("gcb.enabled")
@RequiredArgsConstructor
public class CloudBuildFactory {
  private final int connectTimeoutSec = 10;
  private final int readTimeoutSec = 10;

  private final JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
  private final HttpTransport httpTransport;

  // Override the base URL for all requests to the cloud build API; primarily for testing.
  private final String overrideRootUrl;

  @Autowired
  public CloudBuildFactory(HttpTransport httpTransport) {
    this(httpTransport, null);
  }

  public CloudBuild getCloudBuild(GoogleCredential credential, String applicationName) {
    HttpRequestInitializer requestInitializer = getRequestInitializer(credential);
    CloudBuild.Builder builder =
        new CloudBuild.Builder(httpTransport, jsonFactory, requestInitializer)
            .setApplicationName(applicationName);

    if (overrideRootUrl != null) {
      builder.setRootUrl(overrideRootUrl);
    }
    return builder.build();
  }

  public Storage getCloudStorage(GoogleCredential credential, String applicationName) {
    HttpRequestInitializer requestInitializer = getRequestInitializer(credential);
    Storage.Builder builder =
        new Storage.Builder(httpTransport, jsonFactory, requestInitializer)
            .setApplicationName(applicationName);

    if (overrideRootUrl != null) {
      builder.setRootUrl(overrideRootUrl);
    }
    return builder.build();
  }

  private HttpRequestInitializer getRequestInitializer(GoogleCredential credential) {
    return request -> {
      credential.initialize(request);
      request.setConnectTimeout(connectTimeoutSec * 1000);
      request.setReadTimeout(readTimeoutSec * 1000);
    };
  }
}
