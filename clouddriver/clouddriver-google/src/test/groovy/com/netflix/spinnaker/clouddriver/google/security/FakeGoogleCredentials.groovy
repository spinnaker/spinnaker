/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.security

import com.google.api.client.http.HttpTransport
import com.google.api.client.testing.http.MockHttpTransport
import com.google.auth.oauth2.AccessToken
import com.netflix.spinnaker.clouddriver.google.ComputeVersion

import java.time.LocalDate

class FakeGoogleCredentials extends GoogleCredentials {

  FakeGoogleCredentials() {
    this("unitTestProject")
  }

  FakeGoogleCredentials(String project) {
    super(project, ComputeVersion.DEFAULT)
  }

  @Override
  HttpTransport buildHttpTransport() {
    return new MockHttpTransport.Builder().build()
  }

  @Override
  com.google.auth.oauth2.GoogleCredentials getCredentials() {
    LocalDate tomorrow = LocalDate.now().plusDays(1)
    com.google.auth.oauth2.GoogleCredentials.create(new AccessToken("some-token", java.sql.Date.valueOf(tomorrow)))
  }
}
