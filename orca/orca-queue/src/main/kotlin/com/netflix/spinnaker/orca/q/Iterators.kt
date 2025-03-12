/*
 * Copyright 2017 Netflix, Inc.
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
 */

package com.netflix.spinnaker.orca.q

/**
 * Iterate with information about whether the current element is first or last
 * and what index it is.
 */
fun <T> ListIterator<T>.forEachWithMetadata(block: (IteratorElement<T>) -> Unit) {
  while (hasNext()) {
    val first = !hasPrevious()
    val index = nextIndex()
    val value = next()
    val last = !hasNext()
    block.invoke(IteratorElement(value, index, first, last))
  }
}

data class IteratorElement<out T>(
  val value: T,
  val index: Int,
  val isFirst: Boolean,
  val isLast: Boolean
)

/**
 * Groovy-style sublist using range. For example:
 *
 *     assert(listOf(1, 2, 3)[1..2] == listOf(2, 3))
 */
operator fun <E> List<E>.get(indices: IntRange): List<E> =
  subList(indices.start, indices.endInclusive + 1)
