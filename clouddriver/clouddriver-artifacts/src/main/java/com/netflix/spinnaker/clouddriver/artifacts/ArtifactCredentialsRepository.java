/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts;

import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactAccountAuthorizer;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.kork.exceptions.MissingCredentialsException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ArtifactCredentialsRepository
    extends CompositeCredentialsRepository<ArtifactCredentials> {

  private final ArtifactAccountAuthorizer authorizer;

  public ArtifactCredentialsRepository(
      List<CredentialsRepository<? extends ArtifactCredentials>> repositories,
      ArtifactAccountAuthorizer authorizer) {
    super(repositories);
    this.authorizer = authorizer;
  }

  public ArtifactCredentials getCredentialsForType(String name, String artifactType) {
    if (Strings.isNullOrEmpty(name)) {
      String message = "An artifact account must be supplied to download this artifact: " + name;
      log.debug(message);
      throw new IllegalArgumentException(message);
    }

    ArtifactCredentials credentials =
        getAllCredentials().stream()
            .filter(a -> a.getName().equals(name) && a.handlesType(artifactType))
            .findFirst()
            .orElseThrow(
                () ->
                    new MissingCredentialsException(
                        "Credentials '"
                            + name
                            + "' supporting artifact type '"
                            + artifactType
                            + "' cannot be found"));

    String username = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous");
    if (!authorizer.canUse(username, credentials)) {
      // Same exception as "not found" so an unauthorized caller can't distinguish a restricted
      // account from one that doesn't exist.
      throw new MissingCredentialsException(
          "Credentials '"
              + name
              + "' supporting artifact type '"
              + artifactType
              + "' cannot be found");
    }

    return credentials;
  }
}
