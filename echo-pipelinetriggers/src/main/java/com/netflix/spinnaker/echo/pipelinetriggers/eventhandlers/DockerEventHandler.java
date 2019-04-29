/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.DockerEvent;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.PatternSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Implementation of TriggerEventHandler for events of type {@link DockerEvent}, which occur when a
 * new container is pushed to a docker registry.
 */
@Component
public class DockerEventHandler extends BaseTriggerEventHandler<DockerEvent> {
  private static final String TRIGGER_TYPE = "docker";
  private static final List<String> supportedTriggerTypes = Collections.singletonList(TRIGGER_TYPE);

  @Autowired
  public DockerEventHandler(Registry registry, ObjectMapper objectMapper) {
    super(registry, objectMapper);
  }

  @Override
  public List<String> supportedTriggerTypes() {
    return supportedTriggerTypes;
  }

  @Override
  public boolean handleEventType(String eventType) {
    return eventType.equalsIgnoreCase(DockerEvent.TYPE);
  }

  @Override
  public Class<DockerEvent> getEventType() {
    return DockerEvent.class;
  }

  @Override
  public boolean isSuccessfulTriggerEvent(DockerEvent dockerEvent) {
    // The event should always report a tag
    String tag = dockerEvent.getContent().getTag();
    return tag != null && !tag.isEmpty();
  }

  protected List<Artifact> getArtifactsFromEvent(DockerEvent dockerEvent, Trigger trigger) {
    DockerEvent.Content content = dockerEvent.getContent();

    String name = content.getRegistry() + "/" + content.getRepository();
    String reference = name + ":" + content.getTag();
    return Collections.singletonList(
        Artifact.builder()
            .type("docker/image")
            .name(name)
            .version(content.getTag())
            .reference(reference)
            .build());
  }

  @Override
  protected Function<Trigger, Trigger> buildTrigger(DockerEvent dockerEvent) {
    return trigger ->
        trigger.atTag(dockerEvent.getContent().getTag()).withEventId(dockerEvent.getEventId());
  }

  @Override
  protected boolean isValidTrigger(Trigger trigger) {
    return trigger.isEnabled()
        && ((TRIGGER_TYPE.equals(trigger.getType())
            && trigger.getAccount() != null
            && trigger.getRepository() != null));
  }

  private boolean matchTags(String suppliedTag, String incomingTag) {
    try {
      // use matches to handle regex or basic string compare
      return incomingTag.matches(suppliedTag);
    } catch (PatternSyntaxException e) {
      return false;
    }
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(DockerEvent dockerEvent) {
    return trigger -> isMatchingTrigger(dockerEvent, trigger);
  }

  private boolean isMatchingTrigger(DockerEvent dockerEvent, Trigger trigger) {
    String account = dockerEvent.getContent().getAccount();
    String repository = dockerEvent.getContent().getRepository();
    String eventTag = dockerEvent.getContent().getTag();
    String triggerTagPattern = null;
    if (StringUtils.isNotBlank(trigger.getTag())) {
      triggerTagPattern = trigger.getTag().trim();
    }
    return trigger.getType().equals(TRIGGER_TYPE)
        && trigger.getRepository().equals(repository)
        && trigger.getAccount().equals(account)
        && ((triggerTagPattern == null && !eventTag.equals("latest"))
            || triggerTagPattern != null && matchTags(triggerTagPattern, eventTag));
  }
}
