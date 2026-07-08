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

package com.netflix.spinnaker.igor.scm.bitbucket;

import com.netflix.spinnaker.igor.config.BitBucketProperties;
import com.netflix.spinnaker.igor.exceptions.UnhandledDownstreamServiceErrorException;
import com.netflix.spinnaker.igor.scm.AbstractCommitController;
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketMaster;
import com.netflix.spinnaker.igor.scm.bitbucket.client.model.CompareCommitsResponse;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
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
@RestController(value = "BitBucketCommitController")
@ConditionalOnProperty("bitbucket.base-url")
@RequestMapping("/bitbucket")
public class CommitController extends AbstractCommitController {
  @Autowired
  private BitBucketMaster bitBucketMaster;

  @Autowired
  private BitBucketProperties bitBucketProperties;

  @RequestMapping(method = RequestMethod.GET, value = "/{projectKey}/{repositorySlug}/compareCommits")
  public List<Map<String, Object>> compareCommits(@PathVariable(value = "projectKey") String projectKey, @PathVariable(value="repositorySlug") String repositorySlug, @RequestParam Map<String, String> requestParams) {
    super.compareCommits(projectKey, repositorySlug, requestParams);
    CompareCommitsResponse commitsResponse;
    List<Map<String, Object>> result = new ArrayList<>();

    /*
     * BitBucket Cloud API v2.0 does not implement a 'compare commits' feature like GitHub or BitBucket Server / Stash.
     * Instead, you need to iteratively request commits until you have the range you need.
     * BitBucket limits the number of commits retrieve to a max of 100 at a time.
     */

    try {
      Map<String, String> queryParams = new HashMap<>();
      queryParams.put("limit", "100");
      queryParams.put("include", requestParams.get("to"));
      commitsResponse = Retrofit2SyncCall.execute(bitBucketMaster.getBitBucketClient().getCompareCommits(projectKey, repositorySlug, queryParams));

      boolean foundFromCommit = commitsResponse.getValues().stream()
          .anyMatch(commit -> commit.getHash().equals(requestParams.get("from")));

      if (!foundFromCommit) {
        while (!commitsResponse.getValues().stream().anyMatch(commit -> commit.getHash().equals(requestParams.get("from")))) {
          Map<String, String> nextQueryParams = new HashMap<>();
          nextQueryParams.put("limit", "100");
          nextQueryParams.put("include", commitsResponse.getValues().get(commitsResponse.getValues().size() - 1).getHash());
          CompareCommitsResponse response = Retrofit2SyncCall.execute(bitBucketMaster.getBitBucketClient().getCompareCommits(projectKey, repositorySlug, nextQueryParams));
          commitsResponse.getValues().addAll(response.getValues());
        }
        // Remove duplicates based on hash
        Map<String, CompareCommitsResponse.Commit> uniqueCommits = new HashMap<>();
        for (CompareCommitsResponse.Commit commit : commitsResponse.getValues()) {
          uniqueCommits.put(commit.getHash(), commit);
        }
        commitsResponse.setValues(new ArrayList<>(uniqueCommits.values()));
      }

      int fromIndex = -1;
      for (int i = 0; i < commitsResponse.getValues().size(); i++) {
        if (commitsResponse.getValues().get(i).getHash().equals(requestParams.get("from"))) {
          fromIndex = i;
          break;
        }
      }
      if (fromIndex > -1) {
        commitsResponse.setValues(commitsResponse.getValues().subList(0, fromIndex + 1));
      }
    } catch (SpinnakerServerException e) {
      if (e instanceof SpinnakerHttpException && ((SpinnakerHttpException) e).getResponseCode() == 404) {
        return getNotFoundCommitsResponse(projectKey, repositorySlug, requestParams.get("to"), requestParams.get("from"), bitBucketMaster.getBaseUrl());
      }
      throw new UnhandledDownstreamServiceErrorException("Unhandled bitbucket error for " + bitBucketMaster.getBaseUrl(), e);
    }

    for (CompareCommitsResponse.Commit commit : commitsResponse.getValues()) {
      Map<String, Object> commitMap = new HashMap<>();
      commitMap.put("displayId", commit.getHash().substring(0, bitBucketProperties.getCommitDisplayLength()));
      commitMap.put("id", commit.getHash());
      commitMap.put("authorDisplayName", commit.getAuthor().getUser().getDisplayName());
      commitMap.put("timestamp", commit.getDate());
      commitMap.put("message", commit.getMessage());
      commitMap.put("commitUrl", commit.getHtmlHref());
      result.add(commitMap);
    }

    return result;
  }
}
