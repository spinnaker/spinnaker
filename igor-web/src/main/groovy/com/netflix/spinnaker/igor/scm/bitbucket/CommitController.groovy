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

package com.netflix.spinnaker.igor.scm.bitbucket

import com.netflix.spinnaker.igor.config.BitBucketProperties
import com.netflix.spinnaker.igor.exceptions.UnhandledDownstreamServiceErrorException
import com.netflix.spinnaker.igor.scm.AbstractCommitController
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketMaster
import com.netflix.spinnaker.igor.scm.bitbucket.client.model.CompareCommitsResponse
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import retrofit.RetrofitError

@Slf4j
@RestController(value = "BitBucketCommitController")
@ConditionalOnProperty('bitbucket.base-url')
@RequestMapping("/bitbucket")
class CommitController extends AbstractCommitController {
  @Autowired
  BitBucketMaster bitBucketMaster

  @Autowired
  BitBucketProperties bitBucketProperties

  @RequestMapping(method = RequestMethod.GET, value = '/{projectKey}/{repositorySlug}/compareCommits')
  List compareCommits(@PathVariable(value = 'projectKey') String projectKey, @PathVariable(value='repositorySlug') String repositorySlug, @RequestParam Map<String, String> requestParams) {
    super.compareCommits(projectKey, repositorySlug, requestParams)
    CompareCommitsResponse commitsResponse
    List result = []

    /*
     * BitBucket Cloud API v2.0 does not implement a 'compare commits' feature like GitHub or BitBucket Server / Stash.
     * Instead, you need to iteratively request commits until you have the range you need.
     * BitBucket limits the number of commits retrieve to a max of 100 at a time.
     */

    try {
      commitsResponse = bitBucketMaster.bitBucketClient.getCompareCommits(projectKey, repositorySlug, ['limit': 100, 'include': requestParams.to])
      if (!commitsResponse.values.any { it.hash == requestParams.from }) {
        while (!commitsResponse.values.any { it.hash == requestParams.from }) {
          def response = bitBucketMaster.bitBucketClient.getCompareCommits(projectKey, repositorySlug, ['limit': 100, 'include': commitsResponse.values.last().hash])
          commitsResponse.values.addAll(response.values)
        }
        commitsResponse.values.unique { a, b -> a.hash <=> b.hash }
      }

      def fromIndex = commitsResponse.values.findIndexOf { it.hash == requestParams.from }
      if (fromIndex > -1) {
        commitsResponse.values = commitsResponse.values.subList(0, fromIndex + 1)
      }
    } catch (RetrofitError e) {
      if (e.response.status == 404) {
        return getNotFoundCommitsResponse(projectKey, repositorySlug, requestParams.to, requestParams.from, bitBucketMaster.baseUrl)
      }
      throw new UnhandledDownstreamServiceErrorException("Unhandled bitbucket error for ${bitBucketMaster.baseUrl}", e)
    }

    commitsResponse.values.each {
      result << [displayId: it?.hash.substring(0,bitBucketProperties.commitDisplayLength), id: it?.hash,
                 authorDisplayName: it?.author?.user?.display_name, timestamp: it?.date, message : it?.message,
                 commitUrl: it?.html_href]
    }

    return result
  }
}
