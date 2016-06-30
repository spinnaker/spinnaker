/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackInstance
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.openstack4j.model.common.ActionResponse
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.INSTANCES

class OpenstackInstanceProviderSpec extends Specification {

  String account = 'test'
  String region = 'east'

  OpenstackInstanceProvider instanceProvider
  Cache cache
  ObjectMapper objectMapper
  AccountCredentialsProvider accountCredentialsProvider

  void "setup"() {
    accountCredentialsProvider = Mock(AccountCredentialsProvider)
    cache = Mock(Cache)
    objectMapper = Mock(ObjectMapper)
    instanceProvider = new OpenstackInstanceProvider(cache, accountCredentialsProvider, objectMapper)
  }

  void "test get instance"() {
    given:
    String id = 'instance'
    CacheData cacheData = Mock(CacheData)
    Map<String, Object> attributes = Mock(Map)
    String instanceKey = Keys.getInstanceKey(id, account, region)
    OpenstackInstance openstackInstance = Mock(OpenstackInstance)

    when:
    OpenstackInstance result = instanceProvider.getInstance(account, region, id)

    then:
    1 * cache.get(INSTANCES.ns, instanceKey) >> cacheData
    1 * cacheData.attributes >> attributes
    1 * objectMapper.convertValue(attributes, OpenstackInstance) >> openstackInstance
    result == openstackInstance
    noExceptionThrown()
  }

  void "test get instance - nothing found"() {
    given:
    String id = 'instance'

    when:
    OpenstackInstance result = instanceProvider.getInstance(account, region, id)

    then:
    1 * cache.get(INSTANCES.ns, Keys.getInstanceKey(id, account, region)) >> null
    0 * _
    result == null
    noExceptionThrown()
  }

  void "test get instance exception thrown"() {
    given:
    String id = 'instance'
    Throwable throwable = new RuntimeException('test')

    when:
    instanceProvider.getInstance(account, region, id)

    then:
    1 * cache.get(INSTANCES.ns, Keys.getInstanceKey(id, account, region)) >> { throw throwable }
    thrown(RuntimeException)
  }

  void "test get console output"() {
    given:
    String id = 'instance'
    OpenstackNamedAccountCredentials namedAccountCredentials = Mock(OpenstackNamedAccountCredentials)
    OpenstackCredentials openstackCredentials = GroovyMock(OpenstackCredentials)
    OpenstackClientProvider openstackClientProvider = Mock(OpenstackClientProvider)
    String output = 'output'

    when:
    String result = instanceProvider.getConsoleOutput(account, region, id)

    then:
    1 * accountCredentialsProvider.getCredentials(account) >> namedAccountCredentials
    1 * namedAccountCredentials.credentials >> openstackCredentials
    1 * openstackCredentials.provider >> openstackClientProvider
    1 * openstackClientProvider.getConsoleOutput(region, id) >> output
    result == output
    noExceptionThrown()
  }

  void "test get console output - illegal argument"() {
    given:
    String id = 'instance'

    when:
    instanceProvider.getConsoleOutput(account, region, id)

    then:
    1 * accountCredentialsProvider.getCredentials(account) >> null

    and:
    IllegalArgumentException exception = thrown(IllegalArgumentException)
    [account, region].every {
      exception.message.contains(it)
    }
  }

  void "test get console output - exception"() {
    given:
    String id = 'instance'
    OpenstackNamedAccountCredentials namedAccountCredentials = Mock(OpenstackNamedAccountCredentials)
    OpenstackCredentials openstackCredentials = GroovyMock(OpenstackCredentials)
    OpenstackClientProvider openstackClientProvider = Mock(OpenstackClientProvider)
    Throwable throwable = new OpenstackProviderException(ActionResponse.actionFailed('test', 1))

    when:
    instanceProvider.getConsoleOutput(account, region, id)

    then:
    1 * accountCredentialsProvider.getCredentials(account) >> namedAccountCredentials
    1 * namedAccountCredentials.credentials >> openstackCredentials
    1 * openstackCredentials.provider >> openstackClientProvider
    1 * openstackClientProvider.getConsoleOutput(region, id) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException == throwable
  }
}
