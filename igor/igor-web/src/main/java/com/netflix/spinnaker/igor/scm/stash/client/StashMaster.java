/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.scm.stash.client;

import com.netflix.spinnaker.igor.scm.AbstractScmMaster;
import com.netflix.spinnaker.igor.scm.stash.client.model.TextLinesResponse;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Wrapper class for a collection of Stash clients
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = false)
public class StashMaster extends AbstractScmMaster {
  public static final int DEFAULT_PAGED_RESPONSE_LIMIT = 500;

  private StashClient stashClient;
  private String baseUrl;

  @Override
  public List<String> listDirectory(String projectKey, String repositorySlug, String path, String ref) {
    try {
      return Retrofit2SyncCall.execute(stashClient.listDirectory(projectKey, repositorySlug, path, ref)).toChildFilenames();
    } catch (SpinnakerNetworkException e) {
      throw new NotFoundException("Could not find the server " + baseUrl);
    }
    catch (SpinnakerServerException e) {
      log.error(
        "Failed to fetch file from {}/{}/{}, reason: {}",
        projectKey, repositorySlug, path, e.getMessage()
      );
      throw e;
    }
  }

  @Override
  public String getTextFileContents(String projectKey, String repositorySlug, String path, String ref) {
    try {
      StringBuilder contents = new StringBuilder();
      boolean lastPage = false;
      int start = 0;
      while (!lastPage) {
        log.debug("Retrieving text file contents from project: {}, repo: {}, path: {}, ref: {}, start: {}",
          projectKey, repositorySlug, path, ref, start);
        TextLinesResponse response = Retrofit2SyncCall.execute(stashClient.getTextFileContents(
          projectKey, repositorySlug, path, ref, DEFAULT_PAGED_RESPONSE_LIMIT, start));
        lastPage = response.isLastPage();
        start = response.getStart() + response.getSize();
        contents.append(response.toTextContents()).append("\n");
      }
      return contents.toString();
    } catch (SpinnakerNetworkException e) {
      throw new NotFoundException("Could not find the server " + baseUrl);
    } catch(SpinnakerServerException e) {
      log.error(
        "Failed to fetch file from {}/{}/{}, reason: {}",
        projectKey, repositorySlug, path, e.getMessage()
      );
      throw e;
    }
  }
}
