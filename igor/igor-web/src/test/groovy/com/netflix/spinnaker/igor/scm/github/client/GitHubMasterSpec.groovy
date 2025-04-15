/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.igor.scm.github.client

import com.netflix.spinnaker.igor.scm.github.client.model.GetRepositoryContentResponse
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject

class GitHubMasterSpec extends Specification {
  @Subject
  GitHubMaster gitHubMaster

  GitHubClient gitHubClient = Mock(GitHubClient)

  void setup() {
    gitHubMaster = new GitHubMaster(gitHubClient: gitHubClient, baseUrl: "")
  }

  void 'list directory'() {
    given:
    1 * gitHubClient.listDirectory(project, repo, dir, ref) >> Calls.response(gitHubClientResponse)

    when:
    List<String> response = gitHubMaster.listDirectory(project, repo, dir, ref)

    then:
    response == expectedResponse

    where:
    project = 'proj'
    repo = 'repo'
    dir = 'dir'
    ref = 'refs/heads/master'
    expectedResponse = ["test.yml"]
    gitHubClientResponse = [
      new GetRepositoryContentResponse(
        path: "test.yml",
        type: "file"
      )
    ]
  }

  void 'get file content'() {
    given:
    1 * gitHubClient.getFileContent(project, repo, dir, ref) >> Calls.response(gitHubClientResponse)

    when:
    String response = gitHubMaster.getTextFileContents(project, repo, dir, ref)

    then:
    response == expectedResponse

    where:
    project = 'proj'
    repo = 'repo'
    dir = 'dir'
    ref = 'refs/heads/master'
    expectedResponse = "test"
    gitHubClientResponse = new GetRepositoryContentResponse(
      path: "test.yml",
      type: "file",
      content: "dGVzdA==\n"
    )
  }
}
