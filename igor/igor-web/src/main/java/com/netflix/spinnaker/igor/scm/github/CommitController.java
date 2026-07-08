/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.igor.scm.github;

import com.netflix.spinnaker.igor.config.GitHubProperties;
import com.netflix.spinnaker.igor.scm.AbstractCommitController;
import com.netflix.spinnaker.igor.scm.github.client.GitHubMaster;
import com.netflix.spinnaker.igor.scm.github.client.model.CompareCommitsResponse;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController(value = "GitHubCommitController")
@ConditionalOnProperty("github.base-url")
@RequestMapping("/github")
public class CommitController extends AbstractCommitController {
    @Autowired
    private GitHubMaster master;

    @Autowired
    private GitHubProperties gitHubProperties;

    @RequestMapping(method = RequestMethod.GET, value = "/{projectKey}/{repositorySlug}/compareCommits")
    public List<Map<String, Object>> compareCommits(@PathVariable(value = "projectKey") String projectKey, @PathVariable(value="repositorySlug") String repositorySlug, @RequestParam Map<String, String> requestParams) {
        super.compareCommits(projectKey, repositorySlug, requestParams);
        CompareCommitsResponse commitsResponse;
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            commitsResponse = Retrofit2SyncCall.execute(master.getGitHubClient().getCompareCommits(projectKey, repositorySlug, requestParams.get("to"), requestParams.get("from")));
        } catch (SpinnakerNetworkException e) {
            throw new NotFoundException("Could not find the server " + master.getBaseUrl());
        } catch (SpinnakerServerException e) {
          if (e instanceof SpinnakerHttpException && ((SpinnakerHttpException)e).getResponseCode() == 404) {
            return getNotFoundCommitsResponse(projectKey, repositorySlug, requestParams.get("to"), requestParams.get("from"), master.getBaseUrl());
          }
          log.error("Unhandled error response, acting like commit response was not found", e);
          return getNotFoundCommitsResponse(projectKey, repositorySlug, requestParams.get("to"), requestParams.get("from"), master.getBaseUrl());
        }

        for (CompareCommitsResponse.CommitInfo commit : commitsResponse.getCommits()) {
            Map<String, Object> commitMap = new HashMap<>();
            commitMap.put("displayId", commit.getSha().substring(0, gitHubProperties.getCommitDisplayLength()));
            commitMap.put("id", commit.getSha());
            commitMap.put("authorDisplayName", commit.getCommitInfo().getAuthor().getName());
            commitMap.put("timestamp", commit.getCommitInfo().getAuthor().getDate());
            commitMap.put("message", commit.getCommitInfo().getMessage());
            commitMap.put("commitUrl", commit.getHtmlUrl());
            result.add(commitMap);
        }
        return result;
    }
}
