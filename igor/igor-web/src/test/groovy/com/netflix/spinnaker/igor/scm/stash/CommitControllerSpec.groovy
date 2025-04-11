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

package com.netflix.spinnaker.igor.scm.stash

import com.netflix.spinnaker.igor.helpers.TestUtils
import com.netflix.spinnaker.igor.scm.AbstractCommitController
import com.netflix.spinnaker.igor.scm.stash.client.StashClient
import com.netflix.spinnaker.igor.scm.stash.client.StashMaster
import com.netflix.spinnaker.igor.scm.stash.client.model.Author
import com.netflix.spinnaker.igor.scm.stash.client.model.Commit
import com.netflix.spinnaker.igor.scm.stash.client.model.CompareCommitsResponse
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.Executors
/**
 * Tests for CommitController
 */
class CommitControllerSpec extends Specification {

    @Subject
    CommitController controller

    StashClient client = Mock(StashClient)

    def STASH_ADDRESS = "https://stash.com"

    void setup() {
        controller = new CommitController(executor: Executors.newSingleThreadExecutor(), stashMaster: new StashMaster(stashClient: client, baseUrl : STASH_ADDRESS))

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

    void 'get 404 from stashClient and return one commit'() {
        when:
        1 * client.getCompareCommits(projectKey, repositorySlug, queryParams) >> { throw TestUtils.makeSpinnakerHttpException("http://foo.com", 404, ResponseBody.create("{}", MediaType.parse("application/json"))) }
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
        1 * client.getCompareCommits(projectKey, repositorySlug, [to: toCommit, from: fromCommit]) >> Calls.response(new CompareCommitsResponse(size: 2, values:
            [new Commit(message: "my commit", displayId: "12345", id: "1234512345123451234512345", author : new Author(displayName: "Joe Coder"), authorTimestamp: 1432081865000),
             new Commit(message: "bug fix", displayId: "67890", id: "67890678906789067890", author : new Author(displayName: "Jane Coder"), authorTimestamp: 1432078663000)]))

        when:
        List commitsResponse = controller.compareCommits(projectKey, repositorySlug, ['to': toCommit, 'from': fromCommit])

        then:
        commitsResponse.size() == 2
        commitsResponse[0].displayId == "12345"
        commitsResponse[0].id == "1234512345123451234512345"
        commitsResponse[0].authorDisplayName == "Joe Coder"
        commitsResponse[0].timestamp == 1432081865000
        commitsResponse[0].commitUrl == "${STASH_ADDRESS}/projects/${projectKey}/repos/${repositorySlug}/commits/${commitsResponse[0].id}"
        commitsResponse[0].message == "my commit"

        commitsResponse[1].displayId == "67890"
        commitsResponse[1].id == "67890678906789067890"
        commitsResponse[1].authorDisplayName == "Jane Coder"
        commitsResponse[1].timestamp == 1432078663000
        commitsResponse[1].commitUrl == "${STASH_ADDRESS}/projects/${projectKey}/repos/${repositorySlug}/commits/${commitsResponse[1].id}"
        commitsResponse[1].message == "bug fix"

        where:
        projectKey = 'key'
        repositorySlug = 'slug'
        toCommit = 'abcd'
        fromCommit = 'efgh'
    }
}
