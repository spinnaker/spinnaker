/*
 * Copyright 2020 Google, LLC
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

package com.netflix.spinnaker.clouddriver.kubernetes.description;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class KubernetesCoordinatesTest {
  @ParameterizedTest
  @MethodSource("parseNameCases")
  void parseName(ParseTestCase testCase) {
    KubernetesCoordinates coordinates =
        KubernetesCoordinates.builder().fullResourceName(testCase.fullResourceName).build();

    assertThat(coordinates.getKind()).isEqualTo(testCase.expectedKind);
    assertThat(coordinates.getName()).isEqualTo(testCase.expectedName);
  }

  static Stream<ParseTestCase> parseNameCases() {
    return Stream.of(
        new ParseTestCase("replicaSet abc", KubernetesKind.REPLICA_SET, "abc"),
        new ParseTestCase("replicaSet abc", KubernetesKind.REPLICA_SET, "abc"),
        new ParseTestCase("rs abc", KubernetesKind.REPLICA_SET, "abc"),
        new ParseTestCase("service abc", KubernetesKind.SERVICE, "abc"),
        new ParseTestCase("SERVICE abc", KubernetesKind.SERVICE, "abc"),
        new ParseTestCase("ingress abc", KubernetesKind.INGRESS, "abc"));
  }

  @RequiredArgsConstructor
  private static class ParseTestCase {
    final String fullResourceName;
    final KubernetesKind expectedKind;
    final String expectedName;
  }
}
