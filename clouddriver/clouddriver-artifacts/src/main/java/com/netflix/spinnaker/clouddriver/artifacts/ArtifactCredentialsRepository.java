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
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.kork.exceptions.MissingCredentialsException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;

@Slf4j
public class ArtifactCredentialsRepository
    extends CompositeCredentialsRepository<ArtifactCredentials> {

  public ArtifactCredentialsRepository(
      List<CredentialsRepository<? extends ArtifactCredentials>> repositories) {
    super(repositories);
  }

  /**
   * The single choke point shared by the HTTP fetch endpoint and in-process atomic operations
   * (e.g. cloud-provider deploy converters) that resolve artifact credentials by account name --
   * enforced here, rather than only at HTTP controller boundaries, so a new caller can't
   * accidentally bypass authorization the way {@code
   * DeployCloudFoundryServerGroupAtomicOperationConverter} used to by calling {@code
   * getFirstCredentialsWithName} directly.
   */
  @PreAuthorize("hasPermission(#name, 'artifact_account', 'WRITE')")
  public ArtifactCredentials getCredentialsForType(String name, String artifactType) {
    if (Strings.isNullOrEmpty(name)) {
      String message = "An artifact account must be supplied to download this artifact: " + name;
      log.debug(message);
      throw new IllegalArgumentException(message);
    }

    return getAllCredentials().stream()
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
  }
}
