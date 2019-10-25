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

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpTransport
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import groovy.transform.CompileStatic

@CompileStatic
class GoogleCommonCredentials {
  GoogleCredentials getCredentials() {
    // No JSON key was specified in matching config on key server, so use application default credentials.
    GoogleCredentials.getApplicationDefault()
  }

  HttpTransport buildHttpTransport() {
    try {
      return GoogleNetHttpTransport.newTrustedTransport()
    } catch (Exception e) {
      throw new RuntimeException("Failed to create trusted transport", e)
    }
  }

  static HttpRequestInitializer setHttpTimeout(final GoogleCredentials credentials) {
    return new HttpCredentialsAdapter(credentials) {
      @Override
      public void initialize(HttpRequest httpRequest) throws IOException {
        super.initialize(httpRequest);
        httpRequest.setConnectTimeout(2 * 60000)  // 2 minutes connect timeout
        httpRequest.setReadTimeout(2 * 60000)  // 2 minutes read timeout
      }
    }
  }
}
