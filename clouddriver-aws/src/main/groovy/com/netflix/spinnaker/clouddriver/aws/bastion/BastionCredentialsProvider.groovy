/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.bastion

import com.aestasit.ssh.SshOptions
import com.aestasit.ssh.dsl.CommandOutput
import com.aestasit.ssh.dsl.SshDslEngine
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicSessionCredentials
import groovy.json.JsonSlurper

import java.text.SimpleDateFormat

class BastionCredentialsProvider implements AWSCredentialsProvider {
  private static final JsonSlurper slurper = new JsonSlurper()

  private final String user
  private final String host
  private final Integer port
  private final String proxyCluster
  private final String proxyRegion
  private final String iamRole

  private Date expiration
  private AWSCredentials credentials
  private final String sshKey

  BastionCredentialsProvider(String user, String host, Integer port, String proxyCluster, String proxyRegion, String iamRole) {
    this.user = user ?: System.properties["user.name"]
    this.host = host
    this.port = port
    this.proxyCluster = proxyCluster
    this.proxyRegion = proxyRegion
    this.iamRole = iamRole
    File sshDir = new File(System.getProperty('user.home'), '.ssh')
    this.sshKey = (["id_dsa", "id_rsa"].findResult {
      def key = new File(sshDir, it)
      key.exists() ? key : null
    } ?: new File(sshDir, "id_rsa")).absolutePath
  }

  @Override
  AWSCredentials getCredentials() {
    if (!expiration || expiration.before(new Date())) {
      this.credentials = getRemoteCredentials()
    }
    this.credentials
  }

  @Override
  void refresh() {
    this.credentials = getRemoteCredentials()
  }

  private AWSCredentials getRemoteCredentials() {
    SimpleDateFormat format = new SimpleDateFormat(
      "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    def engine = new SshDslEngine(new SshOptions(defaultKeyFile: new File(sshKey)))
    def command = "oq-ssh -r ${proxyRegion} ${proxyCluster},0 'curl -s http://169.254.169.254/latest/meta-data/iam/security-credentials/${iamRole}'".toString()
    CommandOutput output = engine.remoteSession("${user}@${host}:${port}") {
      exec command: command
    }
    def jsonText = output.output.substring(output.output.indexOf('{'))
    def json = slurper.parseText(jsonText) as Map
    expiration = format.parse(json.Expiration as String)
    new BasicSessionCredentials(json.AccessKeyId as String, json.SecretAccessKey as String, json.Token as String)
  }
}
