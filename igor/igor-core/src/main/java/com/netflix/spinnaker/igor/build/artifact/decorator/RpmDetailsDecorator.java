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
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty("artifact.decorator.enabled")
public class RpmDetailsDecorator implements ArtifactDetailsDecorator {
  private final String packageType = "rpm";
  private final String versionDelimiter = "-";

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

    return genericArtifact.getFileName().endsWith(".rpm");
  }

  @Override
  public String decoratorName() {
    return packageType;
  }

  public String extractVersion(String file) {
    String[] parts = file.split(versionDelimiter);
    String suffix = parts[parts.length - 1].replaceAll("\\.rpm", "");
    return parts[parts.length - 2] + versionDelimiter + suffix;
  }

  public String extractName(String file) {
    List<String> parts = Arrays.asList(file.split(versionDelimiter));
    return String.join(versionDelimiter, parts.subList(0, parts.size() - 2));
  }
}
