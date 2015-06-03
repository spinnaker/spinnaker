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

package com.netflix.spinnaker.orca.sock.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.front50.model.Front50Credential
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.sock.SockService
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class GetCommitsTaskSpec extends Specification {

  @Subject
  GetCommitsTask task = new GetCommitsTask()

  SockService sockService = Mock(SockService)
  OortService oortService = Mock(OortService)
  Front50Service front50Service = Mock(Front50Service)

  @Shared
  Pipeline pipeline = new Pipeline()

  def "sockService should be optional"() {
    given:
    task.sockService = null

    when:
    def result = task.execute(null)

    then:
    0 * _
    result.status == ExecutionStatus.SUCCEEDED
  }

  def "global credential is preferred to the stage account for application lookup"() {
    given:
    def stage = new PipelineStage(pipeline, "stash", [application: app, account: account])
    task.sockService = sockService
    task.front50Service = front50Service

    when:
    task.execute(stage)

    then:

    1 * front50Service.getCredentials() >> credentials
    1 * front50Service.get(expectedAccount, app) >> new Application(repoSlug: null, repoProjectKey: null)
    0 * _

    where:
    credentials                                           | account | expectedAccount
    []                                                    | 'test'  | 'test'
    [new Front50Credential(global: true, name: 'global')] | 'test'  | 'global'
    [new Front50Credential(global: false, name: 'prod')]  | 'test'  | 'test'

    app = "myapp"
  }

  @Unroll
  def "get commits from serverGroup source #serverGroup"() {
    given:
    def stage = new PipelineStage(pipeline, "stash", [application: app, account: account,
                                                      source     : [asgName: serverGroup, region: region, account: account], "deploy.server.groups": ["us-west-1": [targetServerGroup]], deploymentDetails: [[ami: "ami-foo", region: "us-east-1"], [ami: targetImage, region: region]]]).asImmutable()

    and:
    task.sockService = Stub(SockService) {
      compareCommits("stash", "projectKey", "repositorySlug", ['to':'186605b', 'from':'a86305d']) >> [[message: "my commit", displayId: "abcdab", id: "abcdabcdabcdabcd", authorDisplayName: "Joe Coder", timestamp: 1432081865000, commitUrl: "http://stash.com/abcdabcdabcdabcd"],
                                      [message: "bug fix", displayId: "efghefgh", id: "efghefghefghefghefgh", authorDisplayName: "Jane Coder", timestamp: 1432081256000, commitUrl: "http://stash.com/efghefghefghefghefgh"]]
    }

    and:
    task.front50Service = front50Service
    1 * front50Service.get(account, app) >> new Application(repoSlug: "repositorySlug", repoProjectKey: "projectKey", repoType: "stash")

    and:
    task.objectMapper = new ObjectMapper()
    def oortResponse = "{\"launchConfig\" : {\"imageId\" : \"${sourceImage}\"}}".stripIndent()
    Response response = new Response('http://oort', 200, 'OK', [], new TypedString(oortResponse))
    Response sourceResponse = new Response('http://oort', 200, 'OK', [], new TypedString('[{ "tags" : { "appversion" : "myapp-1.143-h216.186605b/MYAPP-package-myapp/216" }}]'))
    Response targetResponse = new Response('http://oort', 200, 'OK', [], new TypedString('[{ "tags" : { "appversion" : "myapp-1.144-h217.a86305d/MYAPP-package-myapp/217" }}]'))
    task.oortService = oortService
    1 * oortService.getServerGroup(app, account, cluster, serverGroup, region, "aws") >> response
    1 * oortService.getByAmiId("aws", account, region, sourceImage) >> sourceResponse
    1 * oortService.getByAmiId("aws", account, region, targetImage) >> targetResponse

    when:
    def result = task.execute(stage)

    then:
    result.status == taskStatus
    result.outputs.commits.size == 2
    result.outputs.commits[0].displayId == "abcdab"
    result.outputs.commits[0].id == "abcdabcdabcdabcd"
    result.outputs.commits[0].authorDisplayName == "Joe Coder"
    result.outputs.commits[0].timestamp == 1432081865000
    result.outputs.commits[0].commitUrl == "http://stash.com/abcdabcdabcdabcd"
    result.outputs.commits[0].message == "my commit"

    result.outputs.commits[1].displayId == "efghefgh"
    result.outputs.commits[1].id == "efghefghefghefghefgh"
    result.outputs.commits[1].authorDisplayName == "Jane Coder"
    result.outputs.commits[1].timestamp == 1432081256000
    result.outputs.commits[1].commitUrl == "http://stash.com/efghefghefghefghefgh"
    result.outputs.commits[1].message == "bug fix"

    where:
    app = "myapp"
    account = "test"
    region = "us-west-1"
    sourceImage = "ami-source"
    targetImage = "ami-target"
    jobState = 'SUCCESS'
    taskStatus = ExecutionStatus.SUCCEEDED

    cluster | serverGroup | targetServerGroup
    "myapp" | "myapp" | "myapp-v000"
    "myapp" | "myapp-v001" | "myapp-v002"
    "myapp-stack" | "myapp-stack-v002" | "myapp-stack-v003"
    "myapp-stack-detail" | "myapp-stack-detail-v002" | "myapp-stack-detail-v003"
  }

  def "returns running where there is an error talking to sock"() {
    given:
    def stage = new PipelineStage(pipeline, "stash", [application: app, account: account,
                                                      source     : [asgName: serverGroup, region: region, account: account], "deploy.server.groups": ["us-west-1": [targetServerGroup]], deploymentDetails: [[ami: "ami-foo", region: "us-east-1"], [ami: targetImage, region: region]]]).asImmutable()

    and:
    task.front50Service = front50Service
    1 * front50Service.get(account, app) >> new Application(repoSlug: "repositorySlug", repoProjectKey: "projectKey", repoType: "stash")

    and:
    task.objectMapper = new ObjectMapper()
    def oortResponse = "{\"launchConfig\" : {\"imageId\" : \"${sourceImage}\"}}".stripIndent()
    Response response = new Response('http://oort', 200, 'OK', [], new TypedString(oortResponse))
    Response sourceResponse = new Response('http://oort', 200, 'OK', [], new TypedString('[{ "tags" : { "appversion" : "myapp-1.143-h216.186605b/MYAPP-package-myapp/216" }}]'))
    Response targetResponse = new Response('http://oort', 200, 'OK', [], new TypedString('[{ "tags" : { "appversion" : "myapp-1.144-h217.a86305d/MYAPP-package-myapp/217" }}]'))

    task.oortService = oortService
    1 * oortService.getServerGroup(app, account, cluster, serverGroup, region, "aws") >> response
    1 * oortService.getByAmiId("aws", account, region, sourceImage) >> sourceResponse
    1 * oortService.getByAmiId("aws", account, region, targetImage) >> targetResponse

    and:
    task.sockService = sockService

    when:
    def result = task.execute(stage)

    then:
    1 * sockService.compareCommits("stash", "projectKey", "repositorySlug", ['to':'186605b', 'from':'a86305d']) >> {
      throw new RetrofitError(null, null,
        new Response("http://stash.com", 500, "test reason", [], null), null, null, null, null)
    }
    result.status == taskStatus

    where:
    app = "myapp"
    account = "test"
    region = "us-west-1"
    sourceImage = "ami-source"
    targetImage = "ami-target"
    jobState = 'SUCCESS'
    taskStatus = ExecutionStatus.RUNNING

    cluster | serverGroup | targetServerGroup
    "myapp" | "myapp" | "myapp-v000"
  }

  def "returns success if there are no repo details provided"() {
    given:
    def stage = new PipelineStage(pipeline, "stash", [application: app, account: account, source: [asgName: serverGroup, region: region, account: account], "deploy.server.groups": ["us-west-1": [targetServerGroup]], deploymentDetails: [[ami: "ami-foo", region: "us-east-1"], [ami: targetImage, region: region]]]).asImmutable()

    and:
    task.sockService = sockService
    task.front50Service = front50Service
    1 * front50Service.getCredentials() >> []
    1 * front50Service.get(account, app) >> new Application()

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED

    where:
    app = "myapp"
    account = "test"
    region = "us-west-1"
    serverGroup = "myapp-stack"
    targetServerGroup = "myapp-stack-v000"
    targetImage = "ami-target"
  }
}
