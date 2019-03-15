/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.echo.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.BuildEvent;
import com.netflix.spinnaker.echo.services.IgorService;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty("igor.enabled")
@RequiredArgsConstructor
/*
 * Given a build event, fetches information about that build from Igor
 */
public class BuildInfoService {
  private final IgorService igorService;
  private final RetrySupport retrySupport;
  private final ObjectMapper objectMapper = new ObjectMapper();

  // Manual triggers try to replicate actual events (and in some cases build events) but rather than pass the event to
  // echo, they add the information to the trigger. It may make sense to refactor manual triggering to pass a trigger and
  // an event as with other triggers, but for now we'll see whether we can extract a build event from the trigger.
  public BuildEvent getBuildEvent(String master, String job, int buildNumber) {
    Map<String, Object> rawBuild = retry(() -> igorService.getBuild(buildNumber, master, job));
    BuildEvent.Build build = objectMapper.convertValue(rawBuild, BuildEvent.Build.class);
    BuildEvent.Project project = new BuildEvent.Project(job, build);
    BuildEvent.Content content = new BuildEvent.Content(project, master);
    BuildEvent buildEvent = new BuildEvent();
    buildEvent.setContent(content);
    return buildEvent;
  }

  public Map<String, Object> getBuildInfo(BuildEvent event) {
    String master = event.getContent().getMaster();
    String job = event.getContent().getProject().getName();
    int buildNumber = event.getBuildNumber();

    if (StringUtils.isNoneEmpty(master, job)) {
      return retry(() -> igorService.getBuild(buildNumber, master, job));
    }
    return Collections.emptyMap();
  }

  public Map<String, Object> getProperties(BuildEvent event, String propertyFile) {
    String master = event.getContent().getMaster();
    String job = event.getContent().getProject().getName();
    int buildNumber = event.getBuildNumber();

    if (StringUtils.isNoneEmpty(master, job, propertyFile)) {
      return retry(() -> igorService.getPropertyFile(buildNumber, propertyFile, master, job));
    }
    return Collections.emptyMap();
  }

  public List<Artifact> getArtifacts(BuildEvent event, String propertyFile) {
    String master = event.getContent().getMaster();
    String job = event.getContent().getProject().getName();
    int buildNumber = event.getBuildNumber();
    if (StringUtils.isNoneEmpty(master, job, propertyFile)) {
      return retry(() -> igorService.getArtifacts(buildNumber, propertyFile, master, job));
    }
    return Collections.emptyList();
  }

  public List<Artifact> getArtifactsFromBuildEvent(BuildEvent event, Trigger trigger) {
    List<Artifact> buildArtifacts = Optional.ofNullable(event.getContent())
      .map(BuildEvent.Content::getProject)
      .map(BuildEvent.Project::getLastBuild)
      .map(BuildEvent.Build::getArtifacts)
      .orElse(Collections.emptyList());
    List<Artifact> extractedArtifacts = this.getArtifacts(event, trigger.getPropertyFile());

    List <Artifact> result = new ArrayList<>();
    result.addAll(buildArtifacts);
    result.addAll(extractedArtifacts);
    return result;
  }

  private <T> T retry(Supplier<T> supplier) {
    return retrySupport.retry(supplier, 5, 2000, false);
  }
}
