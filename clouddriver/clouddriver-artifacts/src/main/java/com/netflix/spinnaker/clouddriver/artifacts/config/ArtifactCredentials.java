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

package com.netflix.spinnaker.clouddriver.artifacts.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.credentials.Credentials;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.NotImplementedException;

@NonnullByDefault
public interface ArtifactCredentials extends Credentials {
  String getName();

  /**
   * The permissions required to read (list/resolve) or use (download/enumerate) this account. An
   * unrestricted (default) {@link Permissions#EMPTY} account is accessible to everyone. Exposed
   * (not {@code @JsonIgnore}'d) the same way {@code AbstractAccountCredentials.getPermissions()} is
   * for cloud-provider accounts, so admins can see whether an account is restricted.
   */
  default Permissions getPermissions() {
    return Permissions.EMPTY;
  }

  /**
   * Returns the artifact types that are handled by these credentials.
   *
   * @return A list of artifact types, which should be immutable.
   */
  List<String> getTypes();

  /**
   * Download the specified artifact
   *
   * @return a stream containing the contents of artifact. It is the caller's responsibility to
   *     close this stream as soon as possible.
   */
  InputStream download(Artifact artifact) throws IOException;

  default Optional<String> resolveArtifactName(Artifact artifact) {
    return Optional.ofNullable(artifact.getName());
  }

  default Optional<String> resolveArtifactVersion(Artifact artifact) {
    return Optional.ofNullable(artifact.getVersion());
  }

  @JsonIgnore
  default List<String> getArtifactNames() {
    throw new NotImplementedException(
        "Artifact names are not supported for artifact types that '"
            + getName()
            + "' account handles");
  }

  @JsonIgnore
  default List<String> getArtifactVersions(String artifactName) {
    throw new NotImplementedException(
        "Artifact versions are not supported for artifact types that '"
            + getName()
            + "' account handles");
  }

  default boolean handlesType(String type) {
    return getTypes().contains(type);
  }
}
