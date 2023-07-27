/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.description.manifest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class KubernetesManifestOwnerRefTest {

  @ParameterizedTest
  @CsvSource(
      value = {
        "{\"kind\":\"Pod\"}|pod",
        "{\"kind\":\"Deployment\",\"apiVersion\":\"apps/v1\"}|deployment",
        "{\"kind\":\"Custom\",\"apiVersion\":\"mygroup/v1\"}|Custom.mygroup",
      },
      delimiter = '|')
  public void testOwnerRef(String referenceAsJson, String computedKind)
      throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    KubernetesManifest.OwnerReference ref =
        objectMapper.readValue(referenceAsJson, KubernetesManifest.OwnerReference.class);
    assertThat(ref).isNotNull();
    assertThat(ref.computedKind().toString()).isEqualTo(computedKind);
  }
}
