/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.front50.security.aws

import com.aestasit.ssh.dsl.CommandOutput
import com.aestasit.ssh.dsl.SshDslEngine
import com.amazonaws.services.simpledb.AmazonSimpleDBClient
import groovy.json.JsonSlurper
import spock.lang.Shared
import spock.lang.Specification

import static groovy.json.JsonOutput.toJson

class BastionCredentialsProviderSpec extends Specification {
  private static final Integer SSH_PORT = 22

  @Shared
  BastionCredentialsProvider provider

  @Shared
  SshDslEngine engine

  void setup() {
    engine = Mock(SshDslEngine)
    provider = new BastionCredentialsProvider("user", "host", SSH_PORT, "proxy", "region", "iam")
    provider.engine = engine
  }

  void "provider should reach out to the bastion to get session credentials"() {
    when:
    provider.getCredentials()

    then:
    1 * engine.remoteSession(_, _) >> {
      def mock = Mock(CommandOutput)
      mock.output >> "\n"+toJson([AccessKeyId: "id", SecretAccessKey: "key", Token: "token", Expiration: "2014-01-01T12:00:00Z"])
      mock
    }
  }
}
