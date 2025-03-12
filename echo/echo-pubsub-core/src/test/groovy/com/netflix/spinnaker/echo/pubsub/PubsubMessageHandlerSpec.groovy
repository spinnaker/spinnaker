/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.pubsub

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.artifacts.MessageArtifactTranslator
import com.netflix.spinnaker.echo.events.EventPropagator
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription
import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem
import com.netflix.spinnaker.echo.pubsub.model.EventCreator
import com.netflix.spinnaker.echo.pubsub.model.MessageAcknowledger
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientSelector
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import java.security.MessageDigest

class PubsubMessageHandlerSpec extends Specification {

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  @Shared
  RedisClientDelegate redisClientDelegate

  @Shared
  Optional<RedisClientSelector> redisClientSelector

  MessageDigest messageDigest = MessageDigest.getInstance("SHA-256")

  EventPropagator eventPropagator = Mock(EventPropagator)

  PubsubMessageHandler.Factory pubsubMessageHandlerFactory = new PubsubMessageHandler.Factory(
    eventPropagator,
    redisClientSelector,
    new NoopRegistry()
  )

  EventCreator eventCreator = new PubsubEventCreator(Optional.empty())

  @Subject
  PubsubMessageHandler pubsubMessageHandler = pubsubMessageHandlerFactory.create(eventCreator)

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
    redisClientDelegate = new JedisClientDelegate("primaryDefault", embeddedRedis.getPool())
    redisClientSelector = Optional.of(new RedisClientSelector([redisClientDelegate]))
  }

  def cleanup() {
    embeddedRedis.jedis.withCloseable { it.flushDB() }
  }

  def "acquiring lock succeeds when no lock key is present"() {
    given:
    String key = 'key'
    String id = 'id'
    Integer ackDeadline = 1

    when:
    def resp = pubsubMessageHandler.acquireMessageLock(key, id, ackDeadline)

    then:
    resp // Response is Boolean.
  }

  def "acquiring lock fails when a lock key is present"() {
    given:
    String key = 'key'
    String id = 'id'
    Integer ackDeadline = 1

    when:
    pubsubMessageHandler.acquireMessageLock(key, id, ackDeadline)
    def resp = pubsubMessageHandler.acquireMessageLock(key, id, ackDeadline)

    then:
    !resp // Response is Boolean.
  }

  def "can reacquire lock after expiration"() {
    given:
    String key = 'key'
    String id = 'id'
    Integer ackDeadline = 1

    when:
    pubsubMessageHandler.acquireMessageLock(key, id, ackDeadline)
    sleep(1005)
    def resp = pubsubMessageHandler.acquireMessageLock(key, id, ackDeadline)

    then:
    resp // Response is Boolean.
  }

  def "acquiring lock fails after a message is handled"() {
    given:
    String key = 'key'
    String id = 'id'
    Integer ackDeadline = 1
    Integer retentionDeadline = 5

    when:
    pubsubMessageHandler.setMessageHandled(key, id, retentionDeadline)
    def resp = pubsubMessageHandler.acquireMessageLock(key, id, ackDeadline)

    then:
    !resp // Response is Boolean.
  }

  def "event processed on pubsub message if it hasn't been processed already"() {
    given:
    String messageId = 'X'
    MessageDescription description = MessageDescription.builder()
    .subscriptionName('subscriptionName')
    .messagePayload('THE TRUTH IS OUT THERE')
    .pubsubSystem(PubsubSystem.GOOGLE)
    .ackDeadlineSeconds(1)
    .retentionDeadlineSeconds(2)
    .build()

    def acker = Mock(MessageAcknowledger)
    String id = 'id'

    when:
    pubsubMessageHandler.handleMessage(description, acker, id, messageId)

    then:
    1 * eventPropagator.processEvent(_)
    1 * acker.ack()
    0 * acker.nack() // Lock acquisition failed.
  }

  def "message gets handled only once while it's retained in the topic"() {
    given:
    String messageId = 'X'
    MessageDescription description = MessageDescription.builder()
        .subscriptionName('subscriptionName')
        .messagePayload('THE TRUTH IS OUT THERE')
        .pubsubSystem(PubsubSystem.GOOGLE)
        .ackDeadlineSeconds(1)
        .retentionDeadlineSeconds(2)
        .build()

    def acker = Mock(MessageAcknowledger)
    String id = 'id'

    when:
    pubsubMessageHandler.handleMessage(description, acker, id, messageId)
    pubsubMessageHandler.handleMessage(description, acker, id, messageId)

    then:
    1 * eventPropagator.processEvent(_)
    2 * acker.ack() // duplicate is dismissed
  }
}
