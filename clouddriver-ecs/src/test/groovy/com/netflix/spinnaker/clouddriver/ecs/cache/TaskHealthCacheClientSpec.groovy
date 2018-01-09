/*
 * Copyright 2017 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskHealthCacheClient
import com.netflix.spinnaker.clouddriver.ecs.cache.model.TaskHealth
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TaskHealthCachingAgent
import spock.lang.Specification
import spock.lang.Subject

class TaskHealthCacheClientSpec extends Specification {
  def cacheView = Mock(Cache)
  @Subject
  private final TaskHealthCacheClient client = new TaskHealthCacheClient(cacheView)

  def 'should convert'() {
    given:
    def taskId = '1dc5c17a-422b-4dc4-b493-371970c6c4d6'
    def key = Keys.getTaskHealthKey('test-account', 'us-west-1', taskId)

    def originalTaskHealth = new TaskHealth(
      taskId: 'task-id',
      type: 'type',
      instanceId: 'i-deadbeef',
      state: 'RUNNING',
      taskArn: 'task-arn',
      serviceName: 'service-name'
    )

    def attributes = TaskHealthCachingAgent.convertTaskHealthToAttributes(originalTaskHealth)
    cacheView.get(Namespace.HEALTH.toString(), key) >> new DefaultCacheData(key, attributes, Collections.emptyMap())

    when:
    def retrievedTaskHealth = client.get(key)

    then:
    retrievedTaskHealth == originalTaskHealth
  }
}
