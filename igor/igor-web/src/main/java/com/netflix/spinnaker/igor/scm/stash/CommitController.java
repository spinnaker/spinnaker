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

package com.netflix.spinnaker.igor.scm.stash;

import com.netflix.spinnaker.igor.scm.AbstractCommitController;
import com.netflix.spinnaker.igor.scm.stash.client.StashMaster;
import com.netflix.spinnaker.igor.scm.stash.client.model.CompareCommitsResponse;
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
@RestController(value = "StashCommitController")
@ConditionalOnProperty("stash.base-url")
@RequestMapping("/stash")
public class CommitController extends AbstractCommitController {
    @Autowired
    private StashMaster stashMaster;

    @RequestMapping(method = RequestMethod.GET, value = "/{projectKey}/{repositorySlug}/compareCommits")
    public List<Map<String, Object>> compareCommits(@PathVariable(value = "projectKey") String projectKey, @PathVariable(value="repositorySlug") String repositorySlug, @RequestParam Map<String, String> requestParams) {
        super.compareCommits(projectKey, repositorySlug, requestParams);
        CompareCommitsResponse commitsResponse = null;
        try {
            commitsResponse = Retrofit2SyncCall.execute(stashMaster.getStashClient().getCompareCommits(projectKey, repositorySlug, requestParams));
        } catch (SpinnakerNetworkException e) {
          throw new NotFoundException("Could not find the server " + stashMaster.getBaseUrl());
        } catch  (SpinnakerHttpException e) {
          if (e.getResponseCode() == 404) {
                return getNotFoundCommitsResponse(projectKey, repositorySlug, requestParams.get("to"), requestParams.get("from"), stashMaster.getBaseUrl());
          }
          log.error(
                "Failed to fetch commits for {}/{}, reason: {}",
                projectKey, repositorySlug, e.getMessage()
            );
        } catch  (SpinnakerServerException e) {
          log.error(
            "Failed to fetch commits for {}/{}, reason: {}",
            projectKey, repositorySlug, e.getMessage()
          );
        }

        List<Map<String, Object>> result = new ArrayList<>();
        if (commitsResponse != null && commitsResponse.getValues() != null) {
            for (CompareCommitsResponse.Commit commit : commitsResponse.getValues()) {
                Map<String, Object> commitMap = new HashMap<>();
                commitMap.put("displayId", commit.getDisplayId());
                commitMap.put("id", commit.getId());
                commitMap.put("authorDisplayName", commit.getAuthor().getDisplayName());
                commitMap.put("timestamp", commit.getAuthorTimestamp());
                commitMap.put("message", commit.getMessage());
                commitMap.put("commitUrl", stashMaster.getBaseUrl() + "/projects/" + projectKey + "/repos/" + repositorySlug + "/commits/" + commit.getId());
                result.add(commitMap);
            }
        }
        return result;
    }
}
