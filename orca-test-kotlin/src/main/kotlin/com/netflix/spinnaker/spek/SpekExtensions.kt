/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.spek

import org.funktionale.partials.partially2
import org.jetbrains.spek.api.dsl.SpecBody

/**
 * Grammar for nesting inside [given].
 */
fun SpecBody.and(description: String, body: SpecBody.() -> Unit) {
  group("and $description", body = body)
}

fun <A> SpecBody.where(description: String, vararg parameterSets: Single<A>, block: SpecBody.(A) -> Unit) {
  parameterSets.forEach {
    val body = block.partially2(it.first)
    group(com.netflix.spinnaker.spek.describe(description, it.first), body = body)
  }
}

fun <A, B> SpecBody.where(description: String, vararg parameterSets: Pair<A, B>, block: SpecBody.(A, B) -> Unit) {
  parameterSets.forEach {
    val body = block.partially2(it.first).partially2(it.second)
    group(com.netflix.spinnaker.spek.describe(description, it.first, it.second), body = body)
  }
}

fun <A, B, C> SpecBody.where(description: String, vararg parameterSets: Triple<A, B, C>, block: SpecBody.(A, B, C) -> Unit) {
  parameterSets.forEach {
    val body = block.partially2(it.first).partially2(it.second).partially2(it.third)
    group(com.netflix.spinnaker.spek.describe(description, it.first, it.second, it.third), body = body)
  }
}

fun <A, B, C, D> SpecBody.where(description: String, vararg parameterSets: Quad<A, B, C, D>, block: SpecBody.(A, B, C, D) -> Unit) {
  parameterSets.forEach {
    val body = block.partially2(it.first).partially2(it.second).partially2(it.third).partially2(it.fourth)
    group(com.netflix.spinnaker.spek.describe(description, it.first, it.second, it.third, it.fourth), body = body)
  }
}

private fun describe(description: String, vararg arguments: Any?) =
  String.format("where $description", *arguments)

data class Single<out A>(val first: A) {
  override fun toString() = "($first)"
}

data class Quad<out A, out B, out C, out D>(val first: A, val second: B, val third: C, val fourth: D) {
  override fun toString() = "($first, $second, $third, $fourth)"
}

fun <A> values(first: A) =
  Single(first)

fun <A, B> values(first: A, second: B) =
  Pair(first, second)

fun <A, B, C> values(first: A, second: B, third: C) =
  Triple(first, second, third)

fun <A, B, C, D> values(first: A, second: B, third: C, fourth: D) =
  Quad(first, second, third, fourth)
