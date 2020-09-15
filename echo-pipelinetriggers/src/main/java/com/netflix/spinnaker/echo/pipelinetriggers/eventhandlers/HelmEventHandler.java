package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.HelmEvent;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.vdurmont.semver4j.Semver;
import com.vdurmont.semver4j.Semver.SemverType;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HelmEventHandler extends BaseTriggerEventHandler<HelmEvent> {
  private static final String TRIGGER_TYPE = Trigger.Type.HELM.toString();

  @Autowired
  public HelmEventHandler(
      Registry registry,
      ObjectMapper objectMapper,
      FiatPermissionEvaluator fiatPermissionEvaluator) {
    super(registry, objectMapper, fiatPermissionEvaluator);
  }

  @Override
  public List<String> supportedTriggerTypes() {
    return Collections.singletonList(TRIGGER_TYPE);
  }

  @Override
  public boolean handleEventType(String eventType) {
    return eventType.equalsIgnoreCase(HelmEvent.TYPE);
  }

  @Override
  public Class<HelmEvent> getEventType() {
    return HelmEvent.class;
  }

  @Override
  public boolean isSuccessfulTriggerEvent(HelmEvent helmEvent) {
    HelmEvent.Content content = helmEvent.getContent();
    return !Strings.isNullOrEmpty(content.getChart())
        && !Strings.isNullOrEmpty(content.getVersion())
        && !Strings.isNullOrEmpty(content.getDigest());
  }

  @Override
  protected List<Artifact> getArtifactsFromEvent(HelmEvent helmEvent, Trigger trigger) {
    HelmEvent.Content content = helmEvent.getContent();
    Map<String, Object> meta = new HashMap<>();
    meta.put("digest", content.getDigest());

    return Collections.singletonList(
        Artifact.builder()
            .type("helm/chart")
            .name(content.getChart())
            .version(content.getVersion())
            .reference(content.getAccount())
            .metadata(meta)
            .build());
  }

  @Override
  protected Function<Trigger, Trigger> buildTrigger(HelmEvent helmEvent) {
    return trigger ->
        trigger
            .withArtifactName(helmEvent.getContent().getChart())
            .withVersion(helmEvent.getContent().getVersion())
            .withDigest(helmEvent.getContent().getDigest())
            .withEventId(helmEvent.getEventId());
  }

  @Override
  protected boolean isValidTrigger(Trigger trigger) {
    return trigger.isEnabled()
        && TRIGGER_TYPE.equals(trigger.getType())
        && trigger.getAccount() != null;
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(HelmEvent helmEvent) {
    return trigger -> isMatchingTrigger(helmEvent, trigger);
  }

  private boolean satisfies(String eventVersion, String triggerSemVer) {
    Boolean satisfiesSemVer;
    try {
      satisfiesSemVer = new Semver(eventVersion, SemverType.NPM).satisfies(triggerSemVer);
    } catch (Exception e) {
      satisfiesSemVer = false;
    }

    return satisfiesSemVer;
  }

  private boolean isMatchingTrigger(HelmEvent helmEvent, Trigger trigger) {
    HelmEvent.Content content = helmEvent.getContent();
    String helmVersion = content.getVersion();

    String triggerSemVer = null;
    if (StringUtils.isNotBlank(trigger.getVersion())) {
      triggerSemVer = trigger.getVersion().trim();
    }

    return TRIGGER_TYPE.equals(trigger.getType())
        && (trigger.getAccount() != null && trigger.getAccount().equals(content.getAccount()))
        && (triggerSemVer == null || satisfies(helmVersion, triggerSemVer));
  }
}
