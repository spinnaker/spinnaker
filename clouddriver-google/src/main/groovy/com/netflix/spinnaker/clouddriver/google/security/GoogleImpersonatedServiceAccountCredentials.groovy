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

package com.netflix.spinnaker.clouddriver.google.security

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.netflix.spinnaker.clouddriver.google.ComputeVersion
import groovy.util.logging.Slf4j

@Slf4j
class GoogleImpersonatedServiceAccountCredentials extends GoogleCredentials {

  String serviceAccountId
  String serviceAccountProject

  GoogleImpersonatedServiceAccountCredentials(String project,
                                              ComputeVersion computeVersion,
                                              String serviceAccountId,
                                              String serviceAccountProject) {
    super(project, computeVersion)
    this.serviceAccountId = serviceAccountId
    this.serviceAccountProject = serviceAccountProject
  }

  @Override
  GoogleCredential getCredential(HttpTransport httpTransport, JsonFactory jsonFactory) {
    def builder = new GoogleCredential.Builder()
        .setTransport(httpTransport)
        .setJsonFactory(jsonFactory)
        .setClientAuthentication(GoogleCredential.getApplicationDefault())
    return new GoogleImpersonatedCredential(builder, serviceAccountId, serviceAccountProject)
  }
}
