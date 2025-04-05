/*
 * Copyright 2017 bol.com
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

package com.netflix.spinnaker.igor.scm.gitlab

import com.netflix.spinnaker.igor.config.GitLabProperties
import com.netflix.spinnaker.igor.helpers.TestUtils
import com.netflix.spinnaker.igor.scm.AbstractCommitController
import com.netflix.spinnaker.igor.scm.gitlab.client.GitLabClient
import com.netflix.spinnaker.igor.scm.gitlab.client.GitLabMaster
import com.netflix.spinnaker.igor.scm.gitlab.client.model.Commit
import com.netflix.spinnaker.igor.scm.gitlab.client.model.CompareCommitsResponse
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant
import java.util.concurrent.Executors

/**
 * Tests for GitLab CommitController
 */
class CommitControllerSpec extends Specification {

    @Subject
    CommitController controller

    GitLabClient client = Mock(GitLabClient)

    def GITLAB_ADDRESS = "https://gitlab.com"

    void setup() {
        def props = new GitLabProperties()
        props.baseUrl = GITLAB_ADDRESS
        props.commitDisplayLength = 8
        controller = new CommitController(new GitLabMaster(client, GITLAB_ADDRESS), props)
    }

    void 'missing query params'() {
        when:
        controller.compareCommits(projectKey, repositorySlug, queryParams)

        then:
        thrown(AbstractCommitController.MissingParametersException)

        where:
        projectKey = 'key'
        repositorySlug = 'slug'
        queryParams | _
        ['to': "abcdef"] | _
        ['from': "ghijk"] | _
    }

    void 'get 404 from client and return one commit'() {
        when:
        1 * client.getCompareCommits(projectKey, repositorySlug, [from: queryParams.to, to: queryParams.from]) >> {
            throw TestUtils.makeSpinnakerHttpException("http://foo.com", 404, ResponseBody.create("{}", MediaType.parse("application/json")))
        }
        def result = controller.compareCommits(projectKey, repositorySlug, queryParams)

        then:
        result.size() == 1
        result[0].id == "NOT_FOUND"

        where:
        projectKey = 'key'
        repositorySlug = 'slug'
        queryParams | _
        ['to': "abcdef", 'from': 'ghijk'] | _
    }

    void 'compare commits'() {
        given:
        1 * client.getCompareCommits(projectKey, repositorySlug, [from: toCommit, to: fromCommit]) >>
          Calls.response(new CompareCommitsResponse(
            [new Commit("1234512345123451234512345", "Joe Coder", new Date(1433192015000), "my commit"),
             new Commit("67890678906789067890", "Jane Coder", new Date(1432078663000), "bug fix")] as List<Commit>))

        when:
        List commitsResponse = controller.compareCommits(projectKey, repositorySlug, ['to': toCommit, 'from': fromCommit])

        then:
        commitsResponse.size() == 2

        with(commitsResponse[0]) {
            displayId == "12345123"
            id == "1234512345123451234512345"
            authorDisplayName == "Joe Coder"
            message == "my commit"
            commitUrl == "https://gitlab.com/${projectKey}/${repositorySlug}/commit/1234512345123451234512345"
            timestamp == new Date(1433192015000)
        }

        with(commitsResponse[1]) {
            displayId == "67890678"
            id == "67890678906789067890"
            authorDisplayName == "Jane Coder"
            message == "bug fix"
            commitUrl == "https://gitlab.com/${projectKey}/${repositorySlug}/commit/67890678906789067890"
            timestamp == new Date(1432078663000)
        }

        where:
        projectKey = 'key'
        repositorySlug = 'slug'
        toCommit = 'abcd'
        fromCommit = 'efgh'
    }
}
