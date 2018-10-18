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

package com.netflix.spinnaker.echo.pipelinetriggers.monitor;

import static com.netflix.spinnaker.echo.pipelinetriggers.artifacts.ArtifactMatcher.anyArtifactsMatchExpected;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.GitEvent;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.PipelineInitiator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Triggers pipelines on _Orca_ when a trigger-enabled build completes successfully.
 */
@Component
@Slf4j
public class GitEventMonitor extends TriggerMonitor {

  public static final String GIT_TRIGGER_TYPE = "git";

  private static final String GITHUB_SECURE_SIGNATURE_HEADER = "X-Hub-Signature";

  @Autowired
  public GitEventMonitor(@NonNull PipelineCache pipelineCache,
                         @NonNull PipelineInitiator pipelineInitiator,
                         @NonNull Registry registry) {
    super(pipelineCache, pipelineInitiator, registry);
  }

  @Override
  protected boolean handleEventType(String eventType) {
    return eventType.equalsIgnoreCase(GitEvent.TYPE);
  }


  @Override
  protected GitEvent convertEvent(Event event) {
    return objectMapper.convertValue(event, GitEvent.class);
  }

  @Override
  protected boolean isValidTrigger(final Trigger trigger) {
    return trigger.isEnabled() &&
      (
        (GIT_TRIGGER_TYPE.equals(trigger.getType()) &&
          trigger.getSource() != null &&
          trigger.getProject() != null &&
          trigger.getSlug() != null)
      );
  }

  @Override
  protected boolean isSuccessfulTriggerEvent(TriggerEvent event) {
    return true;
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(final TriggerEvent event, final Pipeline pipeline) {
    GitEvent gitEvent = (GitEvent) event;
    String source = gitEvent.getDetails().getSource();
    String project = gitEvent.getContent().getRepoProject();
    String slug = gitEvent.getContent().getSlug();
    String branch = gitEvent.getContent().getBranch();
    List<Artifact> artifacts = gitEvent.getContent() != null && gitEvent.getContent().getArtifacts() != null ?
      gitEvent.getContent().getArtifacts() : new ArrayList<>();

    return trigger -> trigger.getType().equals(GIT_TRIGGER_TYPE)
        && trigger.getSource().equalsIgnoreCase(source)
        && trigger.getProject().equalsIgnoreCase(project)
        && trigger.getSlug().equalsIgnoreCase(slug)
        && (trigger.getBranch() == null || trigger.getBranch().equals("") || matchesPattern(branch, trigger.getBranch()))
        && passesGithubAuthenticationCheck(event, trigger)
        && anyArtifactsMatchExpected(artifacts, trigger, pipeline);
  }

  @Override
  protected Function<Trigger, Pipeline> buildTrigger(Pipeline pipeline, TriggerEvent event) {
    GitEvent gitEvent = (GitEvent) event;
    return trigger -> pipeline
      .withReceivedArtifacts(gitEvent.getContent().getArtifacts())
      .withTrigger(trigger.atHash(gitEvent.getHash())
        .atBranch(gitEvent.getBranch())
        .atEventId(gitEvent.getEventId()));
  }

  private boolean passesGithubAuthenticationCheck(TriggerEvent event, Trigger trigger) {
    boolean triggerHasSecret = StringUtils.isNotEmpty(trigger.getSecret());
    boolean eventHasSignature = event.getDetails().getRequestHeaders().containsKey(GITHUB_SECURE_SIGNATURE_HEADER);

    if (triggerHasSecret && !eventHasSignature) {
      log.warn("Received GitEvent from Github without secure signature for trigger configured with a secret");
      return false;
    }

    if (!triggerHasSecret && eventHasSignature) {
      log.warn("Received GitEvent from Github with secure signature, but trigger did not contain the secret");
      return false;
    }
    // If we reach here, triggerHasSecret == eventHasSignature

    if (!triggerHasSecret) {
      // Trigger has no secret, and event sent no signature
      return true;
    }

    // Trigger has a secret, and event sent a signature
    return hasValidGitHubSecureSignature(event, trigger);
  }

  private boolean hasValidGitHubSecureSignature(TriggerEvent event, Trigger trigger) {
    String header = event.getDetails().getRequestHeaders().getFirst(GITHUB_SECURE_SIGNATURE_HEADER);
    log.debug("GitHub Signature detected. " + GITHUB_SECURE_SIGNATURE_HEADER + ": " + header);
    String signature = StringUtils.removeStart(header, "sha1=");

    String computedDigest = HmacUtils.hmacSha1Hex(trigger.getSecret(), event.getRawContent());

    // TODO: Find constant time comparison algo?
    boolean digestsMatch = signature.equalsIgnoreCase(computedDigest);
    if (!digestsMatch) {
      log.warn("Github Digest mismatch! Pipeline NOT triggered: " + trigger);
      log.debug("computedDigest: " + computedDigest + ", from GitHub: " + signature);
    }

    return digestsMatch;
  }
}
