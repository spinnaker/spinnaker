/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.hamkrest

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.should.shouldMatch
import com.natpryce.hamkrest.throws
import kotlin.reflect.KClass

infix fun <T> T.shouldEqual(expected: T) {
  shouldMatch(equalTo(expected))
}

@Suppress("UNCHECKED_CAST")
inline infix fun <reified T : Throwable> (() -> Any?).shouldThrow(matcher: Matcher<T>) {
  (this as () -> Unit) shouldMatch throws(matcher)
}

@Suppress("UNCHECKED_CAST")
inline infix fun <reified T : Throwable> (() -> Any?).shouldThrow(@Suppress("UNUSED_PARAMETER") type: KClass<T>) {
  (this as () -> Unit) shouldMatch throws(isA<T>())
}
