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

package com.netflix.spinnaker.igor.scm.github

import com.netflix.spinnaker.igor.config.GitHubProperties
import com.netflix.spinnaker.igor.scm.AbstractCommitController
import com.netflix.spinnaker.igor.scm.github.client.GitHubMaster
import com.netflix.spinnaker.igor.scm.github.client.model.CompareCommitsResponse
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
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
@RestController(value = "GitHubCommitController")
@ConditionalOnProperty('github.base-url')
@RequestMapping("/github")
class CommitController extends AbstractCommitController {
    @Autowired
    GitHubMaster master

    @Autowired
    GitHubProperties gitHubProperties

    @RequestMapping(method = RequestMethod.GET, value = '/{projectKey}/{repositorySlug}/compareCommits')
    List compareCommits(@PathVariable(value = 'projectKey') String projectKey, @PathVariable(value='repositorySlug') String repositorySlug, @RequestParam Map<String, String> requestParams) {
        super.compareCommits(projectKey, repositorySlug, requestParams)
        CompareCommitsResponse commitsResponse
        List result = []

        try {
            commitsResponse = master.gitHubClient.getCompareCommits(projectKey, repositorySlug, requestParams.to, requestParams.from)
        } catch (RetrofitError e) {
            if(e.getKind() == RetrofitError.Kind.NETWORK) {
                throw new NotFoundException("Could not find the server ${master.baseUrl}")
            } else if(e.response.status == 404) {
                return getNotFoundCommitsResponse(projectKey, repositorySlug, requestParams.to, requestParams.from, master.baseUrl)
            }
            log.error("Unhandled error response, acting like commit response was not found", e)
            return getNotFoundCommitsResponse(projectKey, repositorySlug, requestParams.to, requestParams.from, master.baseUrl)
        }

        commitsResponse.commits.each {
            result << [displayId: it?.sha.substring(0,gitHubProperties.commitDisplayLength), id: it?.sha, authorDisplayName: it?.commitInfo?.author?.name,
                       timestamp: it?.commitInfo?.author?.date, message : it?.commitInfo?.message, commitUrl: it?.html_url]
        }
        return result
    }
}
