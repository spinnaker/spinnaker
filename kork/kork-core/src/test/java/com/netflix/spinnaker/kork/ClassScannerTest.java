/*
 * Copyright 2024 Apple, Inc.
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

package com.netflix.spinnaker.kork;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClassScannerTest {
  interface BaseType {}

  static class ChildType implements BaseType {}

  static class GrandchildType extends ChildType {}

  @Test
  void scanForClasses() {
    var classes = ClassScanner.forBaseType(BaseType.class).addClassPackage(getClass()).scan();
    assertThat(classes)
        .hasSize(2)
        .contains(ChildType.class, GrandchildType.class)
        .doesNotContain(BaseType.class);
  }
}
