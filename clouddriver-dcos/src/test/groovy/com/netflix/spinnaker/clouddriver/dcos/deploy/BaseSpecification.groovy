/*
 * Copyright 2018 Cerner Corporation
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

package com.netflix.spinnaker.clouddriver.dcos.deploy

import com.netflix.spinnaker.clouddriver.dcos.DcosClientCompositeKey
import com.netflix.spinnaker.clouddriver.dcos.DcosConfigurationProperties
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.security.DcosClusterCredentials
import mesosphere.dcos.client.Config
import mesosphere.dcos.client.model.DCOSAuthCredentials
import spock.lang.Specification

class BaseSpecification extends Specification {
  public static final DEFAULT_ACCOUNT = 'test'
  public static final DEFAULT_REGION = 'us-test-1'
  public static final DEFAULT_GROUP = 'default'
  public static final DEFAULT_SECRET_STORE = 'default'
  public static
  final DEFAULT_COMPOSITE_KEY = DcosClientCompositeKey.buildFromVerbose(DEFAULT_ACCOUNT, DEFAULT_REGION).get()
  public static final BAD_ACCOUNT = 'bad-acct'
  public static final DEFAULT_DCOS_UID = "default_uid"

  def defaultCredentialsBuilder() {

    def dcosAuthCreds = Mock(DCOSAuthCredentials) {
      getUid() >> DEFAULT_DCOS_UID
    }

    DcosAccountCredentials.builder().account(DEFAULT_ACCOUNT).environment('test').accountType('test').requiredGroupMembership([])
      .dockerRegistries([new DcosConfigurationProperties.LinkedDockerRegistryConfiguration(accountName: 'dockerReg')])
      .clusters([DcosClusterCredentials.builder().key(DEFAULT_COMPOSITE_KEY).dcosUrl('https://test.url.com').secretStore('default').dcosConfig(Config.builder().withCredentials(dcosAuthCreds).build()).build()])
  }

  def emptyCredentialsBuilder() {
    new DcosAccountCredentials(null, null, null, null, null, null)
  }
}
