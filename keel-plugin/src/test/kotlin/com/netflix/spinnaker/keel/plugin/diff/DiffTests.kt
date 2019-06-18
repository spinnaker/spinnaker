/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.keel.plugin.diff

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext

/**
 * Diffing should work between objects of the same parent, but different sub classes.
 *
 * https://github.com/spinnaker/keel/issues/317
 */
internal class DiffTests : JUnit5Minutests {
  class Fixture

  interface Parent {
    val commonProp: String
  }

  data class Child1(
    override val commonProp: String,
    val myProp: String
  ) : Parent

  data class Child2(
    override val commonProp: String,
    val anotherProp: String
  ) : Parent

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    context("same parent two children") {
//      test("diffing works") {
//        val obj1: Parent = Child1("common", "myprop")
//        val obj2: Parent = Child2("common", "anotherProp")
//        val resourceDiff = ResourceDiff(obj1, obj2)
//        expectThat(resourceDiff.diff.hasChanges()).isEqualTo(true)
//      }
    }
  }
}
