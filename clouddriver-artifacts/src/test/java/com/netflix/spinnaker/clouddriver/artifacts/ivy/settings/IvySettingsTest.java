/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.artifacts.ivy.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(TempDirectory.class)
class IvySettingsTest {
  @Test
  void minimalJCenterSettings(@TempDirectory.TempDir Path tempDir) {
    BintrayResolver bintray = new BintrayResolver();
    bintray.setName("jcenter");

    Resolvers resolvers = new Resolvers();
    resolvers.setBintray(Collections.singletonList(bintray));

    IvySettings settings = new IvySettings();
    settings.setResolvers(resolvers);

    settings.toIvy(tempDir);
  }

  @Test
  void parseIvySettingsGeneratedForMavenRepositoryInArtifactory(@TempDirectory.TempDir Path tempDir) {
    String ivySettingsXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<ivy-settings>\n" +
      "  <settings defaultResolver=\"main\" />\n" +
      "  <resolvers>\n" +
      "    <chain name=\"main\">\n" +
      "      <ibiblio name=\"public\" m2compatible=\"true\" root=\"https://repo.spring.io/libs-release\" />\n" +
      "    </chain>\n" +
      "  </resolvers>\n" +
      "</ivy-settings>";

    IvySettings settings = IvySettings.parse(ivySettingsXml);
    settings.toIvy(tempDir);

    assertThat(settings.getSettings().getDefaultResolver()).isEqualTo("main");
  }

  @Test
  void atLeastOneResolverIsRequired() {
    IvySettings settings = new IvySettings();
    assertThatThrownBy(() -> settings.toIvySettings(Paths.get("./"))).isInstanceOf(IllegalArgumentException.class);
  }
}