/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.gate.security.saml

import spock.lang.Specification
import spock.lang.Unroll

class SAMLSecurityConfigPropertiesSpec extends Specification {
  @Unroll
  def "should validate that the keystore exists and the password/alias are valid"() {
    given:
    def ssoConfig = new SamlSsoConfig.SAMLSecurityConfigProperties(
      keyStore: keyStore.toString(), keyStorePassword: keyStorePassword, keyStoreAliasName: keyStoreAliasName
    )

    expect:
    try {
      ssoConfig.validate()
      assert !expectsException
    } catch (Exception ignored) {
      assert expectsException
    }

    try {
      // ensure validation works if a keystore is not prefixed with "file:"
      ssoConfig.keyStore = ssoConfig ? ssoConfig.keyStore.replaceAll("file:", "") : null
      ssoConfig.validate()
      assert !expectsException
    } catch (Exception ignored) {
      assert expectsException
    }

    where:
    keyStore                                      | keyStorePassword | keyStoreAliasName || expectsException
    this.class.getResource("/does-not-exist.jks") | null             | null              || true        // keystore does not exist
    this.class.getResource("/saml-client.jks")    | "invalid"        | "saml-client"     || true        // password is invalid
    this.class.getResource("/saml-client.jks")    | "123456"         | "invalid"         || true        // alias is invalid
    this.class.getResource("/saml-client.jks")    | "123456"         | "saml-client"     || false
  }
}
