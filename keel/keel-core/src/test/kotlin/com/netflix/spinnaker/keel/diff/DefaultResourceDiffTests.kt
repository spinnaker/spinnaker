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
package com.netflix.spinnaker.keel.diff

import de.danielbechler.diff.node.DiffNode.State.CHANGED
import de.danielbechler.diff.node.DiffNode.State.UNTOUCHED
import de.danielbechler.diff.node.ToMapPrintingVisitor
import de.danielbechler.diff.path.NodePath
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.containsKey
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.time.Duration

internal class DefaultResourceDiffTests {
  private data class Container(val prop: Parent)

  private interface Parent {
    val commonProp: String
  }

  private data class Child1(
    override val commonProp: String,
    val myProp: String
  ) : Parent

  private data class Child2(
    override val commonProp: String,
    val anotherProp: String
  ) : Parent

  /**
   * Diffing should work between objects of the same parent, but different sub classes.
   *
   * https://github.com/spinnaker/keel/issues/317
   */
  @Test
  fun `detects a delta in the type of a polymorphic property`() {
    val obj1 = Container(Child1("common", "myprop"))
    val obj2 = Container(Child2("common", "anotherProp"))

    with(DefaultResourceDiff(obj1, obj2)) {
      expectThat(diff.state) isEqualTo CHANGED
    }
  }

  @Test
  fun `does not detect a delta in two different types of empty map`() {
    val obj1 = emptyMap<Any, Any>()
    val obj2 = LinkedHashMap<Any, Any>()

    with(DefaultResourceDiff(obj1, obj2)) {
      expectThat(diff.state) isEqualTo UNTOUCHED
    }
  }

  private data class DurationContainer(val duration: Duration)

  @Test
  fun `treats a Duration property as a single value`() {
    val obj1 = DurationContainer(Duration.ofSeconds(1))
    val obj2 = DurationContainer(Duration.ofSeconds(2))

    with(DefaultResourceDiff(obj1, obj2)) {
      expect {
        that(diff.state) isEqualTo CHANGED
        that(diff.getChild("duration")) {
          get { state } isEqualTo CHANGED
          get { childCount() } isEqualTo 0
        }
      }
    }
  }

  @Test
  fun `treats a Duration value as a single value`() {
    val obj1 = Duration.ofSeconds(1)
    val obj2 = Duration.ofSeconds(2)

    with(DefaultResourceDiff(obj1, obj2)) {
      expect {
        that(diff.state) isEqualTo CHANGED
        that(diff.childCount()) isEqualTo 0
      }
    }
  }

  private fun <T : Any> DefaultResourceDiff<T>.toMap(): Map<String, String> {
    val visitor = ToMapPrintingVisitor(desired, current)
    diff.visitChildren(visitor)
    return visitor.messages.mapKeys { (k, _) -> k.toString() }
  }
}
