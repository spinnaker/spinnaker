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

package com.netflix.spinnaker.igor.scm.github.client


import com.netflix.spinnaker.igor.scm.AbstractScmMaster
import com.netflix.spinnaker.igor.scm.github.client.model.Commit
import com.netflix.spinnaker.igor.scm.github.client.model.GetRepositoryContentResponse
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
import retrofit.RetrofitError

import java.util.stream.Collectors

/**
 * Wrapper class for a collection of GitHub clients
 */
@Slf4j
class GitHubMaster extends AbstractScmMaster {

  private static final String FILE_CONTENT_TYPE = "file";

  GitHubClient gitHubClient
  String baseUrl

  @Override
  List<String> listDirectory(String projectKey, String repositorySlug, String path, String ref) {
    try {
      List<GetRepositoryContentResponse> response = gitHubClient.listDirectory(projectKey, repositorySlug, path, ref);
      return response.stream()
        .map({ r -> r.path })
        .collect(Collectors.toList())
    } catch (RetrofitError e) {
      if (e.getKind() == RetrofitError.Kind.NETWORK) {
        throw new NotFoundException("Could not find the server ${baseUrl}")
      }
      log.error(
        "Failed to fetch file from {}/{}/{}, reason: {}",
        projectKey, repositorySlug, path, e.message
      )
      throw e
    }
  }

  @Override
  String getTextFileContents(String projectKey, String repositorySlug, String path, String ref) {
    try {
      GetRepositoryContentResponse response = gitHubClient.getFileContent(projectKey, repositorySlug, path, ref);
      if (FILE_CONTENT_TYPE != response.type) {
        throw new NotFoundException("Unexpected content type: ${response.type}");
      }
      return new String(Base64.mimeDecoder.decode(response.content));
    } catch (RetrofitError e) {
      if (e.getKind() == RetrofitError.Kind.NETWORK) {
        throw new NotFoundException("Could not find the server ${baseUrl}")
      }
      log.error(
        "Failed to fetch file from {}/{}/{}, reason: {}",
        projectKey, repositorySlug, path, e.message
      )
      throw e
    }
  }

  @Override
  Commit getCommitDetails(String projectKey, String repositorySlug, String sha) {
    return gitHubClient.commitInfo(projectKey, repositorySlug, sha)
  }
}
