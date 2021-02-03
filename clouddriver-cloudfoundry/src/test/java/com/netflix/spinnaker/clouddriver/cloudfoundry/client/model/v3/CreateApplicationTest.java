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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServerGroupDescription;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CreateApplicationTest {

  @Test
  void getLifecycleShouldReturnMultipleBuildpacks() {
    ToOneRelationship toOneRelationship = new ToOneRelationship(new Relationship("space-guid"));
    Map<String, ToOneRelationship> relationships =
        Collections.singletonMap("relationship", toOneRelationship);
    DeployCloudFoundryServerGroupDescription.ApplicationAttributes applicationAttributes =
        new DeployCloudFoundryServerGroupDescription.ApplicationAttributes();
    applicationAttributes.setBuildpacks(ImmutableList.of("buildpackOne", "buildpackTwo"));
    Lifecycle lifecycle = new Lifecycle(Lifecycle.Type.BUILDPACK, applicationAttributes);
    CreateApplication createApplication =
        new CreateApplication("some-application", relationships, null, lifecycle);

    assertThat(createApplication.getLifecycle().getData().get("buildpacks"))
        .isEqualTo(applicationAttributes.getBuildpacks());
  }

  @Test
  void getLifecycleShouldReturnWithBuildpackAndWithStack() {
    ToOneRelationship toOneRelationship = new ToOneRelationship(new Relationship("space-guid"));
    Map<String, ToOneRelationship> relationships =
        Collections.singletonMap("relationship", toOneRelationship);
    DeployCloudFoundryServerGroupDescription.ApplicationAttributes applicationAttributes =
        new DeployCloudFoundryServerGroupDescription.ApplicationAttributes();
    applicationAttributes.setBuildpacks(ImmutableList.of("buildpackOne"));
    applicationAttributes.setStack("cflinuxfs3");
    Lifecycle lifecycle = new Lifecycle(Lifecycle.Type.BUILDPACK, applicationAttributes);
    CreateApplication createApplication =
        new CreateApplication("some-application", relationships, null, lifecycle);

    Map<String, Object> data =
        ImmutableMap.of(
            "buildpacks", applicationAttributes.getBuildpacks(),
            "stack", applicationAttributes.getStack());

    assertThat(createApplication.getLifecycle().getData()).isEqualTo(data);
  }

  @Test
  void getLifecycleShouldReturnWithoutBuildpackAndWithStack() {
    ToOneRelationship toOneRelationship = new ToOneRelationship(new Relationship("space-guid"));
    Map<String, ToOneRelationship> relationships =
        Collections.singletonMap("relationship", toOneRelationship);
    DeployCloudFoundryServerGroupDescription.ApplicationAttributes applicationAttributes =
        new DeployCloudFoundryServerGroupDescription.ApplicationAttributes();
    applicationAttributes.setStack("cflinuxfs3");
    Lifecycle lifecycle = new Lifecycle(Lifecycle.Type.BUILDPACK, applicationAttributes);
    CreateApplication createApplication =
        new CreateApplication("some-application", relationships, null, lifecycle);

    Map<String, Object> data = ImmutableMap.of("stack", applicationAttributes.getStack());

    assertThat(createApplication.getLifecycle().getData()).isEqualTo(data);
  }
}
