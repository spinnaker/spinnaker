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

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.testing.auth.oauth2.MockTokenServerTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudbuild.v1.CloudBuildScopes;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

public class GoogleCloudBuildTestConfig {
  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  Registry registry() {
    return new NoopRegistry();
  }

  @Bean(name = "stubCloudBuildService")
  WireMockServer stubCloudBuildService() {
    WireMockServer server = new WireMockServer(options().dynamicPort());
    server.start();
    return server;
  }

  @Bean
  @Primary
  CloudBuildFactory cloudBuildFactory(
      HttpTransport httpTransport,
      @Qualifier("stubCloudBuildService") WireMockServer wireMockServer) {
    return new CloudBuildFactory(httpTransport, wireMockServer.baseUrl());
  }

  @Bean
  @Primary
  GoogleCredentialService googleCredentialService() {
    return new GoogleCredentialService() {
      @Override
      GoogleCredential getFromKey(String jsonPath) {
        if (!jsonPath.equals("/path/to/some/file")) {
          return null;
        }
        // Create a mock credential whose bearer token is always "test-token"
        try {
          InputStream is =
              GoogleCloudBuildAccountFactory.class.getResourceAsStream(
                  "/gcb/gcb-test-account.json");
          MockTokenServerTransport mockTransport =
              new MockTokenServerTransport("https://accounts.google.com/o/oauth2/auth");
          mockTransport.addServiceAccount(
              "test-account@spinnaker-gcb-test.iam.gserviceaccount.com", "test-token");
          return GoogleCredential.fromStream(is, mockTransport, JacksonFactory.getDefaultInstance())
              .createScoped(CloudBuildScopes.all());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }
}
