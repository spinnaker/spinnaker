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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.testing.auth.oauth2.MockGoogleCredential
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.testing.http.MockHttpTransport
import com.netflix.spinnaker.clouddriver.google.ComputeVersion

class FakeGoogleCredentials extends GoogleCredentials {

  FakeGoogleCredentials() {
    this("unitTestProject")
  }

  FakeGoogleCredentials(String project) {
    super(project, ComputeVersion.V1)
  }

  @Override
  HttpTransport buildHttpTransport() {
    return new MockHttpTransport.Builder().build()
  }

  @Override
  GoogleCredential getCredential(HttpTransport httpTransport, JsonFactory _) {
    return new MockGoogleCredential.Builder().setTransport(httpTransport).build()
  }
}
