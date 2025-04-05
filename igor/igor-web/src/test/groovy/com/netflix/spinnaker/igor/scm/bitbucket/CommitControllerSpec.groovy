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

package com.netflix.spinnaker.igor.scm.bitbucket

import com.netflix.spinnaker.igor.config.BitBucketProperties
import com.netflix.spinnaker.igor.helpers.TestUtils
import com.netflix.spinnaker.igor.scm.AbstractCommitController
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketClient
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketMaster
import com.netflix.spinnaker.igor.scm.bitbucket.client.model.Author
import com.netflix.spinnaker.igor.scm.bitbucket.client.model.Commit
import com.netflix.spinnaker.igor.scm.bitbucket.client.model.CompareCommitsResponse
import com.netflix.spinnaker.igor.scm.bitbucket.client.model.User
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

  BitBucketClient client = Mock(BitBucketClient)

  def BITBUCKET_ADDRESS = "https://api.bitbucket.org"

  void setup() {
    controller = new CommitController(executor: Executors.newSingleThreadExecutor(), bitBucketMaster: new BitBucketMaster(bitBucketClient: client, baseUrl : BITBUCKET_ADDRESS), bitBucketProperties: new BitBucketProperties(commitDisplayLength: 7))
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
    ['to' : "1234512345123451234512345"] | _
    ['from' : "67890678906789067890"] | _
  }

  void 'get 404 from bitBucketClient and return one commit'() {
    when:
    1 * client.getCompareCommits(projectKey, repositorySlug, clientParams) >> { throw TestUtils.makeSpinnakerHttpException("http://foo.com", 404, ResponseBody.create("{}", MediaType.parse("application/json"))) }
    def result = controller.compareCommits(projectKey, repositorySlug, controllerParams)

    then:
    result.size() == 1
    result[0].id == "NOT_FOUND"

    where:
    projectKey = 'key'
    repositorySlug = 'slug'
    controllerParams =  ['to': "1234512345123451234512345", 'from': '67890678906789067890']
    clientParams = ['limit': 100, 'include' : controllerParams.to]
  }

  void 'compare commits'() {
    given:
    1 * client.getCompareCommits(projectKey, repositorySlug, clientParams) >> Calls.response(new CompareCommitsResponse(values:
      [new Commit(message: "my commit", hash: "1234512345123451234512345", date: "2017-02-13T22:44:51+00:00",
        author: new Author(raw: "Joe Coder <jcoder@code.com>",
          user: new User(display_name: "Joe Coder", username: "jcoder")),
        html_href: "https://bitbucket.org/${projectKey}/${repositorySlug}/commits/1234512345123451234512345"
      ),
       new Commit(message: "bug fix", hash: "67890678906789067890", date: "2017-02-15T22:44:51+00:00",
         author: new Author(raw: "Jane Coder <jane.coder@code.com>",
           user: new User(display_name: "Jane Coder", username: "jane.coder")),
         html_href: "https://bitbucket.org/${projectKey}/${repositorySlug}/commits/67890678906789067890"
       )
      ]))

    when:
    List commitsResponse = controller.compareCommits(projectKey, repositorySlug, controllerParams)

    then:
    commitsResponse.size() == 2

    with(commitsResponse[0]) {
      displayId == "1234512"
      id == "1234512345123451234512345"
      authorDisplayName == "Joe Coder"
      timestamp == new Date(1487025891000)
      message == "my commit"
      commitUrl == "https://bitbucket.org/${projectKey}/${repositorySlug}/commits/${commitsResponse[0].id}"
    }

    with(commitsResponse[1]) {
      displayId == "6789067"
      id == "67890678906789067890"
      authorDisplayName == "Jane Coder"
      timestamp == new Date(1487198691000)
      message == "bug fix"
      commitUrl == "https://bitbucket.org/${projectKey}/${repositorySlug}/commits/${commitsResponse[1].id}"
    }

    where:
    projectKey = 'key'
    repositorySlug = 'slug'
    controllerParams =  ['to': "1234512345123451234512345", 'from': '67890678906789067890']
    clientParams = ['limit': 100, 'include' : controllerParams.to]
  }
}
