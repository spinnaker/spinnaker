/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.echo.scm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.model.Event;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class StashWebhookEventHandler implements GitWebhookHandler {

  private ObjectMapper objectMapper;

  public StashWebhookEventHandler() {
    this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public boolean handles(String source) {
    return "stash".equals(source);
  }

  public void handle(Event event, Map postedEvent) {
    StashWebhookEvent stashWebhookEvent = objectMapper.convertValue(postedEvent, StashWebhookEvent.class);
    event.content.put("hash", stashWebhookEvent.refChanges.get(0).toHash);
    event.content.put("branch", stashWebhookEvent.refChanges.get(0).refId.replace("refs/heads/", ""));
    event.content.put("repoProject", stashWebhookEvent.repository.project.key);
    event.content.put("slug", stashWebhookEvent.repository.slug);
  }

  public boolean shouldSendEvent(Event event) {
    return !event.content.get("hash").toString().startsWith("000000000");
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class StashWebhookEvent {
    List<StashRefChanges> refChanges;
    StashRepository repository;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class StashRefChanges {
    String toHash;
    String refId;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class StashRepository {
    String slug;
    StashProject project;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class StashProject {
    String key;
  }

}
