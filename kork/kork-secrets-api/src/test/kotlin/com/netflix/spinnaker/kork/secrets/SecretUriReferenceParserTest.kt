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

package com.netflix.spinnaker.kork.secrets

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.all
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull

class SecretUriReferenceParserTest {
  private val opaque = SecretUriReferenceParser("test-secret:", ".", ",", SecretUriType.OPAQUE)
  private val hierarchical = SecretUriReferenceParser("test-secret://", ";", "=", SecretUriType.HIERARCHICAL)

  @ParameterizedTest
  @ValueSource(strings = [
    "test-secret",
    "test-secret:",
    "test-secret://",
    "test-secret:engine",
    "test-secret://engine",
    "test:engine.key,value",
    "test-secret:engine.key",
    "test-secret:engine.key,value.key,value",
  ])
  fun invalidOpaqueInput(input: String) {
    expectThrows<InvalidSecretFormatException> {
      opaque.parse(input)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "test-secret:engine.k,key",
    "test-secret:engine.k,key.v,value",
  ])
  fun validOpaqueInput(input: String) {
    expectThat(opaque.parse(input)) {
      get { scheme } isEqualTo "test-secret"
      get { engineIdentifier } isEqualTo "engine"
      get { getParameter(StandardSecretParameter.KEY) }.isNotNull() isEqualTo "key"
    }
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "test-secret",
    "test-secret:",
    "test-secret://",
    "test-secret:engine",
    "test-secret://engine",
    "test://engine?key=value",
    "test-secret://engine?key",
    "test-secret://engine?key=value;key=value",
  ])
  fun invalidHierarchicalInput(input: String) {
    expectThrows<InvalidSecretFormatException> { hierarchical.parse(input) }
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "test-secret://engine?key=value",
    "test-secret://engine?k1=value;k2=value",
    "test-secret://engine?k1=value;k2=value;k3=value",
  ])
  fun validHierarchicalInput(input: String) {
    expectThat(hierarchical.parse(input)) {
      get { scheme } isEqualTo "test-secret"
      get { engineIdentifier } isEqualTo "engine"
      get { parameters.values }.all { isEqualTo("value") }
    }
  }
}
