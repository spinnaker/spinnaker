package com.netflix.spinnaker.orca.q

import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.junit.Assert

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
    Assert.assertEquals(0, attr.totalThrottleTimeMs)
  }

  describe("uses default") {
    val attr = TotalThrottleTimeAttribute(3)
    Assert.assertEquals(3, attr.totalThrottleTimeMs)
  }

  describe("add is additive") {
    val attr = TotalThrottleTimeAttribute(4)
    attr.add(5)
    Assert.assertEquals(9, attr.totalThrottleTimeMs)
  }
})
