/*
 * Copyright 2018 Schibsted ASA
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

package com.netflix.spinnaker.echo.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.exceptions.FieldNotFoundException;
import com.netflix.spinnaker.echo.github.GithubCommit;
import com.netflix.spinnaker.echo.github.GithubService;
import com.netflix.spinnaker.echo.github.GithubStatus;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.kork.core.RetrySupport;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import retrofit.client.Response;

@Slf4j
@ConditionalOnProperty("github-status.enabled")
@Service
public class GithubNotificationAgent extends AbstractEventNotificationAgent {
  private ImmutableMap<String, String> STATUSES =
      ImmutableMap.of(
          "starting", "pending",
          "complete", "success",
          "failed", "failure");

  private final RetrySupport retrySupport = new RetrySupport();
  private static final int MAX_RETRY = 5;
  private static final long RETRY_BACKOFF = 1000;

  @Override
  public void sendNotifications(
      Map preference,
      final String application,
      final Event event,
      Map config,
      final String status) {
    String forced_repo =
        Optional.ofNullable(preference).map(p -> (String) p.get("repo")).orElse(null);
    String forced_hash =
        Optional.ofNullable(preference).map(p -> (String) p.get("commit")).orElse(null);
    EventContent content;
    try {
      content = new EventContent(event, (String) config.get("type"), forced_repo, forced_hash);
    } catch (FieldNotFoundException e) {
      return;
    }

    String state = STATUSES.get(status);

    String description;
    String context;
    String targetUrl;

    if (config.get("type").equals("stage")) {
      description =
          String.format(
              "Stage '%s' in pipeline '%s' is %s",
              content.getStageName(), content.getPipeline(), status);
      context = String.format("stage/%s", content.getStageName());
      targetUrl =
          String.format(
              "%s/#/applications/%s/executions/details/%s?pipeline=%s&stage=%d",
              getSpinnakerUrl(),
              application,
              content.getExecutionId(),
              content.getPipeline(),
              content.getStageIndex());
    } else if (config.get("type").equals("pipeline")) {
      description = String.format("Pipeline '%s' is %s", content.getPipeline(), status);
      context = String.format("pipeline/%s", content.getPipeline());
      targetUrl =
          String.format(
              "%s/#/applications/%s/executions/details/%s?pipeline=%s",
              getSpinnakerUrl(), application, content.getExecutionId(), content.getPipeline());
    } else {
      return;
    }

    log.info(String.format("Sending Github status check for application: %s", application));

    /* Some CI systems (Travis) do a simulation of merging the commit in the PR into the default branch. If we
    trigger the pipeline using the `pull_request_master` trigger, the sha we get in Spinnaker is the one
    corresponding to that commit, so if we set the status check in that commit, we won't be able to see it
    in the pull request in GitHub.
       To detect the real commit, we look at the message of the commit and see if it matches the string
    `Merge ${branch_sha} into ${master_sha}` and if it does we take the real commit
    */
    String branchCommit = getBranchCommit(content.getRepo(), content.getSha());

    GithubStatus githubStatus = new GithubStatus(state, targetUrl, description, context);
    try {
      final String repo = content.getRepo();
      retrySupport.retry(
          () -> githubService.updateCheck("token " + token, repo, branchCommit, githubStatus),
          MAX_RETRY,
          RETRY_BACKOFF,
          false);
    } catch (Exception e) {
      log.error(
          String.format(
              "Failed to send github status for application: '%s' pipeline: '%s', %s",
              application, content.getPipeline(), e));
    }
  }

  private String getBranchCommit(String repo, String sha) {
    Response response = githubService.getCommit("token " + token, repo, sha);
    ObjectMapper objectMapper = EchoObjectMapper.getInstance();
    GithubCommit message = null;
    try {
      message = objectMapper.readValue(response.getBody().in(), GithubCommit.class);
    } catch (IOException e) {
      return sha;
    }

    Pattern pattern =
        Pattern.compile(
            "Merge (?<branchCommit>[0-9a-f]{5,40}) into (?<masterCommit>[0-9a-f]{5,40})");
    Matcher matcher = pattern.matcher(message.getCommit().getMessage());
    if (matcher.matches()) {
      return matcher.group("branchCommit");
    }
    return sha;
  }

  @Override
  public String getNotificationType() {
    return "githubStatus";
  }

  public GithubService getGithubService() {
    return githubService;
  }

  public void setGithubService(GithubService githubService) {
    this.githubService = githubService;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  @Autowired private GithubService githubService;

  @Value("${github-status.token}")
  private String token;
}
