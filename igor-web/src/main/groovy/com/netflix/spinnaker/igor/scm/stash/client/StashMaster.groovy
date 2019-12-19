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
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
import retrofit.RetrofitError

/**
 * Wrapper class for a collection of Stash clients
 */
@Slf4j
class StashMaster extends AbstractScmMaster {
    StashClient stashClient
    String baseUrl

  List<String> listDirectory(String projectKey, String repositorySlug, String path, String at) {
    try {
      return stashClient.listDirectory(projectKey, repositorySlug, path, at).toChildFilenames()
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

  String getTextFileContents(String projectKey, String repositorySlug, String path, String at) {
    try {
      return stashClient.getTextFileContents(projectKey, repositorySlug, path, at).toTextContents()
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
}
