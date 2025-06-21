/*
 * Copyright 2025 Harness, Inc.
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
import com.netflix.spinnaker.echo.model.trigger.AbstractDockerEvent;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.PatternSyntaxException;
import org.apache.commons.lang3.StringUtils;

/**
 * Abstract base class for Docker-like event handlers that share common functionality. This includes
 * both DockerEventHandler and HelmOciEventHandler.
 *
 * @param <T> The specific event type (DockerEvent or HelmOciEvent)
 */
public abstract class AbstractDockerEventHandler<T extends AbstractDockerEvent>
    extends BaseTriggerEventHandler<T> {

  protected AbstractDockerEventHandler(
      Registry registry,
      ObjectMapper objectMapper,
      FiatPermissionEvaluator fiatPermissionEvaluator) {
    super(registry, objectMapper, fiatPermissionEvaluator);
  }

  /** Get the trigger type string for this handler. */
  protected abstract String getTriggerType();

  /** Get the artifact type to use when creating artifacts. */
  protected abstract String getArtifactType();

  @Override
  public List<String> supportedTriggerTypes() {
    return Collections.singletonList(getTriggerType());
  }

  @Override
  public boolean isSuccessfulTriggerEvent(T event) {
    // The event should always report a tag
    String tag = event.getContent().getTag();
    return tag != null && !tag.isEmpty();
  }

  protected List<Artifact> getArtifactsFromEvent(T event, Trigger trigger) {
    AbstractDockerEvent.Content content = event.getContent();
    String name = content.getRegistry() + "/" + content.getRepository();
    String reference = name + ":" + content.getTag();
    return Collections.singletonList(
        Artifact.builder()
            .type(getArtifactType())
            .name(name)
            .version(content.getTag())
            .reference(reference)
            .build());
  }

  @Override
  protected Function<Trigger, Trigger> buildTrigger(T event) {
    return trigger ->
        trigger
            .atTag(event.getContent().getTag(), event.getContent().getDigest())
            .withEventId(event.getEventId());
  }

  @Override
  protected boolean isValidTrigger(Trigger trigger) {
    return trigger.isEnabled()
        && ((getTriggerType().equals(trigger.getType())
            && trigger.getAccount() != null
            && trigger.getRepository() != null));
  }

  protected boolean matchTags(String suppliedTag, String incomingTag) {
    try {
      // use matches to handle regex or basic string compare
      return incomingTag.matches(suppliedTag);
    } catch (PatternSyntaxException e) {
      return false;
    }
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(T event) {
    return trigger -> isMatchingTrigger(event, trigger);
  }

  protected boolean isMatchingTrigger(T event, Trigger trigger) {
    AbstractDockerEvent.Content content = event.getContent();
    String account = content.getAccount();
    String repository = content.getRepository();
    String eventTag = content.getTag();
    String triggerTagPattern = null;
    if (StringUtils.isNotBlank(trigger.getTag())) {
      triggerTagPattern = trigger.getTag().trim();
    }
    return trigger.getType().equals(getTriggerType())
        && trigger.getRepository().equals(repository)
        && trigger.getAccount().equals(account)
        && ((triggerTagPattern == null && !eventTag.equals("latest"))
            || triggerTagPattern != null && matchTags(triggerTagPattern, eventTag));
  }
}
