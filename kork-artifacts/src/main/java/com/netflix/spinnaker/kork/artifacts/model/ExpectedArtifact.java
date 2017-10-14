/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.kork.artifacts.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.StringUtils;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpectedArtifact {
  Artifact matchArtifact;
  boolean usePriorArtifact;
  boolean useDefaultArtifact;
  Artifact defaultArtifact;

  /**
   * Decide if the "matchArtifact" matches the incoming artifact. Any fields not specified
   * if the "matchArtifact" are not compared.
   *
   * @param other is the artifact to match against
   * @return true i.f.f. the artifacts match
   */
  public boolean matches(Artifact other) {
    String thisType = matchArtifact.getType();
    String otherType = other.getType();
    if (StringUtils.isNotEmpty(thisType) && !thisType.equals(otherType)) {
      return false;
    }

    String thisName = matchArtifact.getName();
    String otherName = other.getName();
    if (StringUtils.isNotEmpty(thisName) && !thisName.equals(otherName)) {
      return false;
    }

    String thisVersion = matchArtifact.getVersion();
    String otherVersion = other.getVersion();
    if (StringUtils.isNotEmpty(thisVersion) && !thisVersion.equals(otherVersion)) {
      return false;
    }

    String thisLocation = matchArtifact.getLocation();
    String otherLocation = other.getLocation();
    if (StringUtils.isNotEmpty(thisLocation) && !thisLocation.equals(otherLocation)) {
      return false;
    }

    String thisReference = matchArtifact.getReference();
    String otherReference = other.getReference();
    if (StringUtils.isNotEmpty(thisReference) && !thisReference.equals(otherReference)) {
      return false;
    }

    // Explicitly avoid matching on UUID, provenance & artifactAccount

    return true;
  }
}
