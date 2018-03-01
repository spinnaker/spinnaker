package com.netflix.spinnaker.orca.q

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe

/*
 * Copyright 2017 Netflix, Inc.
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

object TotalThrottleTimeAttributeSpec : Spek({

  describe("defaults to zero") {
    val attr = TotalThrottleTimeAttribute()
    assertThat(attr.totalThrottleTimeMs).isEqualTo(0)
  }

  describe("uses default") {
    val attr = TotalThrottleTimeAttribute(3)
    assertThat(attr.totalThrottleTimeMs).isEqualTo(3)
  }

  describe("add is additive") {
    val attr = TotalThrottleTimeAttribute(4)
    attr.add(5)
    assertThat(attr.totalThrottleTimeMs).isEqualTo(9)
  }
})
