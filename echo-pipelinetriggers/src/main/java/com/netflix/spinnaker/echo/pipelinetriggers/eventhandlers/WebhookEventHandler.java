/*
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

import static com.netflix.spinnaker.echo.pipelinetriggers.artifacts.ArtifactMatcher.isConstraintInPayload;
import static java.util.Collections.emptyList;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.WebhookEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.artifacts.ArtifactMatcher;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Implementation of TriggerEventHandler for events of type {@link WebhookEvent}, which occur when
 * a webhook is received.
 */
@Component
public class WebhookEventHandler extends BaseTriggerEventHandler<WebhookEvent> {
  private static final String TRIGGER_TYPE = "webhook";

  private static final TypeReference<List<Artifact>> ARTIFACT_LIST =
      new TypeReference<List<Artifact>>() {};

  @Autowired
  public WebhookEventHandler(Registry registry, ObjectMapper objectMapper) {
    super(registry, objectMapper);
  }

  @Override
  public boolean handleEventType(String eventType) {
    return eventType != null && !eventType.equals("manual");
  }

  @Override
  public Class<WebhookEvent> getEventType() {
    return WebhookEvent.class;
  }

  @Override
  public boolean isSuccessfulTriggerEvent(WebhookEvent webhookEvent) {
    return true;
  }

  @Override
  protected Function<Trigger, Pipeline> buildTrigger(Pipeline pipeline, WebhookEvent webhookEvent) {
    Map content = webhookEvent.getContent();
    Map payload = webhookEvent.getPayload();
    Map parameters = content.containsKey("parameters") ? (Map) content.get("parameters") : new HashMap();
    List<Artifact> artifacts = objectMapper.convertValue(content.getOrDefault("artifacts", emptyList()), ARTIFACT_LIST);

    return trigger -> pipeline
        .withReceivedArtifacts(artifacts)
        .withTrigger(trigger.atParameters(parameters)
          .atPayload(payload)
          .atEventId(webhookEvent.getEventId()));
  }

  @Override
  protected boolean isValidTrigger(Trigger trigger) {
    return trigger.isEnabled() && TRIGGER_TYPE.equals(trigger.getType());
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(WebhookEvent webhookEvent, Pipeline pipeline) {
    final String type = webhookEvent.getDetails().getType();
    final String source = webhookEvent.getDetails().getSource();
    final Map content = webhookEvent.getContent();
    final List<Artifact> messageArtifacts = objectMapper.convertValue(content.getOrDefault("artifacts", emptyList()), ARTIFACT_LIST);

    return trigger ->
        trigger.getType() != null && trigger.getType().equalsIgnoreCase(type) &&
        trigger.getSource() != null && trigger.getSource().equals(source) &&
        (
            // The Constraints in the Trigger could be null. That's OK.
            trigger.getPayloadConstraints() == null ||

            // If the Constraints are present, check that there are equivalents in the webhook payload.
            (  trigger.getPayloadConstraints() != null &&
               isConstraintInPayload(trigger.getPayloadConstraints(), webhookEvent.getPayload())
            )

        ) &&
        // note this returns true when no artifacts are expected
        ArtifactMatcher.anyArtifactsMatchExpected(messageArtifacts, trigger, pipeline);
  }

  @Override
  public Map<String, String> getAdditionalTags(Pipeline pipeline) {
    Map<String, String> tags = new HashMap<>();
    tags.put("type", pipeline.getTrigger().getType());
    return tags;
  }
}

