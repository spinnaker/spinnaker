/*
 * Copyright 2026 spinnaker.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import com.google.api.services.compute.ComputeScopes
import com.netflix.spinnaker.clouddriver.google.ComputeVersion
import com.netflix.spinnaker.kork.google.auth.GoogleAuthProperties
import com.netflix.spinnaker.kork.google.auth.GoogleCredentialsFactory
import groovy.transform.CompileStatic

/** Adapts the shared authentication contract to the Google provider's client factory. */
@CompileStatic
class GoogleAuthCredentials extends GoogleCredentials {
  private final GoogleAuthProperties auth
  private final GoogleCredentialsFactory credentialsFactory

  GoogleAuthCredentials(String project, ComputeVersion computeVersion,
                        GoogleAuthProperties auth, GoogleCredentialsFactory credentialsFactory) {
    super(project, computeVersion)
    this.auth = auth
    this.credentialsFactory = credentialsFactory
  }

  boolean usesImpersonation() {
    return auth.impersonateServiceAccount != null
  }

  @Override
  com.google.auth.oauth2.GoogleCredentials getCredentials() {
    return credentialsFactory.create(auth, Collections.singletonList(ComputeScopes.COMPUTE))
  }
}
