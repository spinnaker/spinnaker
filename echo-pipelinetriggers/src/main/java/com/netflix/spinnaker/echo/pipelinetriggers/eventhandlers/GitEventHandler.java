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
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.GitEvent;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
  private static final String GITHUB_SECURE_SIGNATURE_HEADER = "X-Hub-Signature";
  private static final List<String> supportedTriggerTypes =
      Collections.singletonList(GIT_TRIGGER_TYPE);

  @Autowired
  public GitEventHandler(Registry registry, ObjectMapper objectMapper) {
    super(registry, objectMapper);
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

    return trigger ->
        trigger.getType().equals(GIT_TRIGGER_TYPE)
            && trigger.getSource().equalsIgnoreCase(source)
            && trigger.getProject().equalsIgnoreCase(project)
            && trigger.getSlug().equalsIgnoreCase(slug)
            && (trigger.getBranch() == null
                || trigger.getBranch().equals("")
                || matchesPattern(branch, trigger.getBranch()))
            && passesGithubAuthenticationCheck(gitEvent, trigger);
  }

  @Override
  protected List<Artifact> getArtifactsFromEvent(GitEvent gitEvent, Trigger trigger) {
    return gitEvent.getContent() != null && gitEvent.getContent().getArtifacts() != null
        ? gitEvent.getContent().getArtifacts()
        : new ArrayList<>();
  }

  @Override
  protected Function<Trigger, Trigger> buildTrigger(GitEvent gitEvent) {
    return trigger ->
        trigger
            .atHash(gitEvent.getHash())
            .atBranch(gitEvent.getBranch())
            .atEventId(gitEvent.getEventId());
  }

  private boolean matchesPattern(String s, String pattern) {
    Pattern p = Pattern.compile(pattern);
    Matcher m = p.matcher(s);
    return m.matches();
  }

  private boolean passesGithubAuthenticationCheck(GitEvent gitEvent, Trigger trigger) {
    boolean triggerHasSecret = StringUtils.isNotEmpty(trigger.getSecret());
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
        gitEvent.getDetails().getRequestHeaders().getFirst(GITHUB_SECURE_SIGNATURE_HEADER);
    log.debug("GitHub Signature detected. " + GITHUB_SECURE_SIGNATURE_HEADER + ": " + header);
    String signature = StringUtils.removeStart(header, "sha1=");

    String computedDigest = HmacUtils.hmacSha1Hex(trigger.getSecret(), gitEvent.getRawContent());

    // TODO: Find constant time comparison algo?
    boolean digestsMatch = signature.equalsIgnoreCase(computedDigest);
    if (!digestsMatch) {
      log.warn("Github Digest mismatch! Pipeline NOT triggered: " + trigger);
      log.debug("computedDigest: " + computedDigest + ", from GitHub: " + signature);
    }

    return digestsMatch;
  }
}
