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

package com.netflix.spinnaker.igor.service;

import com.netflix.spinnaker.igor.build.artifact.decorator.ArtifactDetailsDecorator;
import com.netflix.spinnaker.igor.build.artifact.decorator.ConfigurableFileDecorator;
import com.netflix.spinnaker.igor.build.model.GenericArtifact;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.config.ArtifactDecorationProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty("artifact.decorator.enabled")
public class ArtifactDecorator {
  private final List<ArtifactDetailsDecorator> artifactDetailsDecorators;

  @Autowired
  public ArtifactDecorator(
      List<ArtifactDetailsDecorator> artifactDetailsDecorators,
      @Valid ArtifactDecorationProperties artifactDecorationProperties) {
    this.artifactDetailsDecorators =
        Optional.ofNullable(artifactDetailsDecorators).orElse(new ArrayList<>());

    List<ArtifactDetailsDecorator> configuredArtifactDetailsDecorators =
        Optional.ofNullable(artifactDecorationProperties)
            .map(ArtifactDecorationProperties::getFileDecorators)
            .map(
                fileDecorators ->
                    fileDecorators.stream()
                        .peek(
                            fileDecorator ->
                                log.info(
                                    "Configuring custom artifact decorator of type : {}",
                                    fileDecorator.getType()))
                        .map(
                            fileDecorator ->
                                (ArtifactDetailsDecorator)
                                    new ConfigurableFileDecorator(
                                        fileDecorator.getType(),
                                        fileDecorator.getDecoratorRegex(),
                                        fileDecorator.getIdentifierRegex()))
                        .collect(Collectors.toList()))
            .orElse(Collections.emptyList());

    this.artifactDetailsDecorators.addAll(0, configuredArtifactDetailsDecorators);
  }

  public List<GenericArtifact> decorate(GenericArtifact genericArtifact) {
    List<ArtifactDetailsDecorator> filteredDecorators =
        artifactDetailsDecorators.stream()
            .filter(decorator -> decorator.handles(genericArtifact))
            .collect(Collectors.toList());

    if (filteredDecorators.isEmpty() || genericArtifact.isDecorated()) {
      return Collections.singletonList(genericArtifact);
    }
    if (filteredDecorators.size() > 1) {
      // We want to be able to define multiple decorators of the same type, but we also want to be
      // able to override the built in decorators, so if any custom decorators are found, remove the
      // ones built in.
      filteredDecorators =
          filteredDecorators.stream()
              .filter(decorator -> decorator instanceof ConfigurableFileDecorator)
              .collect(Collectors.toList());
    }

    return filteredDecorators.stream()
        .map(
            decorator -> {
              log.debug(
                  "Decorated artifact with decorator [{}]: {}",
                  decorator.decoratorName(),
                  genericArtifact.toString());
              GenericArtifact decoratedArtifact = decorator.decorate(genericArtifact);
              decoratedArtifact.setDecorated(true);
              return decoratedArtifact;
            })
        .collect(Collectors.toList());
  }

  public void decorate(GenericBuild genericBuild) {
    genericBuild.setArtifacts(
        Optional.ofNullable(genericBuild.getArtifacts()).orElse(Collections.emptyList()).stream()
            .flatMap(artifacts -> decorate(artifacts).stream())
            .collect(Collectors.toList()));
  }
}
