/*
 * Copyright 2026 spinnaker.io
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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.aws.jackson.AwsSdkV2Module
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskCacheClient
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TaskCachingAgent
import software.amazon.awssdk.services.ecs.model.Container
import software.amazon.awssdk.services.ecs.model.ManagedAgent
import software.amazon.awssdk.services.ecs.model.NetworkBinding
import software.amazon.awssdk.services.ecs.model.NetworkInterface
import software.amazon.awssdk.services.ecs.model.Task
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS

class TaskCacheClientSpec extends Specification {
  def cacheView = Mock(Cache)
  // mirrors clouddriver's ObjectMapper: AwsSdkV2Module is registered as a Spring Module bean
  def objectMapper = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .registerModule(new AwsSdkV2Module())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  @Subject
  TaskCacheClient client = new TaskCacheClient(cacheView, objectMapper)

  def 'should convert cache data written by the caching agent back into a task'() {
    given:
    def taskArn = 'arn:aws:ecs:us-west-1:123456789012:task/test-cluster/1dc5c17a-422b-4dc4-b493-371970c6c4d6'
    def key = Keys.getTaskKey('test-account', 'us-west-1', '1dc5c17a-422b-4dc4-b493-371970c6c4d6')

    // a managed agent carries an Instant, the field that broke deserialization while the cache
    // client still bound to the SDK v1 Container model
    def givenTask = Task.builder()
      .taskArn(taskArn)
      .clusterArn('arn:aws:ecs:us-west-1:123456789012:cluster/test-cluster')
      .group('service:test-service')
      .lastStatus('RUNNING')
      .desiredStatus('RUNNING')
      .healthStatus('HEALTHY')
      .startedAt(Instant.ofEpochMilli(1600000000000L))
      .availabilityZone('us-west-1a')
      .containers(Container.builder()
        .name('test-container')
        .networkBindings(NetworkBinding.builder().containerPort(1338).hostPort(1338).build())
        .networkInterfaces(NetworkInterface.builder().privateIpv4Address('192.168.0.100').build())
        .managedAgents(ManagedAgent.builder()
          .name('ExecuteCommandAgent')
          .lastStatus('RUNNING')
          .lastStartedAt(Instant.ofEpochMilli(1600000000000L))
          .build())
        .build())
      .build()

    def attributes = TaskCachingAgent.convertTaskToAttributes(givenTask)
    def cachedAttributes = objectMapper.readValue(objectMapper.writeValueAsString(attributes), Map)
    cacheView.get(TASKS.ns, key) >> new DefaultCacheData(key, cachedAttributes, [:])

    when:
    def retrievedTask = client.get(key)

    then:
    retrievedTask.taskArn == taskArn
    retrievedTask.lastStatus == 'RUNNING'
    retrievedTask.containers.size() == 1
    retrievedTask.containers[0].networkBindings()[0].hostPort() == 1338
    retrievedTask.containers[0].networkInterfaces()[0].privateIpv4Address() == '192.168.0.100'
    retrievedTask.containers[0].managedAgents()[0].lastStartedAt() == Instant.ofEpochMilli(1600000000000L)
  }
}
