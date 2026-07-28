/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.security.s2s;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpinnakerServiceMapperTest {

  @ParameterizedTest
  @CsvSource({
    "spin-, spin-orca, ORCA",
    "spin-, spin-echo, ECHO",
    "spin-, orca, ORCA",
    "spin-, SPIN-ORCA, ORCA",
    "'', clouddriver, CLOUDDRIVER",
    "spin-, spin-unknownsvc, UNKNOWN",
    "spin-, , UNKNOWN"
  })
  void mapsNamesToServicesByConvention(String prefix, String rawName, SpinnakerService expected) {
    assertThat(new SpinnakerServiceMapper(prefix).map(rawName)).isEqualTo(expected);
  }

  @Test
  void nullNameIsUnknown() {
    assertThat(new SpinnakerServiceMapper("spin-").map(null)).isEqualTo(SpinnakerService.UNKNOWN);
  }

  @Test
  void doesNotStripWhenPrefixAbsent() {
    // "spinnaker" starts with neither exactly nor should collapse to a known service
    assertThat(new SpinnakerServiceMapper("spin-").map("gate")).isEqualTo(SpinnakerService.GATE);
  }
}
