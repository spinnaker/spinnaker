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

import static org.assertj.core.api.Assertions.assertThat;

class CloudFoundrySpaceTest {

  @Test
  void fromRegion() {
    CloudFoundrySpace space = CloudFoundrySpace.fromRegion("org>space");

    assertThat(space.getName()).isEqualTo("space");
    assertThat(space.getOrganization().getName()).isEqualTo("org");

    space = CloudFoundrySpace.fromRegion("org > space");

    assertThat(space.getName()).isEqualTo("space");
    assertThat(space.getOrganization().getName()).isEqualTo("org");
  }

  @Test
  void equality() {
    CloudFoundrySpace space = CloudFoundrySpace.fromRegion("org>space");
    CloudFoundrySpace space2 = CloudFoundrySpace.fromRegion("org > space");

    assertThat(space).isEqualTo(space2);
    assertThat(space.hashCode()).isEqualTo(space2.hashCode());
  }
}