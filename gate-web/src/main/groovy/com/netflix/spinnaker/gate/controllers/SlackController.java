/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.gate.controllers;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.gate.config.SlackConfigProperties;
import com.netflix.spinnaker.gate.services.SlackService;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import io.swagger.annotations.ApiOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/slack")
@ConditionalOnProperty(
    prefix = "slack",
    name = {"token", "baseUrl"})
public class SlackController {

  private static final Logger log = LoggerFactory.getLogger(SlackController.class);
  private final Registry registry;
  private final AtomicReference<List<Map>> slackChannelsCache =
      new AtomicReference<>(new ArrayList<>());

  private final SlackConfigProperties slackConfigProperties;
  private final SlackService slackService;

  @Autowired
  public SlackController(
      SlackService slackService, SlackConfigProperties slackConfigProperties, Registry registry) {
    this.slackService = slackService;
    this.slackConfigProperties = slackConfigProperties;
    this.registry = registry;
  }

  @ApiOperation("Retrieve a list of public slack channels")
  @RequestMapping("/channels")
  public List<Map> getChannels() {
    return slackChannelsCache.get();
  }

  @Scheduled(
      fixedDelayString = "${slack.channel-refresh-interval-millis:1200000}",
      initialDelayString = "${random.int(600000)}")
  void refreshSlack() {
    try {
      log.info("Refreshing Slack channels list");
      List<Map> channels = fetchChannels();
      log.info("Fetched {} Slack channels", channels.size());
      slackChannelsCache.set(channels);
    } catch (Exception e) {
      registry.counter("slack.channels.errors").increment();
      log.error("Unable to refresh Slack channels list", e);
    }
  }

  List<Map> fetchChannels() {
    SlackService.SlackChannelsResult response =
        AuthenticatedRequest.allowAnonymous(
            () -> slackService.getChannels(slackConfigProperties.getToken(), null));
    List<Map> channels = response.channels;
    String cursor = response.response_metadata.next_cursor;
    while (cursor != null & cursor.length() > 0) {
      response = slackService.getChannels(slackConfigProperties.getToken(), cursor);
      cursor = response.response_metadata.next_cursor;
      channels.addAll(response.channels);
    }

    return channels;
  }
}
