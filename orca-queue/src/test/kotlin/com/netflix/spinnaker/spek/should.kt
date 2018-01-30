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

package com.netflix.spinnaker.spek

import com.natpryce.hamkrest.allElements
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import com.natpryce.hamkrest.should.shouldNotMatch

infix fun <T> T.shouldEqual(expected: T) {
  this shouldMatch equalTo(expected)
}

infix fun <T> T.shouldNotEqual(expected: T) {
  this shouldNotMatch equalTo(expected)
}

infix fun <E, T : Iterable<E>> T.shouldAllEqual(expected: E) {
  this shouldMatch allElements(equalTo(expected))
}
