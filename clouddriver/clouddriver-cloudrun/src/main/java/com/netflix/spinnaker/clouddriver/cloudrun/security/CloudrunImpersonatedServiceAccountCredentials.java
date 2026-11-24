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

package com.netflix.spinnaker.clouddriver.cloudrun.security;

import com.google.api.services.run.v1.CloudRunScopes;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Calls Cloud Run as {@code serviceAccountId@serviceAccountProject.iam.gserviceaccount.com},
 * impersonated from clouddriver's application default credentials. Same account settings and
 * behavior as {@code GoogleImpersonatedServiceAccountCredentials} in the Google provider, so each
 * Cloud Run account can have its own identity without a JSON key.
 */
public class CloudrunImpersonatedServiceAccountCredentials extends CloudrunCredentials {

  private static final int LIFETIME_SECONDS = 3600;

  interface SourceCredentials {
    GoogleCredentials get() throws IOException;
  }

  private final String serviceAccountId;
  private final String serviceAccountProject;
  private final SourceCredentials sourceCredentials;

  public CloudrunImpersonatedServiceAccountCredentials(
      String project, String serviceAccountId, String serviceAccountProject) {
    this(
        project, serviceAccountId, serviceAccountProject, GoogleCredentials::getApplicationDefault);
  }

  CloudrunImpersonatedServiceAccountCredentials(
      String project,
      String serviceAccountId,
      String serviceAccountProject,
      SourceCredentials sourceCredentials) {
    super(project);
    this.serviceAccountId = serviceAccountId;
    this.serviceAccountProject = serviceAccountProject;
    this.sourceCredentials = sourceCredentials;
  }

  @Override
  public GoogleCredentials getCredentials() {
    try {
      return ImpersonatedCredentials.create(
          sourceCredentials.get(),
          getTargetPrincipal(),
          null /* delegates */,
          List.of(CloudRunScopes.CLOUD_PLATFORM),
          LIFETIME_SECONDS);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Could not load application default credentials to impersonate " + getTargetPrincipal(),
          e);
    }
  }

  public String getTargetPrincipal() {
    return serviceAccountId + "@" + serviceAccountProject + ".iam.gserviceaccount.com";
  }
}
