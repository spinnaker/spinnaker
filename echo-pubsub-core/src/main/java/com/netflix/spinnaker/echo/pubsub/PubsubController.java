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

import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for configured pub/sub publishers and subscriptions.
 */
@RestController
@RequestMapping(value = "/pubsub")
public class PubsubController {

  @Autowired
  private PubsubSubscribers pubsubSubscribers;

  @Autowired
  private PubsubPublishers pubsubPublishers;

  @RequestMapping(value = "/subscriptions", method = RequestMethod.GET)
  List<PubsubSubscriptionBySystem> getSubscriptions() {
    return pubsubSubscribers
        .getAll()
        .stream()
        .map(s -> PubsubSubscriptionBySystem.builder()
          .pubsubSystem(s.getPubsubSystem().toString())
          .subscriptionName(s.getName())
          .build())
        .collect(Collectors.toList());
  }

  @Data
  @Builder
  public static class PubsubSubscriptionBySystem {
    private String pubsubSystem;
    private String subscriptionName;
  }

  @RequestMapping(value = "/publishers", method = RequestMethod.GET)
  List<PubsubPublishersBySystem> getPublishers() {
    return pubsubPublishers
      .getAll()
      .stream()
      .map(p -> PubsubPublishersBySystem.builder()
        .pubsubSystem(p.getPubsubSystem().toString())
        .publisherName(p.getName())
        .topicName(p.getTopicName())
        .build())
      .collect(Collectors.toList());
  }

  @Data
  @Builder
  public static class PubsubPublishersBySystem {
    private String pubsubSystem;
    private String publisherName;
    private String topicName;
  }
}
