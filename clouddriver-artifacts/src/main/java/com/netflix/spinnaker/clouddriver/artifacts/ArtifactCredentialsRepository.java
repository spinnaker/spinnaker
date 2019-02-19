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

import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class ArtifactCredentialsRepository {
  @Getter
  private final List<ArtifactCredentials> allCredentials;

  public ArtifactCredentialsRepository(List<List<? extends ArtifactCredentials>> allCredentials) {
    this.allCredentials = Collections.unmodifiableList(
      allCredentials.stream()
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .collect(Collectors.toList())
    );
  }

  public ArtifactCredentials getCredentials(String accountName) {
    return getAllCredentials()
      .stream()
      .filter(e -> e.getName().equals(accountName))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("No credentials with name '" + accountName + "' could be found."));
  }

  public ArtifactCredentials getCredentials(String accountName, String type) {
    if (StringUtils.isEmpty(accountName)) {
      throw new IllegalArgumentException("An artifact account must be supplied to download this artifact: " + accountName);
    }

    ArtifactCredentials credentials = getCredentials(accountName);
    if (!credentials.handlesType(type)) {
      throw new IllegalArgumentException("Artifact credentials '" + accountName + "' cannot handle artifacts of type '" + type + "'");
    }

    return credentials;
  }
}
