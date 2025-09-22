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

package com.netflix.spinnaker.kork.secrets.env

import com.netflix.spinnaker.kork.secrets.SecretParameter
import com.netflix.spinnaker.kork.secrets.SecretReference
import com.netflix.spinnaker.kork.secrets.SecretReferenceResolver
import org.junit.jupiter.api.Test
import org.springframework.core.env.get
import org.springframework.mock.env.MockEnvironment
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class SecretReferenceResolversTest {
  object ValueParameter : SecretParameter {
    override val parameterName: String = "v"
  }

  object MockResolver : SecretReferenceResolver {
    override fun supports(reference: SecretReference): Boolean =
      reference.engineIdentifier == "mock"

    override fun resolve(reference: SecretReference): String =
      reference.getRequiredParameter(ValueParameter)
  }

  private val resolvers = SecretReferenceResolvers(listOf(MockResolver))

  @Test
  fun `can resolve boot secret values from environment`() {
    val environment = MockEnvironment()
      .withProperty("test.property[0]", "boot-secret://mock?v=test-value")
      .withProperty("test.property[1]", "not-secret:test-value")
      .withProperty("test.property[2]", "boot-secret://mock?v=test-value-again")
      .withProperty("test.property[3]", "regular value")
      .withProperty("test.value", "boot-secret://mock?v=hello")

    resolvers.registerResolvedSecrets(environment.propertySources)

    expect {
      that(environment["test.property[0]"]) isEqualTo "test-value"
      that(environment["test.property[1]"]) isEqualTo "not-secret:test-value"
      that(environment["test.property[2]"]) isEqualTo "test-value-again"
      that(environment["test.property[3]"]) isEqualTo "regular value"
      that(environment["test.value"]) isEqualTo "hello"
    }
  }

  @Test
  fun `can resolve mock secret`() {
    expectThat(resolvers.resolveSecretIfPresent("boot-secret://mock?v=test-value")) {
      isEqualTo("test-value")
    }
  }
}
