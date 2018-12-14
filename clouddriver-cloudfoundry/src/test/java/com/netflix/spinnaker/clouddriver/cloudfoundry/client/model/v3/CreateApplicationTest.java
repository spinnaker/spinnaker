/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


class CreateApplicationTest {

  @Test
  void getLifecycleShouldReturnMultipleBuildpacks() {
    ToOneRelationship toOneRelationship = new ToOneRelationship(new Relationship("space-guid"));
    Map<String, ToOneRelationship> relationships = Collections.singletonMap("relationship", toOneRelationship);
    List<String> buildpacks = Arrays.asList("buildpackOne", "buildpackTwo");
    CreateApplication createApplication = new CreateApplication(
      "some-application",
      relationships,
      null,
      buildpacks);

    assertThat(createApplication.getLifecycle().getData().get("buildpacks")).isEqualTo(buildpacks);
  }

  @Test
  void getLifecycleShouldReturnNull() {
    ToOneRelationship toOneRelationship = new ToOneRelationship(new Relationship("relationship-guid"));
    Map<String, ToOneRelationship> relationships = Collections.singletonMap("relationship", toOneRelationship);
    CreateApplication createApplication = new CreateApplication(
      "some-application",
      relationships,
      null,
      null);

    assertThat(createApplication.getLifecycle()).isNull();
  }
}
