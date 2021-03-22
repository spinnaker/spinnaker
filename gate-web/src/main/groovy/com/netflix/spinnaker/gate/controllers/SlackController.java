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
import com.netflix.spinnaker.kork.core.RetrySupport;
import io.swagger.annotations.ApiOperation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import retrofit.RetrofitError;

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

  private Date slackChannelsCacheLastRefreshed = null;

  private final SlackConfigProperties slackConfigProperties;
  private final SlackService slackService;
  private final RetrySupport retrySupport = new RetrySupport();

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

  @Scheduled(fixedDelayString = "${slack.channel-refresh-interval-millis:600000}")
  void refreshSlack() {
    try {
      Long startTime = System.nanoTime();
      log.info("Refreshing Slack channels");
      slackChannelsCache.set(fetchChannels());
      log.info(
          "Fetched {} Slack channels in {}ms",
          slackChannelsCache.get().size(),
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
      slackChannelsCacheLastRefreshed = new Date();
    } catch (Exception e) {
      registry.counter("slack.channels.errors").increment();
      log.error(
          "Unable to refresh Slack channels (last successful: {})",
          slackChannelsCacheLastRefreshed,
          e);
    }
  }

  private List<Map> fetchChannels() {
    SlackService.SlackChannelsResult response = getChannels(slackConfigProperties.getToken(), null);
    List<Map> channels = response.channels;
    String cursor = response.response_metadata.next_cursor;
    while (cursor != null && cursor.length() > 0) {
      response = getChannels(slackConfigProperties.getToken(), cursor);
      cursor = response.response_metadata.next_cursor;
      channels.addAll(response.channels);
    }

    return channels;
  }

  private SlackService.SlackChannelsResult getChannels(String token, String cursor) {
    return retrySupport.retry(
        () -> {
          try {
            return slackService.getChannels(token, cursor);
          } catch (Exception e) {
            long retryDelayMs = getRetryDelayMs(e).orElse(30_000L);

            try {
              log.warn(
                  "Encountered Slack error, sleeping for {}ms (cursor: {})", retryDelayMs, cursor);
              Thread.sleep(retryDelayMs);
            } catch (InterruptedException interruptedException) {
              // do nothing
            }

            throw e;
          }
        },
        12,
        Duration.ofSeconds(5),
        false);
  }

  private Optional<Long> getRetryDelayMs(Exception e) {
    if (e instanceof RetrofitError) {
      RetrofitError re = (RetrofitError) e;
      if (re.getKind() == RetrofitError.Kind.HTTP) {
        if (re.getResponse() != null && re.getResponse().getStatus() == 429) {
          return re.getResponse().getHeaders().stream()
              // slack rate limit responses may include a `Retry-After` header indicating the number
              // of seconds
              // before a subsequent request should be made.
              .filter(h -> "retry-after".equalsIgnoreCase(h.getName()))
              .findFirst()
              .map(h -> Long.parseLong(h.getValue()) * 1000);
        }
      }
    }

    return Optional.empty();
  }
}
