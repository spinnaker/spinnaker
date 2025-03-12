/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.google.security;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.MonitoringScopes;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.StorageScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString
@Slf4j
public class GoogleClientFactory {

  private static String applicationVersion =
      Optional.ofNullable(GoogleClientFactory.class.getPackage().getImplementationVersion())
          .orElse("Unknown");

  @Getter private String project;

  public GoogleClientFactory(String project) {
    this.project = project;
  }

  public Monitoring getMonitoring() throws IOException {
    HttpTransport httpTransport = buildHttpTransport();
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    GoogleCredentials credentials = getCredentials(MonitoringScopes.all());
    HttpRequestInitializer reqInit = setHttpTimeout(credentials);
    String applicationName = "Spinnaker/" + applicationVersion;

    return new Monitoring.Builder(httpTransport, jsonFactory, reqInit)
        .setApplicationName(applicationName)
        .build();
  }

  public Storage getStorage() throws IOException {
    HttpTransport httpTransport = buildHttpTransport();
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    GoogleCredentials credentials = getCredentials(StorageScopes.all());
    HttpRequestInitializer reqInit = setHttpTimeout(credentials);
    String applicationName = "Spinnaker/" + applicationVersion;

    return new Storage.Builder(httpTransport, jsonFactory, reqInit)
        .setApplicationName(applicationName)
        .build();
  }

  protected GoogleCredentials getCredentials(Collection<String> scopes) throws IOException {
    log.debug(
        "Loading credentials for project {} using application default credentials, with scopes {}.",
        project,
        scopes);

    // No JSON key was specified in matching config on key server, so use application default
    // credentials.
    return GoogleCredentials.getApplicationDefault().createScoped(scopes);
  }

  protected HttpTransport buildHttpTransport() {
    try {
      return GoogleNetHttpTransport.newTrustedTransport();
    } catch (Exception e) {
      throw new RuntimeException("Failed to create trusted transport", e);
    }
  }

  static HttpRequestInitializer setHttpTimeout(final GoogleCredentials credentials) {
    return new HttpCredentialsAdapter(credentials) {
      @Override
      public void initialize(HttpRequest httpRequest) throws IOException {
        super.initialize(httpRequest);
        httpRequest.setConnectTimeout(2 * 60000); // 2 minutes connect timeout
        httpRequest.setReadTimeout(2 * 60000); // 2 minutes read timeout
      }
    };
  }
}
