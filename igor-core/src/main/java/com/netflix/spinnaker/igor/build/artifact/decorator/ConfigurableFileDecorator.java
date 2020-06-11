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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;

@Getter
public class ConfigurableFileDecorator implements ArtifactDetailsDecorator {
  private final String type;
  private final Pattern decoratorPattern;
  private final Pattern identifierPattern;

  public ConfigurableFileDecorator(String type, String decoratorRegex, String identifierRegex) {
    this.decoratorPattern = Pattern.compile(decoratorRegex);
    this.identifierPattern = Pattern.compile(identifierRegex);
    this.type = type;
  }

  @Override
  public GenericArtifact decorate(GenericArtifact genericArtifact) {
    if (genericArtifact.getFileName() == null) {
      return genericArtifact;
    }
    GenericArtifact copy = genericArtifact.toBuilder().build();

    Matcher matcher = decoratorPattern.matcher(genericArtifact.getFileName());
    if (matcher.find()) {
      copy.setName(matcher.group(1));
      copy.setVersion(matcher.group(2));
      copy.setType(matcher.groupCount() > 2 ? matcher.group(3) : type);
      copy.setReference(genericArtifact.getFileName());
    }

    return copy;
  }

  @Override
  public boolean handles(GenericArtifact genericArtifact) {
    if (genericArtifact == null || genericArtifact.getFileName() == null) {
      return false;
    }
    return identifierPattern.matcher(genericArtifact.getFileName()).find();
  }

  @Override
  public String decoratorName() {
    return type;
  }
}
