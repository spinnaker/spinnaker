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
 *
 *
 */

package com.netflix.spinnaker.halyard.core.registry.v1;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.StorageScopes;
import com.netflix.spinnaker.halyard.core.provider.v1.google.GoogleCredentials;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("spinnaker.config.input.gcs.enabled")
@Slf4j
public class GoogleProfileReaderConfig {
  @Bean
  public Storage applicationDefaultGoogleStorage() {
    return createGoogleStorage(true);
  }

  @Bean
  public Storage unauthenticatedGoogleStorage() {
    return createGoogleStorage(false);
  }

  private Storage createGoogleStorage(boolean useApplicationDefaultCreds) {
    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
    String applicationName = "Spinnaker/Halyard";
    HttpRequestInitializer requestInitializer = null;

    if (useApplicationDefaultCreds) {
      try {
        com.google.auth.oauth2.GoogleCredentials credentials =
            com.google.auth.oauth2.GoogleCredentials.getApplicationDefault();
        if (credentials.createScopedRequired()) {
          credentials =
              credentials.createScoped(
                  Collections.singleton(StorageScopes.DEVSTORAGE_FULL_CONTROL));
        }
        requestInitializer = GoogleCredentials.setHttpTimeout(credentials);
        log.info("Loaded application default credential for reading BOMs & profiles.");
      } catch (Exception e) {
        log.debug(
            "No application default credential could be loaded for reading BOMs & profiles. Continuing unauthenticated: {}",
            e.getMessage());
      }
    }
    if (requestInitializer == null) {
      requestInitializer = GoogleCredentials.retryRequestInitializer();
    }

    return new Storage.Builder(
            GoogleCredentials.buildHttpTransport(), jsonFactory, requestInitializer)
        .setApplicationName(applicationName)
        .build();
  }
}
