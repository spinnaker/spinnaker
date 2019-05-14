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

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CloudFoundryApplicationTest {
  @Test
  void getClusterNamesGroupsByAccount() {
    CloudFoundryApplication app =
        new CloudFoundryApplication(
            "app",
            Stream.of(
                    new CloudFoundryCluster("dev", "app-dev1", emptySet()),
                    new CloudFoundryCluster("dev", "app-dev2", emptySet()),
                    new CloudFoundryCluster("prod", "app-prod", emptySet()))
                .collect(toSet()));

    Map<String, Set<String>> clusterNames = app.getClusterNames();

    assertThat(clusterNames).hasSize(2);
    assertThat(clusterNames.get("dev")).containsExactlyInAnyOrder("app-dev1", "app-dev2");
    assertThat(clusterNames.get("prod")).containsExactly("app-prod");
  }
}
