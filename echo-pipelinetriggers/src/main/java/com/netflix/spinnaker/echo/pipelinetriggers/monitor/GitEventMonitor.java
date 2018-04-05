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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.GitEvent;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.functions.Action1;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.netflix.spinnaker.echo.pipelinetriggers.artifacts.ArtifactMatcher.anyArtifactsMatchExpected;

/**
 * Triggers pipelines on _Orca_ when a trigger-enabled build completes successfully.
 */
@Component
@Slf4j
public class GitEventMonitor extends TriggerMonitor {

  public static final String GIT_TRIGGER_TYPE = "git";

  private static final String GITHUB_SECURE_SIGNATURE_HEADER = "X-Hub-Signature";

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final PipelineCache pipelineCache;

  @Autowired
  public GitEventMonitor(@NonNull PipelineCache pipelineCache,
                         @NonNull Action1<Pipeline> subscriber,
                         @NonNull Registry registry) {
    super(subscriber, registry);
    this.pipelineCache = pipelineCache;
  }

  @Override
  public void processEvent(Event event) {
    super.validateEvent(event);
    if (!event.getDetails().getType().equalsIgnoreCase(GitEvent.TYPE)) {
      return;
    }

    GitEvent buildEvent = objectMapper.convertValue(event, GitEvent.class);
    Observable.just(buildEvent)
      .doOnNext(this::onEchoResponse)
      .subscribe(triggerEachMatchFrom(pipelineCache.getPipelines()));
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
        && hasValidGitHubSecureSignature(event, trigger)
        && anyArtifactsMatchExpected(artifacts, trigger, pipeline);
  }

  @Override
  protected Function<Trigger, Pipeline> buildTrigger(Pipeline pipeline, TriggerEvent event) {
    GitEvent gitEvent = (GitEvent) event;
    return trigger -> pipeline
      .withReceivedArtifacts(gitEvent.getContent().getArtifacts())
      .withTrigger(trigger.atHash(gitEvent.getHash()).atBranch(gitEvent.getBranch()));
  }

  @Override
  protected void emitMetricsOnMatchingPipeline(Pipeline pipeline) {
    val id = registry.createId("pipelines.triggered")
      .withTag("application", pipeline.getApplication())
      .withTag("name", pipeline.getName());

    id.withTag("repository", pipeline.getTrigger().getProject() + ' ' + pipeline.getTrigger().getSlug());

    registry.counter(id).increment();
  }

  private boolean hasValidGitHubSecureSignature(TriggerEvent event, Trigger trigger) {
    val headers = event.getDetails().getRequestHeaders();
    if (!headers.containsKey(GITHUB_SECURE_SIGNATURE_HEADER)) {
      return true;
    }

    String header = headers.getFirst(GITHUB_SECURE_SIGNATURE_HEADER);
    log.debug("GitHub Signature detected. " + GITHUB_SECURE_SIGNATURE_HEADER + ": " + header);
    String signature = StringUtils.removeStart(header, "sha1=");

    String triggerSecret = trigger.getSecret();
    if (StringUtils.isEmpty(triggerSecret)) {
      log.warn("Received GitEvent from Github with secure signature, but trigger did not contain the secret");
      return false;
    }

    String computedDigest = HmacUtils.hmacSha1Hex(triggerSecret, event.getRawContent());

    // TODO: Find constant time comparison algo?
    boolean digestsMatch = signature.equalsIgnoreCase(computedDigest);
    if (!digestsMatch) {
      log.warn("Github Digest mismatch! Pipeline NOT triggered: " + trigger);
      log.debug("computedDigest: " + computedDigest + ", from GitHub: " + signature);
    }

    return digestsMatch;
  }
}
