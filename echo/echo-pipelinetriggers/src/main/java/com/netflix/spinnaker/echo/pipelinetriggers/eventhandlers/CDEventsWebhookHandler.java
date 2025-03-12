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

import static com.netflix.spinnaker.echo.pipelinetriggers.artifacts.ArtifactMatcher.isJsonPathConstraintInPayload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.CDEvent;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Implementation of TriggerEventHandler for events of type {@link CDEvent}, which occur when a
 * CDEvents webhook is received.
 */
@Slf4j
@Component
public class CDEventsWebhookHandler extends BaseTriggerEventHandler<CDEvent> {
  private static final String TRIGGER_TYPE = "cdevents";
  private static final List<String> supportedTriggerTypes = List.of(TRIGGER_TYPE);

  @Autowired
  public CDEventsWebhookHandler(
      Registry registry,
      ObjectMapper objectMapper,
      FiatPermissionEvaluator fiatPermissionEvaluator) {
    super(registry, objectMapper, fiatPermissionEvaluator);
  }

  @Override
  public List<String> supportedTriggerTypes() {
    return supportedTriggerTypes;
  }

  @Override
  public boolean handleEventType(String eventType) {
    return eventType != null && !eventType.equals("manual");
  }

  @Override
  public Class<CDEvent> getEventType() {
    return CDEvent.class;
  }

  @Override
  public boolean isSuccessfulTriggerEvent(CDEvent cdEvent) {
    return true;
  }

  @Override
  protected Function<Trigger, Trigger> buildTrigger(CDEvent cdEvent) {
    CDEvent.Content content = cdEvent.getContent();
    Map payload = cdEvent.getPayload();

    return trigger ->
        trigger
            .atParameters(content.getParameters())
            .atPayload(payload)
            .atEventId(cdEvent.getEventId());
  }

  @Override
  protected boolean isValidTrigger(Trigger trigger) {
    return trigger.isEnabled() && TRIGGER_TYPE.equals(trigger.getType());
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(CDEvent cdEvent) {
    final String type = cdEvent.getDetails().getType();
    final String source = cdEvent.getDetails().getSource();

    return trigger ->
        trigger.getType() != null
            && trigger.getType().equalsIgnoreCase(type)
            && trigger.getSource() != null
            && trigger.getSource().equals(source)
            && (trigger.getAttributeConstraints() == null
                || trigger.getAttributeConstraints() != null
                    && isAttributeConstraintInReqHeader(
                        trigger.getAttributeConstraints(),
                        cdEvent.getDetails().getRequestHeaders()))
            && (trigger.getPayloadConstraints() == null
                || (trigger.getPayloadConstraints() != null
                    && isJsonPathConstraintInPayload(
                        trigger.getPayloadConstraints(), cdEvent.getPayload())));
  }

  private boolean isAttributeConstraintInReqHeader(
      final Map attConstraints, final Map<String, List<String>> reqHeaders) {
    for (Object key : attConstraints.keySet()) {
      if (!reqHeaders.containsKey(key) || reqHeaders.get(key).isEmpty()) {
        return false;
      }

      if (attConstraints.get(key) != null
          && !(reqHeaders.get(key).contains(attConstraints.get(key).toString()))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Map<String, String> getAdditionalTags(Pipeline pipeline) {
    return Map.of("type", pipeline.getTrigger().getType());
  }

  @Override
  protected List<Artifact> getArtifactsFromEvent(CDEvent cdEvent, Trigger trigger) {
    return cdEvent.getContent().getArtifacts();
  }
}
