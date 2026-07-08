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
import com.netflix.spinnaker.igor.build.artifact.decorator.DebDetailsDecorator;
import com.netflix.spinnaker.igor.build.artifact.decorator.RpmDetailsDecorator;
import com.netflix.spinnaker.igor.build.model.GenericArtifact;
import com.netflix.spinnaker.igor.config.ArtifactDecorationProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactDecoratorTest {

  @ParameterizedTest
  @CsvSource({
    "openmotif22-libs-2.2.4-192.1.3.x86_64.rpm,openmotif22-libs,2.2.4-192.1.3.x86_64,rpm",
    "api_1.1.1-h01.sha123_all.deb,api,1.1.1-h01.sha123,deb",
    "test-2.13.jar,test,2.13,jar",
    "wara-2.13.war,wara,2.13,war",
    "unknown-3.2.1.apk,,,",
    ",,,",
  })
  void decorate(String reference, String expectedName, String expectedVersion, String expectedType) {
    List<ArtifactDetailsDecorator> artifactDetailsDecorators = new ArrayList<>();
    artifactDetailsDecorators.add(new DebDetailsDecorator());
    artifactDetailsDecorators.add(new RpmDetailsDecorator());
    String regex = "([a-zA-Z-]+)\\-([\\d\\.]+)\\.([jw]ar)$";
    ArtifactDecorationProperties.FileDecorator fileDecorator =
        new ArtifactDecorationProperties.FileDecorator();
    fileDecorator.setType("java-magic");
    fileDecorator.setIdentifierRegex(regex);
    fileDecorator.setDecoratorRegex(regex);
    ArtifactDecorationProperties artifactDecorationProperties =
        new ArtifactDecorationProperties();
    artifactDecorationProperties.setFileDecorators(List.of(fileDecorator));
    ArtifactDecorator artifactDecorator =
        new ArtifactDecorator(artifactDetailsDecorators, artifactDecorationProperties);

    GenericArtifact genericArtifact = new GenericArtifact(reference, reference, reference);
    List<GenericArtifact> decorated = artifactDecorator.decorate(genericArtifact);
    GenericArtifact result = decorated != null && !decorated.isEmpty() ? decorated.get(0) : null;

    if (expectedName == null || expectedName.isEmpty()) {
      assertNull(result == null ? null : result.getName());
      assertNull(result == null ? null : result.getVersion());
      assertNull(result == null ? null : result.getType());
    } else {
      assertNotNull(result);
      assertEquals(expectedName, result.getName());
      assertEquals(expectedVersion, result.getVersion());
      assertEquals(expectedType, result.getType());
    }
  }

  @ParameterizedTest
  @CsvSource({
    "api_1.1.1-h01.sha123_all.deb,api,1.1.1-h01.sha123,deb",
    "api_1.1.1.deb,api,1.1.1,deb-override",
  })
  void overrideTheIncludedParserWithAParserDefinedInConfiguration(
      String reference, String expectedName, String expectedVersion, String expectedType) {
    List<ArtifactDetailsDecorator> artifactDetailsDecorators = new ArrayList<>();
    artifactDetailsDecorators.add(new DebDetailsDecorator());
    artifactDetailsDecorators.add(new RpmDetailsDecorator());
    String regex = "([a-zA-Z-]+)\\_([\\d\\.]+)\\.deb$";
    ArtifactDecorationProperties.FileDecorator fileDecorator =
        new ArtifactDecorationProperties.FileDecorator();
    fileDecorator.setType("deb-override");
    fileDecorator.setIdentifierRegex(regex);
    fileDecorator.setDecoratorRegex(regex);
    ArtifactDecorationProperties artifactDecorationProperties =
        new ArtifactDecorationProperties();
    artifactDecorationProperties.setFileDecorators(List.of(fileDecorator));
    ArtifactDecorator artifactDecorator =
        new ArtifactDecorator(artifactDetailsDecorators, artifactDecorationProperties);

    GenericArtifact genericArtifact = new GenericArtifact(reference, reference, reference);
    GenericArtifact result = artifactDecorator.decorate(genericArtifact).get(0);

    assertEquals(expectedName, result.getName());
    assertEquals(expectedVersion, result.getVersion());
    assertEquals(expectedType, result.getType());
  }

  @Test
  void shouldSupportMultipleConfigurableParsers() {
    List<ArtifactDetailsDecorator> artifactDetailsDecorators = new ArrayList<>();
    artifactDetailsDecorators.add(new DebDetailsDecorator());
    artifactDetailsDecorators.add(new RpmDetailsDecorator());
    ArtifactDecorationProperties.FileDecorator oldDockerDecorator =
        new ArtifactDecorationProperties.FileDecorator();
    oldDockerDecorator.setType("docker");
    oldDockerDecorator.setIdentifierRegex("(.+\\/.+:.+)");
    oldDockerDecorator.setDecoratorRegex("[a-zA-Z0-9.]+\\/(.+):(.+)");
    ArtifactDecorationProperties.FileDecorator newDockerDecorator =
        new ArtifactDecorationProperties.FileDecorator();
    newDockerDecorator.setType("docker/image");
    newDockerDecorator.setIdentifierRegex("(.+\\/.+:.+)");
    newDockerDecorator.setDecoratorRegex("(.+):(.+)");
    ArtifactDecorationProperties artifactDecorationProperties =
        new ArtifactDecorationProperties();
    artifactDecorationProperties.setFileDecorators(
        List.of(newDockerDecorator, oldDockerDecorator));
    ArtifactDecorator artifactDecorator =
        new ArtifactDecorator(artifactDetailsDecorators, artifactDecorationProperties);

    String reference = "gcr.io/my-images/nginx:0cce25b9a55";
    GenericArtifact genericArtifact = new GenericArtifact(reference, reference, reference);
    List<GenericArtifact> genericArtifacts = artifactDecorator.decorate(genericArtifact);

    assertEquals(List.of("gcr.io/my-images/nginx", "my-images/nginx"),
        genericArtifacts.stream().map(GenericArtifact::getName).toList());
    assertEquals(List.of("0cce25b9a55", "0cce25b9a55"),
        genericArtifacts.stream().map(GenericArtifact::getVersion).toList());
    assertEquals(List.of("docker/image", "docker"),
        genericArtifacts.stream().map(GenericArtifact::getType).toList());
  }

  @Test
  void shouldOnlyDecorateArtifactsOnce() {
    List<ArtifactDetailsDecorator> artifactDetailsDecorators = new ArrayList<>();
    artifactDetailsDecorators.add(new DebDetailsDecorator());
    artifactDetailsDecorators.add(new RpmDetailsDecorator());
    ArtifactDecorationProperties.FileDecorator oldDockerDecorator =
        new ArtifactDecorationProperties.FileDecorator();
    oldDockerDecorator.setType("docker");
    oldDockerDecorator.setIdentifierRegex("(.+\\/.+:.+)");
    oldDockerDecorator.setDecoratorRegex("[a-zA-Z0-9.]+\\/(.+):(.+)");
    ArtifactDecorationProperties.FileDecorator newDockerDecorator =
        new ArtifactDecorationProperties.FileDecorator();
    newDockerDecorator.setType("docker/image");
    newDockerDecorator.setIdentifierRegex("(.+\\/.+:.+)");
    newDockerDecorator.setDecoratorRegex("(.+):(.+)");
    ArtifactDecorationProperties artifactDecorationProperties =
        new ArtifactDecorationProperties();
    artifactDecorationProperties.setFileDecorators(
        List.of(newDockerDecorator, oldDockerDecorator));
    ArtifactDecorator artifactDecorator =
        new ArtifactDecorator(artifactDetailsDecorators, artifactDecorationProperties);

    String reference = "gcr.io/my-images/nginx:0cce25b9a55";
    GenericArtifact genericArtifact = new GenericArtifact(reference, reference, reference);
    List<GenericArtifact> genericArtifacts = artifactDecorator.decorate(genericArtifact);
    genericArtifacts =
        genericArtifacts.stream()
            .flatMap(artifact -> artifactDecorator.decorate(artifact).stream())
            .toList();

    assertEquals(List.of("gcr.io/my-images/nginx", "my-images/nginx"),
        genericArtifacts.stream().map(GenericArtifact::getName).toList());
    assertEquals(List.of("0cce25b9a55", "0cce25b9a55"),
        genericArtifacts.stream().map(GenericArtifact::getVersion).toList());
    assertEquals(List.of("docker/image", "docker"),
        genericArtifacts.stream().map(GenericArtifact::getType).toList());
  }
}
