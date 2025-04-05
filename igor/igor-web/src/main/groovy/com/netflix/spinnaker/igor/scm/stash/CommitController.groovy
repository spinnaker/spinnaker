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

package com.netflix.spinnaker.igor.scm.stash

import com.netflix.spinnaker.igor.scm.AbstractCommitController
import com.netflix.spinnaker.igor.scm.stash.client.StashMaster
import com.netflix.spinnaker.igor.scm.stash.client.model.CompareCommitsResponse
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Slf4j
@RestController(value = "StashCommitController")
@ConditionalOnProperty('stash.base-url')
@RequestMapping("/stash")
class CommitController extends AbstractCommitController {
    @Autowired
    StashMaster stashMaster

    @RequestMapping(method = RequestMethod.GET, value = '/{projectKey}/{repositorySlug}/compareCommits')
    List compareCommits(@PathVariable(value = 'projectKey') String projectKey, @PathVariable(value='repositorySlug') String repositorySlug, @RequestParam Map<String, String> requestParams) {
        super.compareCommits(projectKey, repositorySlug, requestParams)
        CompareCommitsResponse commitsResponse
        try {
            commitsResponse = Retrofit2SyncCall.execute(stashMaster.stashClient.getCompareCommits(projectKey, repositorySlug, requestParams))
        }   catch (SpinnakerNetworkException e) {
          throw new NotFoundException("Could not find the server ${stashMaster.baseUrl}")
        } catch  (SpinnakerHttpException e) {
          if(e.getResponseCode() == 404) {
                return getNotFoundCommitsResponse(projectKey, repositorySlug, requestParams.to, requestParams.from, stashMaster.baseUrl)
          }
          log.error(
                "Failed to fetch commits for {}/{}, reason: {}",
                projectKey, repositorySlug, e.message
            )
        }catch  (SpinnakerServerException e) {
          log.error(
            "Failed to fetch commits for {}/{}, reason: {}",
            projectKey, repositorySlug, e.message
          )
        }

        List result = []
        commitsResponse?.values?.each {
            result << [displayId: it?.displayId, id: it?.id, authorDisplayName: it?.author?.displayName,
                       timestamp: it?.authorTimestamp, message : it?.message, commitUrl:
                           "${stashMaster.baseUrl}/projects/${projectKey}/repos/${repositorySlug}/commits/${it.id}".toString()]
        }
        return result
    }
}
