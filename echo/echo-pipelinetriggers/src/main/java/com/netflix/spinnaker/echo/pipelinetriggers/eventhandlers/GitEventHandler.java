/*
 * Copyright 2016 Netflix, Inc.
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
import com.netflix.spinnaker.echo.config.PipelineTriggerConfiguration;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.GitEvent;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Implementation of TriggerEventHandler for events of type {@link GitEvent}, which occur when a
 * commit is pushed to a tracked git repository.
 */
@Component
@Slf4j
public class GitEventHandler extends BaseTriggerEventHandler<GitEvent> {
  private static final String GIT_TRIGGER_TYPE = "git";
  private static final String GITHUB_SECURE_SIGNATURE_HEADER = "x-hub-signature";
  private static final String GITEA_SOURCE = "gitea";
  private static final String GITEA_SECURE_SIGNATURE_HEADER = "x-gitea-signature";
  private static final List<String> supportedTriggerTypes =
      Collections.singletonList(GIT_TRIGGER_TYPE);

  @Autowired private PipelineTriggerConfiguration pipelineTriggerConfiguration;

  @Autowired
  public GitEventHandler(
      Registry registry,
      ObjectMapper objectMapper,
      FiatPermissionEvaluator fiatPermissionEvaluator,
      PipelineTriggerConfiguration pipelineTriggerConfiguration) {
    super(registry, objectMapper, fiatPermissionEvaluator);
    this.pipelineTriggerConfiguration = pipelineTriggerConfiguration;
  }

  @Override
  public List<String> supportedTriggerTypes() {
    return supportedTriggerTypes;
  }

  @Override
  public boolean handleEventType(String eventType) {
    return eventType.equalsIgnoreCase(GitEvent.TYPE);
  }

  @Override
  public Class<GitEvent> getEventType() {
    return GitEvent.class;
  }

  @Override
  protected boolean isValidTrigger(Trigger trigger) {
    return trigger.isEnabled()
        && ((GIT_TRIGGER_TYPE.equals(trigger.getType())
            && trigger.getSource() != null
            && trigger.getProject() != null
            && trigger.getSlug() != null));
  }

  @Override
  public boolean isSuccessfulTriggerEvent(GitEvent event) {
    return true;
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(GitEvent gitEvent) {
    String source = gitEvent.getDetails().getSource();
    String project = gitEvent.getContent().getRepoProject();
    String slug = gitEvent.getContent().getSlug();
    String branch = gitEvent.getContent().getBranch();
    String action = gitEvent.getContent().getAction();

    return trigger ->
        trigger.getType().equals(GIT_TRIGGER_TYPE)
            && trigger.getSource().equalsIgnoreCase(source)
            && trigger.getProject().equalsIgnoreCase(project)
            && trigger.getSlug().equalsIgnoreCase(slug)
            && (trigger.getBranch() == null
                || trigger.getBranch().equals("")
                || matchesPattern(branch, trigger.getBranch()))
            && passesAuthenticationCheck(gitEvent, trigger)
            && (trigger.getEvents() == null
                || trigger.getEvents().size() == 0
                || trigger.getEvents().stream().anyMatch(a -> a.equals(action)));
  }

  @Override
  protected List<Artifact> getArtifactsFromEvent(GitEvent gitEvent, Trigger trigger) {
    return gitEvent.getContent() != null && gitEvent.getContent().getArtifacts() != null
        ? gitEvent.getContent().getArtifacts()
        : new ArrayList<>();
  }

  @Override
  protected Function<Trigger, Trigger> buildTrigger(GitEvent gitEvent) {
    return trigger -> {
      Trigger t =
          trigger
              .atHash(gitEvent.getHash())
              .atBranch(gitEvent.getBranch())
              .atEventId(gitEvent.getEventId())
              .atAction(gitEvent.getAction());

      return t.withProperties(
          Stream.concat(
                  Optional.ofNullable(t.getProperties())
                      .orElse(new HashMap<>())
                      .entrySet()
                      .stream(),
                  Stream.of(
                          new AbstractMap.SimpleEntry<>(
                              "number", gitEvent.getContent().getNumber()),
                          new AbstractMap.SimpleEntry<>("draft", gitEvent.getContent().getDraft()),
                          new AbstractMap.SimpleEntry<>("state", gitEvent.getContent().getState()),
                          new AbstractMap.SimpleEntry<>("title", gitEvent.getContent().getTitle()))
                      .filter(entry -> entry.getValue() != null))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    };
  }

  private boolean matchesPattern(String s, String pattern) {
    Pattern p = Pattern.compile(pattern);
    Matcher m = p.matcher(s);
    return m.matches();
  }

  private boolean passesAuthenticationCheck(GitEvent gitEvent, Trigger trigger) {
    if (GITEA_SOURCE.equalsIgnoreCase(gitEvent.getDetails().getSource())) {
      return passesGiteaAuthenticationCheck(gitEvent, trigger);
    }
    return passesGithubAuthenticationCheck(gitEvent, trigger);
  }

  /**
   * Gitea signs the raw request body with HMAC-SHA256 and sends the hex digest, without a prefix,
   * in {@code X-Gitea-Signature}. When neither the trigger nor the shared secret configures a
   * secret there is nothing to verify against, so the event is accepted.
   */
  private boolean passesGiteaAuthenticationCheck(GitEvent gitEvent, Trigger trigger) {
    String secret = trigger.getSecret();
    if (StringUtils.isEmpty(secret)) {
      secret = pipelineTriggerConfiguration.getGitSharedSecret();
    }
    if (StringUtils.isEmpty(secret)) {
      return true;
    }

    List<String> header =
        gitEvent.getDetails().getRequestHeaders().get(GITEA_SECURE_SIGNATURE_HEADER);
    if (header == null || header.isEmpty()) {
      log.warn(
          "Received GitEvent from Gitea without a signature for trigger configured with a secret");
      return false;
    }

    String computedDigest = HmacUtils.hmacSha256Hex(secret, gitEvent.getRawContent());
    boolean digestsMatch =
        MessageDigest.isEqual(
            header.get(0).trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
            computedDigest.getBytes(StandardCharsets.UTF_8));
    if (!digestsMatch) {
      log.warn("Gitea signature mismatch! Pipeline NOT triggered: " + trigger);
    }
    return digestsMatch;
  }

  private boolean passesGithubAuthenticationCheck(GitEvent gitEvent, Trigger trigger) {
    boolean triggerHasSecret =
        StringUtils.isNotEmpty(trigger.getSecret())
            || StringUtils.isNotEmpty(pipelineTriggerConfiguration.getGitSharedSecret());
    boolean eventHasSignature =
        gitEvent.getDetails().getRequestHeaders().containsKey(GITHUB_SECURE_SIGNATURE_HEADER);

    if (triggerHasSecret && !eventHasSignature) {
      log.warn(
          "Received GitEvent from Github without secure signature for trigger configured with a secret");
      return false;
    }

    if (!triggerHasSecret && eventHasSignature) {
      log.warn(
          "Received GitEvent from Github with secure signature, but trigger did not contain the secret");
      return false;
    }
    // If we reach here, triggerHasSecret == eventHasSignature

    if (!triggerHasSecret) {
      // Trigger has no secret, and event sent no signature
      return true;
    }

    // Trigger has a secret, and event sent a signature
    return hasValidGitHubSecureSignature(gitEvent, trigger);
  }

  private boolean hasValidGitHubSecureSignature(GitEvent gitEvent, Trigger trigger) {
    String header =
        gitEvent.getDetails().getRequestHeaders().get(GITHUB_SECURE_SIGNATURE_HEADER).get(0);
    log.debug("GitHub Signature detected. " + GITHUB_SECURE_SIGNATURE_HEADER + ": " + header);
    String signature = StringUtils.removeStart(header, "sha1=");

    String secret = trigger.getSecret();
    if (StringUtils.isEmpty(secret)) {
      secret = pipelineTriggerConfiguration.getGitSharedSecret();
    }

    String computedDigest = HmacUtils.hmacSha1Hex(secret, gitEvent.getRawContent());

    // Compare in constant time so the time taken does not depend on how much of the signature
    // matched. Hex digits are case-insensitive, so normalize the case first.
    boolean digestsMatch =
        MessageDigest.isEqual(
            signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
            computedDigest.getBytes(StandardCharsets.UTF_8));
    if (!digestsMatch) {
      log.warn("Github Digest mismatch! Pipeline NOT triggered: " + trigger);
    }

    return digestsMatch;
  }
}
