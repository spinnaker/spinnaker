/*
 * Copyright 2017 bol.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.scm.gitlab;

import com.netflix.spinnaker.igor.config.GitLabProperties;
import com.netflix.spinnaker.igor.scm.AbstractCommitController;
import com.netflix.spinnaker.igor.scm.gitlab.client.GitLabMaster;
import com.netflix.spinnaker.igor.scm.gitlab.client.model.CompareCommitsResponse;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import retrofit.RetrofitError;

@RestController(value = "GitLabCommitController")
@ConditionalOnProperty("gitlab.base-url")
@RequestMapping("/gitlab")
public class CommitController extends AbstractCommitController {
  private static final Logger log = LoggerFactory.getLogger(CommitController.class);
  private final GitLabMaster gitLabMaster;
  private final GitLabProperties gitLabProperties;

  @Autowired
  public CommitController(GitLabMaster gitLabMaster, GitLabProperties gitLabProperties) {
    this.gitLabMaster = gitLabMaster;
    this.gitLabProperties = gitLabProperties;
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/{projectKey}/{repositorySlug}/compareCommits")
  public List<Map<String, Object>> compareCommits(
      @PathVariable String projectKey,
      @PathVariable String repositorySlug,
      @RequestParam Map<String, String> requestParams) {
    super.compareCommits(projectKey, repositorySlug, requestParams);
    CompareCommitsResponse commitsResponse;

    // Intentionally "wrong" (to=from and vice versa) as that's how the code that uses it expects it
    String toParam = requestParams.get("from");
    String fromParam = requestParams.get("to");

    try {
      Map<String, String> queryMap = new HashMap<>();
      queryMap.put("to", toParam);
      queryMap.put("from", fromParam);
      commitsResponse =
          gitLabMaster.getGitLabClient().getCompareCommits(projectKey, repositorySlug, queryMap);
    } catch (RetrofitError e) {
      if (e.getKind() == RetrofitError.Kind.NETWORK) {
        throw new NotFoundException("Could not find the server " + gitLabMaster.getBaseUrl());
      } else if (e.getResponse().getStatus() == 404) {
        return getNotFoundCommitsResponse(
            projectKey, repositorySlug, toParam, fromParam, gitLabMaster.getBaseUrl());
      }
      log.error("Unhandled error response, acting like commit response was not found", e);
      return getNotFoundCommitsResponse(
          projectKey, repositorySlug, toParam, fromParam, gitLabMaster.getBaseUrl());
    }

    return commitsResponse.commits.stream()
        .map(
            c -> {
              Map<String, Object> cMap = new HashMap<>();
              cMap.put(
                  "displayId", c.getId().substring(0, gitLabProperties.getCommitDisplayLength()));
              cMap.put("id", c.getId());
              cMap.put("authorDisplayName", c.getAuthorName());
              cMap.put("timestamp", c.getAuthoredDate());
              cMap.put("message", c.getMessage());
              cMap.put(
                  "commitUrl",
                  getCommitUrl(
                      gitLabProperties.getBaseUrl(), projectKey, repositorySlug, c.getId()));
              return cMap;
            })
        .collect(Collectors.toList());
  }

  private static String getCommitUrl(
      String baseUrl, String projectKey, String repositorySlug, String commitId) {
    return String.format("%s/%s/%s/commit/%s", baseUrl, projectKey, repositorySlug, commitId);
  }
}
