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

package com.netflix.spinnaker.igor.scm.github

import com.netflix.spinnaker.igor.config.GitHubProperties
import com.netflix.spinnaker.igor.helpers.TestUtils
import com.netflix.spinnaker.igor.scm.AbstractCommitController
import com.netflix.spinnaker.igor.scm.github.client.GitHubClient
import com.netflix.spinnaker.igor.scm.github.client.GitHubMaster
import com.netflix.spinnaker.igor.scm.github.client.model.Author
import com.netflix.spinnaker.igor.scm.github.client.model.Commit
import com.netflix.spinnaker.igor.scm.github.client.model.CommitInfo
import com.netflix.spinnaker.igor.scm.github.client.model.CompareCommitsResponse
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant
import java.util.concurrent.Executors

/**
 * Tests for CommitController
 */
class CommitControllerSpec extends Specification {

    @Subject
    CommitController controller

    GitHubClient client = Mock(GitHubClient)

    def GITHUB_ADDRESS = "https://github.com"

    void setup() {
        controller = new CommitController(executor: Executors.newSingleThreadExecutor(), master: new GitHubMaster(gitHubClient: client, baseUrl : GITHUB_ADDRESS), gitHubProperties: new GitHubProperties(commitDisplayLength: 8))
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
        ['to' : "abcdef"] | _
        ['from' : "ghijk"] | _
    }

    void 'get 404 from client and return one commit'() {
        when:
        1 * client.getCompareCommits(projectKey, repositorySlug, queryParams.to, queryParams.from) >> { throw TestUtils.makeSpinnakerHttpException("http://foo.com", 404, ResponseBody.create("{}", MediaType.parse("application/json"))) }
        def result = controller.compareCommits(projectKey, repositorySlug, queryParams)

        then:
        result.size() == 1
        result[0].id == "NOT_FOUND"

        where:
        projectKey = 'key'
        repositorySlug = 'slug'
        queryParams | _
        ['to' : "abcdef", 'from' : 'ghijk'] | _
    }

    void 'compare commits'() {
        given:
        1 * client.getCompareCommits(projectKey, repositorySlug, toCommit, fromCommit) >>
          Calls.response(new CompareCommitsResponse(url: "", html_url: "", commits:
            [new Commit(html_url: "https://github.com/${projectKey}/${repositorySlug}/1234512345123451234512345", sha: "1234512345123451234512345", commitInfo: new CommitInfo(author: new Author(email: 'joecoder@project.com', date: Instant.ofEpochMilli(1433192015000), name: "Joe Coder"), message: "my commit")),
             new Commit(html_url: "https://github.com/${projectKey}/${repositorySlug}/67890678906789067890", sha: "67890678906789067890", commitInfo: new CommitInfo(author: new Author(email: 'janecoder@project.com', date: Instant.ofEpochMilli(1432078663000), name: "Jane Coder"), message: "bug fix"))]))

        when:
        List commitsResponse = controller.compareCommits(projectKey, repositorySlug, ['to': toCommit, 'from': fromCommit])

        then:
        commitsResponse.size() == 2

        with(commitsResponse[0]) {
            displayId == "12345123"
            id == "1234512345123451234512345"
            authorDisplayName == "Joe Coder"
            message == "my commit"
            commitUrl == "https://github.com/${projectKey}/${repositorySlug}/1234512345123451234512345"
            timestamp == Instant.ofEpochMilli(1433192015000)
        }

        with(commitsResponse[1]) {
            displayId == "67890678"
            id == "67890678906789067890"
            authorDisplayName == "Jane Coder"
            message == "bug fix"
            commitUrl == "https://github.com/${projectKey}/${repositorySlug}/67890678906789067890"
            timestamp == Instant.ofEpochMilli(1432078663000)
        }

        where:
        projectKey = 'key'
        repositorySlug = 'slug'
        toCommit = 'abcd'
        fromCommit = 'efgh'
    }
}
