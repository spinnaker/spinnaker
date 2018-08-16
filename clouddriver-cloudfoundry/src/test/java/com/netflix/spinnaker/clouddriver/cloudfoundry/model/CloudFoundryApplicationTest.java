/*
 * Copyright 2018 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;

class CloudFoundryApplicationTest {
  @Test
  void getClusterNamesGroupsByAccount() {
    CloudFoundryApplication app = new CloudFoundryApplication(
      "app",
      Arrays.asList(
        new CloudFoundryCluster("dev", "app-dev1", emptySet(), emptySet()),
        new CloudFoundryCluster("dev", "app-dev2", emptySet(), emptySet()),
        new CloudFoundryCluster("prod", "app-prod", emptySet(), emptySet())
      ),
      emptyMap()
    );

    Map<String, Set<String>> clusterNames = app.getClusterNames();

    assertThat(clusterNames).hasSize(2);
    assertThat(clusterNames.get("dev")).containsExactlyInAnyOrder("app-dev1", "app-dev2");
    assertThat(clusterNames.get("prod")).containsExactly("app-prod");
  }
}