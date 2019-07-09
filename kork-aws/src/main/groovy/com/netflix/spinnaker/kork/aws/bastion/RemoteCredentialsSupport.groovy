/*
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
 */

package com.netflix.spinnaker.kork.aws.bastion

import com.aestasit.infrastructure.ssh.SshOptions
import com.aestasit.infrastructure.ssh.dsl.CommandOutput
import com.aestasit.infrastructure.ssh.dsl.SshDslEngine
import com.jcraft.jsch.IdentityRepository
import com.jcraft.jsch.agentproxy.ConnectorFactory
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository
import groovy.json.JsonSlurper

class RemoteCredentialsSupport {
  private static final JsonSlurper slurper = new JsonSlurper()
  private static final IdentityRepository identityRepository = new RemoteIdentityRepository(ConnectorFactory.default.createConnector())

  static RemoteCredentials getRemoteCredentials(String command, String user, String host, int port) {
    def engine = new SshDslEngine(
      new SshOptions(
        defaultPassword: '',
        trustUnknownHosts: true,
        jschProperties: [(SshDslEngine.SSH_PREFERRED_AUTHENTICATIONS): 'publickey']
      )
    )

    engine.jsch.setIdentityRepository(identityRepository)
    CommandOutput output = null
    engine.remoteSession("${user}@${host}:${port}") {
      output = exec command: command
    }

    def jsonText = output.output.substring(output.output.indexOf('{'))
    def json = slurper.parseText(jsonText) as Map

    return new RemoteCredentials(
      accessKeyId: json.AccessKeyId as String,
      secretAccessKey: json.SecretAccessKey as String,
      token: json.Token as String,
      expiration: json.Expiration as String
    )
  }

  static class RemoteCredentials {
    String accessKeyId
    String secretAccessKey
    String token
    String expiration
  }
}
