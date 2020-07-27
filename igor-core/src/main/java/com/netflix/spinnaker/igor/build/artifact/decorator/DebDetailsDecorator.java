/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.build.artifact.decorator;

import com.netflix.spinnaker.igor.build.model.GenericArtifact;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty("artifact.decorator.enabled")
public class DebDetailsDecorator implements ArtifactDetailsDecorator {
  private final String packageType = "deb";
  private final String versionDelimiter = "_";

  @Override
  public GenericArtifact decorate(GenericArtifact genericArtifact) {
    return genericArtifact.toBuilder()
        .name(extractName(genericArtifact.getFileName()))
        .version(extractVersion(genericArtifact.getFileName()))
        .type(packageType)
        .reference(genericArtifact.getFileName())
        .build();
  }

  @Override
  public boolean handles(GenericArtifact genericArtifact) {
    if (genericArtifact.getFileName() == null) {
      return false;
    }

    // expected package name: <name>_<version>_<architecture>.deb
    return genericArtifact.getFileName().endsWith(".deb")
        && genericArtifact.getFileName().split(versionDelimiter).length >= 3;
  }

  @Override
  public String decoratorName() {
    return packageType;
  }

  public String extractVersion(String file) {
    return file.split(versionDelimiter)[1];
  }

  public String extractName(String file) {
    return file.split(versionDelimiter)[0];
  }
}
