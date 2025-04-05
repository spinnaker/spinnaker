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

package com.netflix.spinnaker.igor.scm.bitbucket.client

import com.netflix.spinnaker.igor.config.BitBucketConfig
import com.netflix.spinnaker.igor.helpers.TestUtils
import com.netflix.spinnaker.igor.scm.bitbucket.client.model.CompareCommitsResponse
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.mock.Calls
import spock.lang.Shared
import spock.lang.Specification

/**
 * Tests that BitBucket bitBucketClient correctly binds to underlying model as expected
 */
class BitBucketClientSpec extends Specification {

  @Shared
  BitBucketClient client

  @Shared
  MockWebServer server

  void setup() {
    server = new MockWebServer()
  }

  void cleanup() {
    server.shutdown()
  }

  private void setResponse(String body) {
    server.enqueue(
      new MockResponse()
        .setBody(body)
        .setHeader('Content-Type', 'text/xml;charset=UTF-8')
    )
    server.start()
    client = new BitBucketConfig().bitBucketClient(server.url('/').toString(), 'username', 'password', TestUtils.makeOkHttpClientConfig())
  }

  void 'getCompareCommits'() {
    given:
    setResponse getCompareCommitsResponse()

    when:
    CompareCommitsResponse commitsResponse = Retrofit2SyncCall.execute(client.getCompareCommits('foo', 'repo', [toCommit: 'abcd', fromCommit: 'defg']))

    then:
    commitsResponse.values.size() == 2

    with(commitsResponse.values.get(0)) {
      hash == 'adc708bb1251ac8177474d6a1b40f738f2dc44dc'
      author.raw == 'Joe Coder <jcoder@code.com>'
      author.user.username == 'jcoder'
      author.user.display_name == 'Joe Coder'
      message == "don't call evaluate if user is null"
      date == new Date(1487025891000)
    }

    with(commitsResponse.values.get(1)) {
      hash == '70a121a7e8f86c54467a43bd29066e5ff1174510'
      author.raw == 'Jane Coder <jane.coder@code.com>'
      author.user.username == 'jane.coder'
      author.user.display_name == 'Jane Coder'
      message == "Merge branch 'my-work' into master"
      date == new Date(1487198691000)
    }
  }

  String getCompareCommitsResponse() {

    return '\n' +
      '{ "pagelen": 100, "values": [ { "hash": "adc708bb1251ac8177474d6a1b40f738f2dc44dc", "links": { "self": ' +
      '{ "href": "https://api.bitbucket.org/2.0/repositories/foo/repo/commit/adc708bb1251ac8177474d6a1b40f738f2dc44dc" }, ' +
      '"html": { "href": "https://bitbucket.org/foo/repo/commits/adc708bb1251ac8177474d6a1b40f738f2dc44dc" } }, ' +
      '"author": { "raw": "Joe Coder <jcoder@code.com>", "user": { "username": "jcoder", "display_name": "Joe Coder", ' +
      '"type": "user", "links": { "self": { "href": "https://api.bitbucket.org/2.0/users/jcoder" }, "html": { ' +
      '"href": "https://bitbucket.org/jcoder/" } } } }, "date": "2017-02-13T22:44:51+00:00", ' +
      '"message": "don\'t call evaluate if user is null", "type": "commit" }, { "hash": "70a121a7e8f86c54467a43bd29066e5ff1174510", ' +
      '"links": { "self": { "href": "https://api.bitbucket.org/2.0/repositories/foo/repo/commit/70a121a7e8f86c54467a43bd29066e5ff1174510" }, ' +
      '"html": { "href": "https://bitbucket.org/foo/repo/commits/70a121a7e8f86c54467a43bd29066e5ff1174510" } }, ' +
      '"author": { "raw": "Jane Coder <jane.coder@code.com>", "user": { "username": "jane.coder", "display_name": "Jane Coder", ' +
      '"type": "user", "links": { "self": { "href": "https://api.bitbucket.org/2.0/users/jane.coder" }, "html": { ' +
      '"href": "https://bitbucket.org/jane.coder/" } } } }, "date": "2017-02-15T22:44:51+00:00", ' +
      '"message": "Merge branch \'my-work\' into master", "type": "commit" } ], ' +
      '"next": "https://api.bitbucket.org/2.0/repositories/foo/repo/commits?pagelen=100&include=adc708bb1251ac8177474d6a1b40f738f2dc44dc&page=2"}'
  }
}
