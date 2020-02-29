/*
 * Copyright 2018 Schibsted ASA
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

package com.netflix.spinnaker.echo.notification

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.github.GithubCommitDetail
import com.netflix.spinnaker.echo.github.GithubCommit
import com.netflix.spinnaker.echo.github.GithubService
import com.netflix.spinnaker.echo.github.GithubStatus
import com.netflix.spinnaker.echo.api.events.Event
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import spock.util.concurrent.BlockingVariable

class GithubNotificationAgentSpec extends Specification {

  def github = Mock(GithubService)
  @Subject
  def agent = new GithubNotificationAgent()

  void setup() {
    agent.spinnakerUrl = "http://spinnaker.io"
    agent.setGithubService(github)
  }


  @Unroll
  def "sets correct status check for #status status in pipeline events"() {
    given:
    def actualMessage = new BlockingVariable<GithubStatus>()
    github.updateCheck(*_) >> { token, repo, sha, status ->
      actualMessage.set(status)
    }
    github.getCommit(*_) >> { token, repo, sha ->
      new Response("url", 200, "nothing", [], new TypedByteArray("application/json", "message".bytes))
    }

    when:
    agent.sendNotifications(null, application, event, [type: type], status)

    then:
    actualMessage.get().getDescription() ==~ expectedDescription
    actualMessage.get().getTarget_url() == "http://spinnaker.io/#/applications/whatever/executions/details/1?pipeline=foo-pipeline"
    actualMessage.get().getContext() ==~ "pipeline/foo-pipeline"

    where:
    status     || expectedDescription
    "complete" || "Pipeline 'foo-pipeline' is complete"
    "starting" || "Pipeline 'foo-pipeline' is starting"
    "failed"   || "Pipeline 'foo-pipeline' is failed"

    application = "whatever"
    event = new Event(
      content: [
        execution: [
          id     : "1",
          name   : "foo-pipeline",
          trigger: [
            buildInfo: [
              name: "some-org/some-repo",
              scm : [
                [
                  branch: "master",
                  name  : "master",
                  sha1  : "asdf",
                ]
              ]
            ]
          ]
        ]
      ]
    )
    type = "pipeline"
  }

  @Unroll
  def "sets correct status check for #status status in stage events"() {
    given:
    def actualMessage = new BlockingVariable<GithubStatus>()
    github.updateCheck(*_) >> { token, repo, sha, status ->
      actualMessage.set(status)
    }
    github.getCommit(*_) >> { token, repo, sha ->
      new Response("url", 200, "nothing", [], new TypedByteArray("application/json", "message".bytes))
    }

    when:
    agent.sendNotifications(null, application, event, [type: type], status)

    then:
    actualMessage.get().getDescription() == expectedDescription
    actualMessage.get().getTarget_url() == "http://spinnaker.io/#/applications/whatever/executions/details/1?pipeline=foo-pipeline&stage=1"
    actualMessage.get().getContext() == "stage/second stage"

    where:
    status     || expectedDescription
    "complete" || "Stage 'second stage' in pipeline 'foo-pipeline' is complete"
    "starting" || "Stage 'second stage' in pipeline 'foo-pipeline' is starting"
    "failed"   || "Stage 'second stage' in pipeline 'foo-pipeline' is failed"

    application = "whatever"
    event = new Event(
      content: [
        name     : "second stage",
        execution: [
          id     : "1",
          name   : "foo-pipeline",
          trigger: [
            buildInfo: [
              name: "some-org/some-repo",
              scm : [
                [
                  branch: "master",
                  name  : "master",
                  sha1  : "asdf",
                ]
              ]
            ]
          ],
          stages : [
            [
              name: "first stage"
            ],
            [
              name: "second stage"
            ],
          ]
        ]
      ]
    )
    type = "stage"
  }

  def "if commit is a merge commit of the head branch and the default branch then return the head commit"() {
    given:
    GithubCommit commit = new GithubCommit(new GithubCommitDetail(commitMessage))
    ObjectMapper mapper = EchoObjectMapper.getInstance()
    String response = mapper.writeValueAsString(commit)
    github.getCommit(*_) >> { token, repo, sha ->
      new Response("url", 200, "nothing", [], new TypedByteArray("application/json", response.bytes))
    }

    when:
    agent.sendNotifications(null, application, event, [type: type], status)

    then:
    1 * github.updateCheck(_, _, expectedSha, _)

    where:
    commitMessage                                                                                  || expectedSha
    "Merge 4505f046514add513f7de9eaba3883d538673297 into aede6867d774af7ea5cbf962f2876f25df141e73" || "4505f046514add513f7de9eaba3883d538673297"
    "Some commit message"                                                                          || "asdf"

    application = "whatever"
    status = "starting"
    event = new Event(
      content: [
        execution: [
          id     : "1",
          name   : "foo-pipeline",
          trigger: [
            buildInfo: [
              name: "some-org/some-repo",
              scm : [
                [
                  branch: "master",
                  name  : "master",
                  sha1  : "asdf",
                ]
              ]
            ]
          ]
        ]
      ]
    )
    type = "pipeline"
  }

  def "retries if updating the github check fails"() {
    given:
    github.getCommit(*_) >> { token, repo, sha ->
      new Response("url", 200, "nothing", [], new TypedByteArray("application/json", "message".bytes))
    }

    when:
    agent.sendNotifications(null, application, event, [type: type], status)

    then:
    5 * github.updateCheck(_, _, _, _) >> RetrofitError.networkError("timeout", new IOException())

    where:
    application = "whatever"
    event = new Event(
      content: [
        execution: [
          id     : "1",
          name   : "foo-pipeline",
          trigger: [
            buildInfo: [
              name: "some-org/some-repo",
              scm : [
                [
                  branch: "master",
                  name  : "master",
                  sha1  : "asdf",
                ]
              ]
            ]
          ]
        ]
      ]
    )
    type = "pipeline"
    status = "status"
  }

}
