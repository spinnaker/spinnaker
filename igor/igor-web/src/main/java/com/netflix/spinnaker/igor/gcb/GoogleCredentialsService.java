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

import com.google.api.services.cloudbuild.v1.CloudBuildScopes;
import com.google.auth.oauth2.GoogleCredentials;
import com.netflix.spinnaker.kork.google.auth.GoogleAuthProperties;
import com.netflix.spinnaker.kork.google.auth.GoogleCredentialsFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Applies the Cloud Build API scopes to the shared Google authentication contract. */
@Component
@ConditionalOnProperty("gcb.enabled")
class GoogleCredentialsService {
  private final GoogleCredentialsFactory credentialsFactory;

  GoogleCredentialsService() {
    this(new GoogleCredentialsFactory());
  }

  GoogleCredentialsService(GoogleCredentialsFactory credentialsFactory) {
    this.credentialsFactory = credentialsFactory;
  }

  GoogleCredentials getCredentials(GoogleAuthProperties auth) {
    try {
      return credentialsFactory.create(auth, CloudBuildScopes.all());
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to initialize Google Cloud Build credentials", e);
    }
  }
}
