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
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.apache.commons.lang3.NotImplementedException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface ArtifactCredentials {
  String getName();
  List<String> getTypes();
  InputStream download(Artifact artifact) throws IOException;

  @JsonIgnore
  default List<String> getArtifactNames() {
    throw new NotImplementedException("Artifact names are not supported for artifact types that '" + getName() + "' account handles");
  }

  @JsonIgnore
  default List<String> getArtifactVersions(String artifactName) {
    throw new NotImplementedException("Artifact versions are not supported for artifact types that '" + getName() + "' account handles");
  }

  default boolean handlesType(String type) {
    return getTypes().stream().anyMatch(it -> it.equals(type));
  }
}
