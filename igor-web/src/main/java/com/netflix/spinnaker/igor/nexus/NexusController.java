/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.igor.nexus;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.config.NexusProperties;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.nexus.model.NexusAssetWebhookPayload;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty("nexus.enabled")
@RequestMapping("/nexus")
@Slf4j
public class NexusController {

  private final NexusProperties nexusProperties;
  private final Optional<EchoService> echoService;
  private final Registry registry;
  private final Id missedNotificationId;

  public NexusController(
      NexusProperties nexusProperties, Optional<EchoService> echoService, Registry registry) {
    this.nexusProperties = nexusProperties;
    this.echoService = echoService;
    this.registry = registry;

    missedNotificationId = registry.createId("webhook.missedEchoNotification");
  }

  @PostMapping(path = "/webhook", consumes = "application/json")
  public void webhook(@RequestBody NexusAssetWebhookPayload payload) {
    if (!echoService.isPresent()) {
      log.warn("Cannot send build notification: Echo is not configured");
      registry
          .counter(missedNotificationId.withTag("webhook", NexusController.class.getSimpleName()))
          .increment();
    } else {
      if (payload != null) {
        new NexusEventPoster(nexusProperties, echoService.get()).postEvent(payload);
      }
    }
  }
}
