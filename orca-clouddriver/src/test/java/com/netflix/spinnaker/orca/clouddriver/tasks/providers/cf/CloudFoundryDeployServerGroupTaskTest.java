/*
 * Copyright 2020 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CloudFoundryDeployServerGroupTaskTest {

  private ObjectMapper mapper =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  @Test
  void mapperShouldConvertFormBasedManifest() {
    DeploymentManifest.Direct direct = new DeploymentManifest.Direct();
    direct.setBuildpacks(Collections.emptyList());
    direct.setEnv(Collections.emptyMap());
    direct.setEnvironment(Collections.emptyList());
    direct.setRoutes(Collections.emptyList());
    direct.setDiskQuota("1024M");
    direct.setMemory("1024M");

    Map<String, DeploymentManifest.Direct> input = ImmutableMap.of("direct", direct);

    String manifestYmlBytes = Base64.getEncoder().encodeToString(direct.toManifestYml().getBytes());
    DeploymentManifest result = mapper.convertValue(input, DeploymentManifest.class);
    assertThat(result.getArtifact()).isNotNull();
    assertThat(result.getArtifact().getReference()).isEqualTo(manifestYmlBytes);
  }
}
