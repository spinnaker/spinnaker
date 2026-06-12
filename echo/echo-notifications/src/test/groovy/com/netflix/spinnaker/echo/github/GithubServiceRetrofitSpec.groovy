/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.github

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import spock.lang.Specification
import spock.lang.Subject

/**
 * Tests for GithubService to ensure proper Retrofit 2 handling after the fix for
 * SpinnakerConversionException when updateCheck return type was corrected from
 * Call<Response<ResponseBody>> to Call<ResponseBody>.
 *
 * The bug was that Call<Response<ResponseBody>> was attempting to deserialize the
 * GitHub response into a retrofit2.Response object, which has no default constructor
 * and caused Jackson to fail with "Cannot construct instance of retrofit2.Response".
 */
class GithubServiceRetrofitSpec extends Specification {

  MockWebServer server
  @Subject
  GithubService githubService

  def setup() {
    server = new MockWebServer()
    server.start()

    def retrofit = new Retrofit.Builder()
      .baseUrl(server.url("/"))
      .addConverterFactory(JacksonConverterFactory.create())
      .build()

    githubService = retrofit.create(GithubService)
  }

  def cleanup() {
    server.shutdown()
  }

  def "updateCheck should handle successful GitHub status creation with JSON response body"() {
    given: "GitHub API returns a successful status creation response"
    def responseJson = '''
      {
        "id": 1,
        "state": "success",
        "description": "Build passed",
        "target_url": "http://spinnaker.io/build/123",
        "context": "continuous-integration/spinnaker"
      }
    '''
    server.enqueue(new MockResponse()
      .setResponseCode(201)
      .setHeader("Content-Type", "application/json")
      .setBody(responseJson))

    def status = new GithubStatus("success", "http://spinnaker.io/build/123", "Build passed", "ci/spinnaker")

    when: "calling updateCheck"
    def response = Retrofit2SyncCall.execute(githubService.updateCheck("token abc123", "org/repo", "abc123def", status))

    then: "response is successful and body can be read"
    response != null
    def bodyString = response.string()
    bodyString.contains("continuous-integration/spinnaker")
  }

  def "updateCheck should handle GitHub API error response without deserialization exception"() {
    given: "GitHub API returns an error"
    def errorJson = '''
      {
        "message": "Not Found",
        "documentation_url": "https://docs.github.com/rest"
      }
    '''
    server.enqueue(new MockResponse()
      .setResponseCode(404)
      .setHeader("Content-Type", "application/json")
      .setBody(errorJson))

    def status = new GithubStatus("success", "http://spinnaker.io/build/123", "Build passed", "ci/spinnaker")

    when: "calling updateCheck"
    def response = Retrofit2SyncCall.executeCall(githubService.updateCheck("token abc123", "org/repo", "nonexistent", status))

    then: "response indicates failure without throwing deserialization exception"
    response != null
    !response.isSuccessful()
    response.code() == 404
  }

  def "updateCheck should handle empty response body"() {
    given: "GitHub API returns empty response"
    server.enqueue(new MockResponse()
      .setResponseCode(201)
      .setHeader("Content-Type", "application/json"))

    def status = new GithubStatus("pending", "http://spinnaker.io/build/456", "Build in progress", "ci/spinnaker")

    when: "calling updateCheck"
    def response = Retrofit2SyncCall.executeCall(githubService.updateCheck("token abc123", "org/repo", "def456", status))

    then: "response is successful without throwing deserialization exception"
    response != null
    response.isSuccessful()
    response.code() == 201
  }

  def "updateCheck should not attempt to deserialize response into retrofit2.Response type"() {
    given: "GitHub API returns a complex JSON response"
    def responseJson = '''
      {
        "id": 123456789,
        "node_id": "MDEyOlN0YXR1c0NvbnRleHQxMjM0NTY3ODk=",
        "state": "success",
        "description": "Pipeline completed successfully",
        "target_url": "http://spinnaker.io/#/applications/myapp/executions/details/exec-123",
        "context": "pipeline/my-pipeline",
        "created_at": "2026-06-11T10:00:00Z",
        "updated_at": "2026-06-11T10:05:00Z",
        "creator": {
          "login": "spinnaker-bot",
          "id": 12345
        }
      }
    '''
    server.enqueue(new MockResponse()
      .setResponseCode(201)
      .setHeader("Content-Type", "application/json")
      .setBody(responseJson))

    def status = new GithubStatus("success", "http://spinnaker.io/exec", "Pipeline completed", "pipeline/my-pipeline")

    when: "calling updateCheck with executeCall"
    def response = Retrofit2SyncCall.executeCall(githubService.updateCheck("token xyz789", "myorg/myrepo", "commit-sha", status))

    then: "response body is ResponseBody, not retrofit2.Response"
    response != null
    response.isSuccessful()
    response.body() != null
    def bodyString = response.body().string()
    bodyString.contains("pipeline/my-pipeline")
    bodyString.contains("MDEyOlN0YXR1c0NvbnRleHQxMjM0NTY3ODk=")
  }

  def "getCommit should successfully deserialize GitHub commit response"() {
    given: "GitHub API returns a commit"
    def commitJson = '''
      {
        "sha": "abc123",
        "commit": {
          "message": "fix: correct return type",
          "author": {
            "name": "John Doe",
            "email": "john@example.com"
          }
        }
      }
    '''
    server.enqueue(new MockResponse()
      .setResponseCode(200)
      .setHeader("Content-Type", "application/json")
      .setBody(commitJson))

    when: "calling getCommit"
    def commit = Retrofit2SyncCall.execute(githubService.getCommit("token abc123", "org/repo", "abc123"))

    then: "commit is properly deserialized"
    commit != null
    commit.commit != null
    commit.commit.message == "fix: correct return type"
  }

  def "getCommit should handle merge commit message for branch commit detection"() {
    given: "GitHub API returns a merge commit"
    def commitJson = '''
      {
        "sha": "merge123",
        "commit": {
          "message": "Merge 4505f046514add513f7de9eaba3883d538673297 into aede6867d774af7ea5cbf962f2876f25df141e73"
        }
      }
    '''
    server.enqueue(new MockResponse()
      .setResponseCode(200)
      .setHeader("Content-Type", "application/json")
      .setBody(commitJson))

    when: "calling getCommit"
    def commit = Retrofit2SyncCall.execute(githubService.getCommit("token abc123", "org/repo", "merge123"))

    then: "merge commit message is properly deserialized"
    commit != null
    commit.commit != null
    commit.commit.message.startsWith("Merge 4505f046514add513f7de9eaba3883d538673297")
  }
}
