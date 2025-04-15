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

import com.netflix.spinnaker.igor.config.StashConfig
import com.netflix.spinnaker.igor.helpers.TestUtils
import com.netflix.spinnaker.igor.scm.ScmMaster
import com.netflix.spinnaker.igor.scm.stash.client.model.CompareCommitsResponse
import com.netflix.spinnaker.igor.scm.stash.client.model.DirectoryListingResponse
import com.netflix.spinnaker.igor.scm.stash.client.model.TextLinesResponse
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests that Stash stashClient correctly binds to underlying model as expected
 */
class StashClientSpec extends Specification {

    @Shared
    StashClient client

    @Shared
    MockWebServer server

    void setup() {
        server = new MockWebServer()
    }

    void cleanup() {
        server.shutdown()
    }

    private void setResponse(String contentType, String body) {
        server.enqueue(
            new MockResponse()
                .setBody(body)
                .setHeader('Content-Type', contentType)
        )
        server.start()
        client = new StashConfig().stashClient(server.url('/').toString(), 'username', 'password', TestUtils.makeOkHttpClientConfig())
    }

    void 'getCompareCommits'() {
        given:
        setResponse('text/xml;charset=UTF-8', compareCommitsResponse)

        when:
        CompareCommitsResponse commitsResponse = Retrofit2SyncCall.execute(client.getCompareCommits('foo', 'repo', [toCommit:'abcd', fromCommit:'defg']))

        then:
        commitsResponse.size == 2
        commitsResponse.isLastPage == false
        commitsResponse.start == 0
        commitsResponse.limit == 2
        commitsResponse.nextPageStart == 2
        commitsResponse.values.size() == 2

        commitsResponse.values[0].id == "adc708bb1251ac8177474d6a1b40f738f2dc44dc"
        commitsResponse.values[0].displayId == "adc708bb125"
        commitsResponse.values[0].author.name == "jcoder"
        commitsResponse.values[0].author.emailAddress == "jcoder@code.com"
        commitsResponse.values[0].author.id == 1817
        commitsResponse.values[0].author.displayName == "Joe Coder"
        commitsResponse.values[0].author.active == true
        commitsResponse.values[0].author.slug == "jcoder"
        commitsResponse.values[0].author.type == "NORMAL"
        commitsResponse.values[0].authorTimestamp == 1432081865000
        commitsResponse.values[0].message == "don't call evaluate if user is null"
        commitsResponse.values[0].parents[0].id == "70a121a7e8f86c54467a43bd29066e5ff1174510"
        commitsResponse.values[0].parents[0].displayId == "70a121a7e8f"

        commitsResponse.values[1].id == "70a121a7e8f86c54467a43bd29066e5ff1174510"
        commitsResponse.values[1].displayId == "70a121a7e8f"
        commitsResponse.values[1].author.name == "jcoder"
        commitsResponse.values[1].author.emailAddress == "jcoder@code.com"
        commitsResponse.values[1].author.id == 1817
        commitsResponse.values[1].author.displayName == "Joe Coder"
        commitsResponse.values[1].author.active == true
        commitsResponse.values[1].author.slug == "jcoder"
        commitsResponse.values[1].author.type == "NORMAL"
        commitsResponse.values[1].authorTimestamp == 1432081404000
        commitsResponse.values[1].message == "Merge branch 'my-work' into master"
        commitsResponse.values[1].parents[0].id == "3c3b942b09767e01c25e42bcb65a6630e8b2fc75"
        commitsResponse.values[1].parents[0].displayId == "3c3b942b097"
        commitsResponse.values[1].parents[1].id == "13881c94156429084910e6ca417c48fcb6d74be8"
        commitsResponse.values[1].parents[1].displayId == "13881c94156"
    }

  void 'listDirectory'() {
    given:
    setResponse('application/json', listDirectoryResponse)

    when:
    DirectoryListingResponse dirListResponse = Retrofit2SyncCall.execute(client.listDirectory('foo', 'repo', '.spinnaker', ScmMaster.DEFAULT_GIT_REF))

    then:
    dirListResponse.children.size == 2
    dirListResponse.children.isLastPage == false
    dirListResponse.children.start == 0
    dirListResponse.children.limit == 500

    dirListResponse.children.values[0].path.name == "rocket.yml"
    dirListResponse.children.values[0].path.extension == "yml"
    dirListResponse.children.values[0].type == "FILE"
    dirListResponse.children.values[0].size == 39

    dirListResponse.children.values[1].path.name == "spinnaker.yml"
    dirListResponse.children.values[1].path.extension == "yml"
    dirListResponse.children.values[1].type == "FILE"
    dirListResponse.children.values[1].size == 5998
  }

  @Unroll
  void 'getTextFileContents'() {
    given:
    setResponse('application/json', contents)

    when:
    TextLinesResponse response = Retrofit2SyncCall.execute(client.getTextFileContents('foo', 'repo', 'bananas.txt', ScmMaster.DEFAULT_GIT_REF, 1, start))

    then:
    response.size == size
    response.isLastPage == isLastPage
    response.start == start
    response.size == response.lines.size()
    response.lines[0].text == "bananas!"

    where:
    contents                      | start | size | isLastPage
    firstTextFileContentsResponse | 0     | 1    | false
    lastTextFileContentsResponse  | 1     | 1    | true
  }

  static final String compareCommitsResponse = """
    {
      "values":[
        {
          "id":"adc708bb1251ac8177474d6a1b40f738f2dc44dc",
          "displayId":"adc708bb125",
          "author":{
            "name":"jcoder",
            "emailAddress":"jcoder@code.com",
            "id":1817,
            "displayName":"Joe Coder",
            "active":true,
            "slug":"jcoder",
            "type":"NORMAL"
          },
          "authorTimestamp":1432081865000,
          "message":"don't call evaluate if user is null",
          "parents":[
            {
              "id":"70a121a7e8f86c54467a43bd29066e5ff1174510",
              "displayId":"70a121a7e8f"
            }
          ]
        },
        {
          "id":"70a121a7e8f86c54467a43bd29066e5ff1174510",
          "displayId":"70a121a7e8f",
          "author":{
            "name":"jcoder",
            "emailAddress":"jcoder@code.com",
            "id":1817,
            "displayName":"Joe Coder",
            "active":true,
            "slug":"jcoder",
            "type":"NORMAL"
          },
          "authorTimestamp":1432081404000,
          "message":"Merge branch 'my-work' into master",
          "parents":[
            {
              "id":"3c3b942b09767e01c25e42bcb65a6630e8b2fc75",
              "displayId":"3c3b942b097"
            },
            {
              "id":"13881c94156429084910e6ca417c48fcb6d74be8",
              "displayId":"13881c94156"
            }
          ]
        }
      ],
      "size":2,
      "isLastPage":false,
      "start":0,
      "limit":2,
      "nextPageStart":2
    }
    """

  static final String listDirectoryResponse = """
    {
      "path": {
        "components": [
          ".spinnaker"
        ],
        "parent": "",
        "name": ".spinnaker",
        "extension": "netflix",
        "toString": ".spinnaker"
      },
      "revision": "refs/heads/master",
      "children": {
        "size": 2,
        "limit": 500,
        "isLastPage": true,
        "values": [
          {
            "path": {
              "components": [
                "rocket.yml"
              ],
              "parent": "",
              "name": "rocket.yml",
              "extension": "yml",
              "toString": "rocket.yml"
            },
            "contentId": "124a48ac58141263a3ceea98b4d157bc71950a1a",
            "type": "FILE",
            "size": 39
          },
          {
            "path": {
              "components": [
                "spinnaker.yml"
              ],
              "parent": "",
              "name": "spinnaker.yml",
              "extension": "yml",
              "toString": "spinnaker.yml"
            },
            "contentId": "1c773bd42267fc2ef48c38b8e4292199277591fb",
            "type": "FILE",
            "size": 5998
          }
        ],
        "start": 0
      }
    }
    """.stripIndent()

  static final String firstTextFileContentsResponse = """
    {
      "lines": [
        {
          "text": "bananas!"
        }
      ],
      "start": 0,
      "size": 1,
      "isLastPage": false
    }
    """.stripIndent()

  static final String lastTextFileContentsResponse = """
    {
      "lines": [
        {
          "text": "bananas!"
        }
      ],
      "start": 1,
      "size": 1,
      "isLastPage": true
    }
    """.stripIndent()

}
