/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.spinnaker.orca.libdiffs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The class under test documents itself as a "Groovy implementation of python LooseVersion class",
 * where {@code LooseVersion("1.2.3") > LooseVersion("1.2")} is True.
 */
class DefaultComparableLooseVersionTest {

  private final DefaultComparableLooseVersion subject = new DefaultComparableLooseVersion();

  @Test
  void equalLengthComparisonsWork() {
    // These mirror what LibraryDiffSpec already covers (same number of components).
    assertThat(subject.compare("1.3", "1.2")).isPositive();
    assertThat(subject.compare("7.0.59", "7.0.55")).isPositive();
    assertThat(subject.compare("5.5.0", "5.4.0")).isPositive();
  }

  @Test
  void longerVersionWithMoreComponentsIsGreater() {
    // 1.2.3 is a newer version than 1.2, so the comparison must be positive.
    assertThat(subject.compare("1.2.3", "1.2")).isPositive();
  }

  @Test
  void comparisonIsAntisymmetric() {
    int forward = subject.compare("1.2.3", "1.2");
    int backward = subject.compare("1.2", "1.2.3");
    // sign(compare(a,b)) must == -sign(compare(b,a))
    assertThat(Integer.signum(forward)).isEqualTo(-Integer.signum(backward));
  }
}
