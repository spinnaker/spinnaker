/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.googlecommon.security

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory

class GoogleCommonCredentials {
  GoogleCredential getCredential(HttpTransport httpTransport, JsonFactory jsonFactory) {
    // No JSON key was specified in matching config on key server, so use application default credentials.
    GoogleCredential.getApplicationDefault()
  }

  HttpTransport buildHttpTransport() {
    try {
      return GoogleNetHttpTransport.newTrustedTransport()
    } catch (Exception e) {
      throw new RuntimeException("Failed to create trusted transport", e)
    }
  }

  static HttpRequestInitializer setHttpTimeout(final HttpRequestInitializer requestInitializer) {
    return new HttpRequestInitializer() {
      @Override
      public void initialize(HttpRequest httpRequest) throws IOException {
        requestInitializer.initialize(httpRequest)
        httpRequest.setConnectTimeout(2 * 60000)  // 2 minutes connect timeout
        httpRequest.setReadTimeout(2 * 60000)  // 2 minutes read timeout
      }
    }
  }
}
