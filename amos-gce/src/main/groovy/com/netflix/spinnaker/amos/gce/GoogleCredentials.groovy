/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.amos.gce

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.services.compute.Compute
import groovy.transform.Canonical
import groovy.transform.TupleConstructor
import java.security.PrivateKey

@TupleConstructor(includeFields = true)
@Canonical
class GoogleCredentials {
  final String project
  final Compute compute

  private final HttpTransport httpTransport
  private final JsonFactory jsonFactory
  private final String email
  private final PrivateKey privateKey

  GoogleCredential.Builder createCredentialBuilder(String... serviceAccountScopes) {
    if (email && privateKey) {
      new GoogleCredential.Builder().setTransport(httpTransport)
              .setJsonFactory(jsonFactory)
              .setServiceAccountId(email)
              .setServiceAccountScopes(Arrays.asList(serviceAccountScopes))
              .setServiceAccountPrivateKey(privateKey)
    } else {
      new GoogleCredential.Builder() {
        @Override
        public GoogleCredential build() {
          GoogleCredential credential = GoogleCredential.getApplicationDefault()

          credential.createScoped(serviceAccountScopes as Set)
        }
      }
              .setTransport(httpTransport)
              .setJsonFactory(jsonFactory)
    }
  }
}
