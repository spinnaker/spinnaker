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

package com.netflix.spinnaker.igor.scm.stash.client

import com.netflix.spinnaker.igor.scm.AbstractScmMaster
import com.netflix.spinnaker.igor.scm.stash.client.model.TextLinesResponse
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
/**
 * Wrapper class for a collection of Stash clients
 */
@Slf4j
class StashMaster extends AbstractScmMaster {
  public static final int DEFAULT_PAGED_RESPONSE_LIMIT = 500

  StashClient stashClient
  String baseUrl

  List<String> listDirectory(String projectKey, String repositorySlug, String path, String ref) {
    try {
      return Retrofit2SyncCall.execute(stashClient.listDirectory(projectKey, repositorySlug, path, ref)).toChildFilenames()
    }catch (SpinnakerNetworkException e) {
      throw new NotFoundException("Could not find the server ${baseUrl}")
    }
    catch (SpinnakerServerException e) {
      log.error(
        "Failed to fetch file from {}/{}/{}, reason: {}",
        projectKey, repositorySlug, path, e.message
      )
      throw e
    }
  }

  String getTextFileContents(String projectKey, String repositorySlug, String path, String ref) {
    try {
      String contents = ""
      boolean lastPage = false
      int start = 0
      while (!lastPage) {
        log.debug("Retrieving text file contents from project: $projectKey, repo: $repositorySlug, path: $path, ref: $ref, start: $start")
        TextLinesResponse response = Retrofit2SyncCall.execute(stashClient.getTextFileContents(
          projectKey, repositorySlug, path, ref, DEFAULT_PAGED_RESPONSE_LIMIT, start))
        lastPage = response.isLastPage
        start = response.start + response.size
        contents += response.toTextContents() + "\n"
      }
      return contents
    } catch (SpinnakerNetworkException e) {
      throw new NotFoundException("Could not find the server ${baseUrl}")
    } catch(SpinnakerServerException e) {
      log.error(
        "Failed to fetch file from {}/{}/{}, reason: {}",
        projectKey, repositorySlug, path, e.message
      )
      throw e
    }
  }
}
