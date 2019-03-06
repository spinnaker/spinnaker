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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.WebhookEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.artifacts.JinjaArtifactExtractor;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.netflix.spinnaker.echo.pipelinetriggers.artifacts.ArtifactMatcher.isConstraintInPayload;

/**
 * Implementation of TriggerEventHandler for events of type {@link WebhookEvent}, which occur when
 * a webhook is received.
 */
@Component
public class WebhookEventHandler extends BaseTriggerEventHandler<WebhookEvent> {
  private static final String TRIGGER_TYPE = "webhook";

  @Autowired
  public WebhookEventHandler(Registry registry, ObjectMapper objectMapper, JinjaArtifactExtractor jinjaArtifactExtractor) {
    super(registry, objectMapper, jinjaArtifactExtractor);
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
  protected Function<Trigger, Trigger> buildTrigger(WebhookEvent webhookEvent) {
    WebhookEvent.Content content = webhookEvent.getContent();
    Map payload = webhookEvent.getPayload();

    return trigger -> trigger.atParameters(content.getParameters())
          .atPayload(payload)
          .atEventId(webhookEvent.getEventId());
  }

  @Override
  protected boolean isValidTrigger(Trigger trigger) {
    return trigger.isEnabled() && TRIGGER_TYPE.equals(trigger.getType());
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(WebhookEvent webhookEvent) {
    final String type = webhookEvent.getDetails().getType();
    final String source = webhookEvent.getDetails().getSource();

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

        );
  }

  @Override
  public Map<String, String> getAdditionalTags(Pipeline pipeline) {
    Map<String, String> tags = new HashMap<>();
    tags.put("type", pipeline.getTrigger().getType());
    return tags;
  }

  @Override
  protected List<Artifact> getArtifactsFromEvent(WebhookEvent webhookEvent) {
    return webhookEvent.getContent().getArtifacts();
  }
}

