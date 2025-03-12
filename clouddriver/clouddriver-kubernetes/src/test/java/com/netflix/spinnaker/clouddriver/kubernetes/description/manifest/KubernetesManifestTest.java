/*
 * Copyright 2021 Salesforce, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.description.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import org.junit.jupiter.api.Test;

final class KubernetesManifestTest {

  private static final String GENERATE_NAME = "my-generate-name";

  @Test
  void fullResourceNameConsidersGenerateName() {
    KubernetesManifest manifest = new KubernetesManifest();

    // Job is an arbitrary choice since kubernetes supports generateName in
    // other resources.  But it's often used with jobs so it's possible to
    // run the same job multiple times.
    manifest.setKind(KubernetesKind.JOB);

    manifest.put("metadata", new HashMap<>());
    manifest.setGenerateName(GENERATE_NAME);

    // To be explicit, make sure the name is null
    assertNull(manifest.getName());

    assertThat(manifest.getFullResourceName()).isEqualTo("job " + GENERATE_NAME);
  }
}
