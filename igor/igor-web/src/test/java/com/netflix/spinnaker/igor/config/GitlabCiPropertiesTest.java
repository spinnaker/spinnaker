/*
 * Copyright 2026 Ericsson AB
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
 */

package com.netflix.spinnaker.igor.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Verifies that {@code gitlab-ci.masters[].listProjectsLive} is loaded from configuration through
 * Spring's property binding (the same mechanism that reads Igor's YAML config), defaults to true,
 * and applies to each master independently.
 */
class GitlabCiPropertiesTest {

  private static GitlabCiProperties bind(Map<String, Object> properties) {
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    return Binder.get(environment).bind("gitlab-ci", GitlabCiProperties.class).get();
  }

  @Test
  void listProjectsLiveDefaultsToTrueWhenNotConfigured() {
    GitlabCiProperties properties =
        bind(
            Map.of(
                "gitlab-ci.masters[0].name", "gitlab-one",
                "gitlab-ci.masters[0].address", "https://gitlab-one.example.com"));

    List<GitlabCiProperties.GitlabCiHost> masters = properties.getMasters();
    assertThat(masters).hasSize(1);
    assertThat(masters.get(0).isListProjectsLive()).isTrue();
  }

  @Test
  void listProjectsLiveCanBeTurnedOffForOneMasterWithoutAffectingOthers() {
    GitlabCiProperties properties =
        bind(
            Map.of(
                "gitlab-ci.masters[0].name", "big-gitlab",
                "gitlab-ci.masters[0].address", "https://big.example.com",
                "gitlab-ci.masters[0].listProjectsLive", "false",
                "gitlab-ci.masters[1].name", "small-gitlab",
                "gitlab-ci.masters[1].address", "https://small.example.com"));

    List<GitlabCiProperties.GitlabCiHost> masters = properties.getMasters();
    assertThat(masters).hasSize(2);
    assertThat(masters.get(0).isListProjectsLive()).isFalse();
    assertThat(masters.get(1).isListProjectsLive()).isTrue();
  }
}
