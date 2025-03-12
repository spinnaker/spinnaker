/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.kork.secrets.engines;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.storage.StorageScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

class GoogleUtils {

  private static final int CONNECT_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(20);
  private static final int READ_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(20);

  static GoogleCredentials buildGoogleCredentials() throws IOException {
    GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();

    if (credentials.createScopedRequired()) {
      credentials =
          credentials.createScoped(Collections.singleton(StorageScopes.DEVSTORAGE_READ_ONLY));
    }

    return credentials;
  }

  static HttpTransport buildHttpTransport() {
    try {
      return GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException("Failed to build trusted transport", e);
    }
  }

  static HttpRequestInitializer setTimeoutsAndRetryBehavior(final GoogleCredentials credentials) {
    return new HttpCredentialsAdapter(credentials) {
      public void initialize(HttpRequest request) throws IOException {
        super.initialize(request);
        request.setConnectTimeout(CONNECT_TIMEOUT);
        request.setReadTimeout(READ_TIMEOUT);
        HttpBackOffUnsuccessfulResponseHandler unsuccessfulResponseHandler =
            new HttpBackOffUnsuccessfulResponseHandler(new ExponentialBackOff());
        unsuccessfulResponseHandler.setBackOffRequired(
            HttpBackOffUnsuccessfulResponseHandler.BackOffRequired.ON_SERVER_ERROR);
        request.setUnsuccessfulResponseHandler(unsuccessfulResponseHandler);
      }
    };
  }
}
