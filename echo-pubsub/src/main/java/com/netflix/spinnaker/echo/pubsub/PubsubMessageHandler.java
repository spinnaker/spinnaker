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

package com.netflix.spinnaker.echo.pubsub;

import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Metadata;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem;
import com.netflix.spinnaker.echo.pipelinetriggers.monitor.PubsubEventMonitor;
import com.netflix.spinnaker.echo.pubsub.model.MessageAcknowledger;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.PostConstruct;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared cache of received and handled pubsub messages to synchronize clients.
 */
@Data
@Service
@Slf4j
public class PubsubMessageHandler {

  @Autowired
  private JedisPool jedisPool;

  @Autowired
  private PubsubEventMonitor pubsubEventMonitor;

  private MessageDigest digest;

  private static final String SET_IF_NOT_EXIST = "NX";
  private static final String SET_EXPIRE_TIME_MILLIS = "PX";
  private static final String SUCCESS = "OK";

  @PostConstruct
  void postConstruct() {
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException nsa) {
      log.error(nsa.getMessage());
    }
  }

  public void handleMessage(MessageDescription description,
                            MessageAcknowledger acknowledger,
                            String identifier) {
    String messageKey = makeKey(description.getMessagePayload(), description.getPubsubSystem(), description.getSubscriptionName());

    if (!acquireMessageLock(messageKey, identifier, description.getAckDeadlineMillis())) {
      acknowledger.nack();
      return;
    }

    acknowledger.ack();
    processEvent(description);
    setMessageHandled(messageKey, identifier, description.getRetentionDeadlineMillis());
  }

  private Boolean acquireMessageLock(String messageKey, String identifier, Long ackDeadlineMillis) {
    try (Jedis resource = jedisPool.getResource()) {
      String response = resource.set(messageKey, identifier, SET_IF_NOT_EXIST, SET_EXPIRE_TIME_MILLIS, ackDeadlineMillis);
      return SUCCESS.equals(response);
    }
  }

  private void setMessageHandled(String messageKey, String identifier, Long retentionDeadlineMillis) {
    try (Jedis resource = jedisPool.getResource()) {
      resource.psetex(messageKey, retentionDeadlineMillis, identifier);
    }
  }

  private String makeKey(String messagePayload, PubsubSystem pubsubSystem, String subscription) {
    digest.reset();
    digest.update(messagePayload.getBytes());
    String messageHash = new String(Base64.getEncoder().encode(digest.digest()));
    return String.format("%s:echo-pubsub:%s:%s", pubsubSystem.toString(), subscription, messageHash);
  }


  private void processEvent(MessageDescription description) {
    log.debug("Processed Pubsub event with payload {}", description.getMessagePayload());

    Event event = new Event();
    Map<String, Object> content = new HashMap<>();
    Metadata details = new Metadata();

    content.put("messageDescription", description);
    details.setType(PubsubEventMonitor.PUBSUB_TRIGGER_TYPE);

    event.setContent(content);
    event.setDetails(details);
    pubsubEventMonitor.processEvent(event);
  }
}
