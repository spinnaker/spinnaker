/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.cache

import com.amazonaws.services.ec2.model.Instance
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsInstanceCacheClient
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES

class EcsInstanceCacheClientSpec extends Specification {
  def cacheView = Mock(Cache)
  def objectMapper = new ObjectMapper()
                          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  @Subject
  def client = new EcsInstanceCacheClient(cacheView, objectMapper)

  def 'should convert into an object'() {
    given:
    def instanceId = 'instance-id'
    def key = Keys.getInstanceKey(instanceId, 'test-account', 'us-west-1')
    def givenInstance = new Instance(
      instanceId: instanceId,
      privateIpAddress: '127.0.0.1',
      publicDnsName: 'localhost',
      launchTime: new Date()
    )

    def attributes = objectMapper.convertValue(givenInstance, Map)
    def instanceCache = new DefaultCacheData(key, attributes, [:])

    cacheView.filterIdentifiers(INSTANCES.getNs(), _) >> [key]
    cacheView.getAll(INSTANCES.getNs(), [key]) >> [instanceCache]

    when:
    def foundInstances = client.findAll()

    then:
    foundInstances.size() == 1
    foundInstances[0] == givenInstance
  }
}
