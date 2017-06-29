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

package com.netflix.spinnaker.orca.igor.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.front50.model.Front50Credential
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
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

  BuildService buildService = Mock(BuildService)
  OortService oortService = Mock(OortService)
  Front50Service front50Service = Mock(Front50Service)

  @Shared
  Pipeline pipeline = new Pipeline()

  ObjectMapper getObjectMapper() {
    return new ObjectMapper()
  }

  def "buildService should be optional"() {
    given:
    task.buildService = null

    when:
    def result = task.execute(new Stage<>(new Pipeline(), ""))

    then:
    0 * _
    result.status == ExecutionStatus.SUCCEEDED
  }

  def "global credential is preferred to the stage account for application lookup"() {
    given:
    def stage = new Stage<>(pipeline, "stash", [application: app, account: account])//, "kato.tasks" : katoMap])
    task.buildService = buildService
    task.front50Service = front50Service

    when:
    task.execute(stage)

    then:

    1 * front50Service.getCredentials() >> credentials
    1 * front50Service.get(app) >> new Application(repoSlug: null, repoProjectKey: null)
    0 * _

    where:
    credentials                                           | account | expectedAccount
    []                                                    | 'test'  | 'test'
    [new Front50Credential(global: true, name: 'global')] | 'test'  | 'global'
    [new Front50Credential(global: false, name: 'prod')]  | 'test'  | 'test'

    app = "myapp"
  }

  @Unroll
  def "get commits from a deploy stage"() {
    given:
    String katoTasks = "[{\"resultObjects\": [" +
      "{\"ancestorServerGroupNameByRegion\": { \"${region}\":\"${serverGroup}\"}}," +
      "{\"messages\" : [ ], \"serverGroupNameByRegion\": {\"${region}\": \"${targetServerGroup}\"},\"serverGroupNames\": [\"${region}:${targetServerGroup}\"]}],\"status\": {\"completed\": true,\"failed\": false}}]"
    def katoMap = getObjectMapper().readValue(katoTasks, List)
    def contextMap = [application: app, account: account,
                      source     : [asgName: serverGroup, region: region, account: account], "deploy.server.groups": ["us-west-1": [targetServerGroup]], deploymentDetails: [[imageId: "ami-foo", ami: "amiFooName", region: "us-east-1"], [imageId: targetImage, ami: targetImageName, region: region]], "kato.tasks" : katoMap]
    def stage = setupGetCommits(contextMap, account, app, sourceImage, targetImage, region, cluster, serverGroup)

    when:
    def result = task.execute(stage)

    then:
    assertResults(result, ExecutionStatus.SUCCEEDED)

    where:
    app = "myapp"
    account = "test"
    region = "us-west-1"
    sourceImage = "ami-source"
    targetImage = "ami-target"
    targetImageName = "amiTargetName"
    jobState = 'SUCCESS'

    cluster | serverGroup | targetServerGroup
    "myapp" | "myapp" | "myapp-v000"
    "myapp" | "myapp-v001" | "myapp-v002"
    "myapp-stack" | "myapp-stack-v002" | "myapp-stack-v003"
    "myapp-stack-detail" | "myapp-stack-detail-v002" | "myapp-stack-detail-v003"
  }

  def "get commits from a copy last asg stage"() {
    given:
    String katoTasks = "[{\"resultObjects\": [" +
      "{\"ancestorServerGroupNameByRegion\": { \"${region}\":\"${serverGroup}\"}}," +
      "{\"messages\" : [ ], \"serverGroupNameByRegion\": {\"${region}\": \"${targetServerGroup}\"},\"serverGroupNames\": [\"${region}:${targetServerGroup}\"]}],\"status\": {\"completed\": true,\"failed\": false}}]"
    def katoMap = getObjectMapper().readValue(katoTasks, List)
    def contextMap = [application: app, account: account,
                      source     : [asgName: serverGroup, region: region, account: account], imageId: targetImage, amiName : targetImageName, "kato.tasks" : katoMap]
    def stage = setupGetCommits(contextMap, account, app, sourceImage, targetImageName, region, cluster, serverGroup)

    when:
    def result = task.execute(stage)

    then:
    assertResults(result, ExecutionStatus.SUCCEEDED)

    where:
    app = "myapp"
    account = "test"
    region = "us-west-1"
    sourceImage = "ami-source"
    targetImage = "ami-target"
    targetImageName = "amiTargetName"
    cluster = "app-cluster"
    serverGroup = "app-cluster-v001"
    targetServerGroup = "app-cluster-v002"
    jobState = 'SUCCESS'
  }

  def "get commits from a single region canary stage"() {
    given:
    def contextMap = [application: app, account: account,
                      source     : [asgName: serverGroup, region: region, account: account],
                      clusterPairs :
                          [[ baseline : [amiName: sourceImage, availabilityZones: [(region) : ["${region}-1c"]]], canary : [imageId: targetImage, amiName: targetImageName, availabilityZones: [(region) : ["${region}-1c"]]]]]
                      ]
    def stage = setupGetCommits(contextMap, account, app, sourceImage, targetImageName, region, cluster, serverGroup, 0)

    when:
    def result = task.execute(stage)

    then:
    assertResults(result, ExecutionStatus.SUCCEEDED)

    where:
    app = "myapp"
    account = "test"
    region = "us-west-1"
    sourceImage = "ami-source"
    targetImage = "ami-target"
    targetImageName = "amiTargetName"
    cluster = "app-cluster"
    serverGroup = "app-cluster-v001"
    targetServerGroup = "app-cluster-v002"
    jobState = 'SUCCESS'
  }

  def "get commits from a multi-region canary stage"() {
    given:
    def contextMap = [application: app, account: account,
                      source     : [asgName: serverGroup, region: region, account: account],
                      clusterPairs : [
                        [baseline : [amiName: "ami-fake", availabilityZones: ["us-east-1" : ["us-east-1-1c"]]], canary : [amiName: "ami-fake2",  availabilityZones: ["us-east-1" : ["us-east-1-1c"]]]],
                        [baseline : [amiName: sourceImage, availabilityZones: [(region) : ["${region}-1c"]]], canary : [amiName: targetImage, availabilityZones: [(region) : ["${region}-1c"]]]],
                        [baseline : [amiName: "ami-fake3", availabilityZones: ["eu-west-1" : ["eu-west-1-1c"]]], canary : [amiName: "ami-fake4", availabilityZones: ["eu-west-1" : ["eu-west-1-1c"]]]]
      ]
    ]
    def stage = setupGetCommits(contextMap, account, app, sourceImage, targetImage, region, cluster, serverGroup, 0)

    when:
    def result = task.execute(stage)

    then:
    assertResults(result, ExecutionStatus.SUCCEEDED)

    where:
    app = "myapp"
    account = "test"
    region = "us-west-1"
    sourceImage = "ami-source"
    targetImage = "ami-target"
    targetImageName = "amiTargetName"
    cluster = "app-cluster"
    serverGroup = "app-cluster-v001"
    targetServerGroup = "app-cluster-v002"
    jobState = 'SUCCESS'
  }

  Stage<Pipeline> setupGetCommits(Map contextMap, String account, String app, String sourceImage, String targetImage, String region, String cluster, String serverGroup, int serverGroupCalls = 1, int oortCalls = 1) {
    def stage = new Stage<>(pipeline, "stash", contextMap)

    task.buildService = Stub(BuildService) {
      compareCommits("stash", "projectKey", "repositorySlug", ['to':'186605b', 'from':'a86305d', 'limit':100]) >> [[message: "my commit", displayId: "abcdab", id: "abcdabcdabcdabcd", authorDisplayName: "Joe Coder", timestamp: 1432081865000, commitUrl: "http://stash.com/abcdabcdabcdabcd"],
                                                                                                                   [message: "bug fix", displayId: "efghefgh", id: "efghefghefghefghefgh", authorDisplayName: "Jane Coder", timestamp: 1432081256000, commitUrl: "http://stash.com/efghefghefghefghefgh"]]
    }

    task.front50Service = front50Service
    1 * front50Service.get(app) >> new Application(repoSlug: "repositorySlug", repoProjectKey: "projectKey", repoType: "stash")

    task.objectMapper = getObjectMapper()
    def oortResponse = "{\"launchConfig\" : {\"imageId\" : \"${sourceImage}\"}}".stripIndent()
    Response response = new Response('http://oort', 200, 'OK', [], new TypedString(oortResponse))
    task.oortService = oortService
    serverGroupCalls * oortService.getServerGroup(app, account, cluster, serverGroup, region, "aws") >> response
    List<Map> sourceResponse = [[ "tags": [ "appversion" : "myapp-1.143-h216.186605b/MYAPP-package-myapp/216" ]]]
    List<Map> targetResponse = [[ "tags": [ "appversion" : "myapp-1.144-h217.a86305d/MYAPP-package-myapp/217" ]]]
    oortCalls * oortService.getByAmiId("aws", account, region, sourceImage) >> sourceResponse
    oortCalls * oortService.getByAmiId("aws", account, region, targetImage) >> targetResponse
    return stage
  }

  boolean assertResults(def result, def taskStatus) {
    assert result.status == taskStatus
    assert result.outputs.commits.size == 2
    assert result.outputs.commits[0].displayId == "abcdab"
    assert result.outputs.commits[0].id == "abcdabcdabcdabcd"
    assert result.outputs.commits[0].authorDisplayName == "Joe Coder"
    assert result.outputs.commits[0].timestamp == 1432081865000
    assert result.outputs.commits[0].commitUrl == "http://stash.com/abcdabcdabcdabcd"
    assert result.outputs.commits[0].message == "my commit"

    assert result.outputs.commits[1].displayId == "efghefgh"
    assert result.outputs.commits[1].id == "efghefghefghefghefgh"
    assert result.outputs.commits[1].authorDisplayName == "Jane Coder"
    assert result.outputs.commits[1].timestamp == 1432081256000
    assert result.outputs.commits[1].commitUrl == "http://stash.com/efghefghefghefghefgh"
    assert result.outputs.commits[1].message == "bug fix"
    return true
  }

  def "returns running where there is an error talking to igor"() {
    given:
    String katoTasks = "[{\"resultObjects\": [" +
      "{\"ancestorServerGroupNameByRegion\": { \"${region}\":\"${serverGroup}\"}}," +
      "{\"messages\" : [ ], \"serverGroupNameByRegion\": {\"${region}\": \"${targetServerGroup}\"},\"serverGroupNames\": [\"${region}:${targetServerGroup}\"]}],\"status\": {\"completed\": true,\"failed\": false}}]"
    def katoMap = getObjectMapper().readValue(katoTasks, List)
    def stage = new Stage<>(pipeline, "stash", [application: app, account: account,
                                                      source     : [asgName: serverGroup, region: region, account: account], "deploy.server.groups": ["us-west-1": [targetServerGroup]], deploymentDetails: [[ami: "ami-foo", region: "us-east-1"], [imageId: targetImage, ami: targetImageName, region: region]], "kato.tasks": katoMap])

    and:
    task.front50Service = front50Service
    1 * front50Service.get(app) >> new Application(repoSlug: "repositorySlug", repoProjectKey: "projectKey", repoType: "stash")

    and:
    task.objectMapper = getObjectMapper()
    def oortResponse = "{\"launchConfig\" : {\"imageId\" : \"${sourceImage}\"}}".stripIndent()
    Response response = new Response('http://oort', 200, 'OK', [], new TypedString(oortResponse))
    List<Map> sourceResponse = [[ "tags" : [ "appversion" : "myapp-1.143-h216.186605b/MYAPP-package-myapp/216" ]]]
    List<Map> targetResponse = [[ "tags" : [ "appversion" : "myapp-1.144-h217.a86305d/MYAPP-package-myapp/217" ]]]

    task.oortService = oortService
    1 * oortService.getServerGroup(app, account, cluster, serverGroup, region, "aws") >> response
    1 * oortService.getByAmiId("aws", account, region, sourceImage) >> sourceResponse
    1 * oortService.getByAmiId("aws", account, region, targetImage) >> targetResponse

    and:
    task.buildService = buildService

    when:
    def result = task.execute(stage)

    then:
    1 * buildService.compareCommits("stash", "projectKey", "repositorySlug", ['to':'186605b', 'from':'a86305d', 'limit':100]) >> {
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
    targetImageName = "amiTargetName"
    jobState = 'SUCCESS'
    taskStatus = ExecutionStatus.RUNNING

    cluster | serverGroup | targetServerGroup
    "myapp" | "myapp" | "myapp-v000"
  }

  @Unroll
  def "returns success if there are no repo details provided"() {
    given:
    def stage = new Stage<>(pipeline, "stash", [application: app, account: account, source: [asgName: serverGroup, region: region, account: account], "deploy.server.groups": ["us-west-1": [targetServerGroup]], deploymentDetails: [[ami: "ami-foo", region: "us-east-1"], [imageId: targetImage, ami: targetImageName, region: region]]])

    and:
    task.buildService = buildService
    task.front50Service = front50Service
    1 * front50Service.getCredentials() >> []
    1 * front50Service.get(app) >> new Application()

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
    targetImageName = "amiTargetName"
  }

  def "returns success if commit info is missing"() {
    given:
    String katoTasks = "[{\"resultObjects\": [" +
      "{\"ancestorServerGroupNameByRegion\": { \"${region}\":\"${serverGroup}\"}}," +
      "{\"messages\" : [ ], \"serverGroupNameByRegion\": {\"${region}\": \"${targetServerGroup}\"},\"serverGroupNames\": [\"${region}:${targetServerGroup}\"]}],\"status\": {\"completed\": true,\"failed\": false}}]"
    def katoMap = getObjectMapper().readValue(katoTasks, List)
    def stage = new Stage<>(pipeline, "stash", [application: app, account: account,
                                                      source     : [asgName: serverGroup, region: region, account: account], "deploy.server.groups": ["us-west-1": [targetServerGroup]], deploymentDetails: [[ami: "ami-foo", region: "us-east-1"], [ami: targetImageName, imageId: targetImage, region: region]], "kato.tasks": katoMap])

    and:
    task.buildService = Stub(BuildService) {
      compareCommits("stash", "projectKey", "repositorySlug", ['to':'186605b', 'from':'a86305d', 'limit':100]) >> [[message: "my commit", displayId: "abcdab", id: "abcdabcdabcdabcd", authorDisplayName: "Joe Coder", timestamp: 1432081865000, commitUrl: "http://stash.com/abcdabcdabcdabcd"],
                                                                                                                   [message: "bug fix", displayId: "efghefgh", id: "efghefghefghefghefgh", authorDisplayName: "Jane Coder", timestamp: 1432081256000, commitUrl: "http://stash.com/efghefghefghefghefgh"]]
    }

    and:
    task.front50Service = front50Service
    1 * front50Service.get(app) >> new Application(repoSlug: "repositorySlug", repoProjectKey: "projectKey", repoType: "stash")

    and:
    task.objectMapper = getObjectMapper()
    def oortResponse = "{\"launchConfig\" : {\"imageId\" : \"${sourceImage}\"}}".stripIndent()
    Response response = new Response('http://oort', 200, 'OK', [], new TypedString(oortResponse))
    task.oortService = oortService
    1 * oortService.getServerGroup(app, account, cluster, serverGroup, region, "aws") >> response
    oortService.getByAmiId("aws", account, region, sourceImage) >> sourceTags
    oortService.getByAmiId("aws", account, region, targetImage) >> targetTags

    when:
    def result = task.execute(stage)

    then:
    result.status == taskStatus

    where:
    app = "myapp"
    account = "test"
    region = "us-west-1"
    sourceImage = "ami-source"
    targetImage = "ami-target"
    targetImageName = "amiTargetName"
    jobState = 'SUCCESS'
    taskStatus = ExecutionStatus.SUCCEEDED

    cluster | serverGroup | targetServerGroup | sourceTags | targetTags
    "myapp" | "myapp-v001" | "myapp-v002" | [[ "tags" : [ "appversion" : "myapp-1.143-h216.186605b/MYAPP-package-myapp/216" ]]] | [[ "tags" : [ ]]]
    "myapp" | "myapp-v001" | "myapp-v002" | [[ "tags" : [ ]]] | [[ "tags" : [ "appversion" : "myapp-1.143-h216.186605b/MYAPP-package-myapp/216" ]]]
  }


  def "oort service 404 results in success"() {
    given:
    String katoTasks = "[{\"resultObjects\": [" +
      "{\"ancestorServerGroupNameByRegion\": { \"${region}\":\"${serverGroup}\"}}," +
      "{\"messages\" : [ ], \"serverGroupNameByRegion\": {\"${region}\": \"${targetServerGroup}\"},\"serverGroupNames\": [\"${region}:${targetServerGroup}\"]}],\"status\": {\"completed\": true,\"failed\": false}}]"
    def katoMap = getObjectMapper().readValue(katoTasks, List)
    def stage = new Stage<>(pipeline, "stash", [application: app, account: account,
                                                      source     : [asgName: serverGroup, region: region, account: account], "deploy.server.groups": ["us-west-1": [targetServerGroup]], deploymentDetails: [[ami: "ami-foo", region: "us-east-1"], [ami: targetImageName, imageId: targetImage, region: region]], "kato.tasks": katoMap])

    and:
    task.buildService = Stub(BuildService) {
      compareCommits("stash", "projectKey", "repositorySlug", ['to':'186605b', 'from':'a86305d', 'limit':100]) >> [[message: "my commit", displayId: "abcdab", id: "abcdabcdabcdabcd", authorDisplayName: "Joe Coder", timestamp: 1432081865000, commitUrl: "http://stash.com/abcdabcdabcdabcd"],
                                                                                                                   [message: "bug fix", displayId: "efghefgh", id: "efghefghefghefghefgh", authorDisplayName: "Jane Coder", timestamp: 1432081256000, commitUrl: "http://stash.com/efghefghefghefghefgh"]]
    }

    and:
    task.front50Service = front50Service
    1 * front50Service.get(app) >> new Application(repoSlug: "repositorySlug", repoProjectKey: "projectKey", repoType: "stash")

    and:
    task.objectMapper = getObjectMapper()
    def oortResponse = "{\"launchConfig\" : {\"imageId\" : \"${sourceImage}\"}}".stripIndent()
    Response response = new Response('http://oort', 200, 'OK', [], new TypedString(oortResponse))
    List<Map> sourceResponse = [[ "tags" : [ "appversion" : "myapp-1.143-h216.186605b/MYAPP-package-myapp/216" ]]]
    List<Map> targetResponse = [[ "tags" : [ "appversion" : "myapp-1.144-h217.a86305d/MYAPP-package-myapp/217" ]]]
    task.oortService = oortService
    1 * oortService.getServerGroup(app, account, cluster, serverGroup, region, "aws") >>> response

    if(sourceThrowRetrofitError) {
      1 * oortService.getByAmiId("aws", account, region, sourceImage) >> {
        throw new RetrofitError(null, null, new Response("http://stash.com", 404, "test reason", [], null), null, null, null, null)
      }
    } else {
      1 * oortService.getByAmiId("aws", account, region, sourceImage) >> sourceResponse

    }

    if(targetThrowRetrofitError) {
      (sourceThrowRetrofitError ? 0 : 1) * oortService.getByAmiId("aws", account, region, targetImage) >> {
        throw new RetrofitError(null, null, new Response("http://stash.com", 404, "test reason", [], null), null, null, null, null)
      }
    } else {
      (sourceThrowRetrofitError ? 0 : 1) * oortService.getByAmiId("aws", account, region, targetImage) >> targetResponse

    }

    when:
    def result = task.execute(stage)

    then:
    result.status == taskStatus
    result.outputs.commits.size == 0

    where:
    app = "myapp"
    account = "test"
    region = "us-west-1"
    sourceImage = "ami-source"
    targetImage = "ami-target"
    targetImageName = "amiTargetName"
    jobState = 'SUCCESS'
    taskStatus = ExecutionStatus.SUCCEEDED

    cluster | serverGroup | targetServerGroup | sourceThrowRetrofitError | targetThrowRetrofitError
    "myapp" | "myapp" | "myapp-v000" | true | false
    "myapp" | "myapp" | "myapp-v000" | false | true
  }

  def "igor service 404 results in success"() {
    given:
    String katoTasks = "[{\"resultObjects\": [" +
      "{\"ancestorServerGroupNameByRegion\": { \"${region}\":\"${serverGroup}\"}}," +
      "{\"messages\" : [ ], \"serverGroupNameByRegion\": {\"${region}\": \"${targetServerGroup}\"},\"serverGroupNames\": [\"${region}:${targetServerGroup}\"]}],\"status\": {\"completed\": true,\"failed\": false}}]"
    def katoMap = getObjectMapper().readValue(katoTasks, List)
    def stage = new Stage<>(pipeline, "stash", [application: app, account: account,
                                                      source     : [asgName: serverGroup, region: region, account: account], "deploy.server.groups": ["us-west-1": [targetServerGroup]], deploymentDetails: [[ami: "ami-foo", region: "us-east-1"], [ami: targetImageName, imageId: targetImage, region: region]], "kato.tasks": katoMap])

    and:
    task.buildService = Stub(BuildService) {
      compareCommits("stash", "projectKey", "repositorySlug", ['to':'186605b', 'from':'a86305d', 'limit':100]) >> {
        throw new RetrofitError(null, null, new Response("http://stash.com", 404, "test reason", [], null), null, null, null, null)
      }
    }

    and:
    task.front50Service = front50Service
    1 * front50Service.get(app) >> new Application(repoSlug: "repositorySlug", repoProjectKey: "projectKey", repoType: "stash")

    and:
    task.objectMapper = getObjectMapper()
    def oortResponse = "{\"launchConfig\" : {\"imageId\" : \"${sourceImage}\"}}".stripIndent()
    Response response = new Response('http://oort', 200, 'OK', [], new TypedString(oortResponse))
    List<Map> sourceResponse = [[ "tags" : [ "appversion" : "myapp-1.143-h216.186605b/MYAPP-package-myapp/216" ]]]
    List<Map> targetResponse = [[ "tags" : [ "appversion" : "myapp-1.144-h217.a86305d/MYAPP-package-myapp/217" ]]]
    task.oortService = oortService
    1 * oortService.getServerGroup(app, account, cluster, serverGroup, region, "aws") >> response
    1 * oortService.getByAmiId("aws", account, region, sourceImage) >> sourceResponse
    1 * oortService.getByAmiId("aws", account, region, targetImage) >> targetResponse

    when:
    def result = task.execute(stage)

    then:
    result.status == taskStatus
    result.outputs.commits.size == 0

    where:
    app = "myapp"
    account = "test"
    region = "us-west-1"
    sourceImage = "ami-source"
    targetImage = "ami-target"
    targetImageName = "amiTargetName"
    jobState = 'SUCCESS'
    taskStatus = ExecutionStatus.SUCCEEDED

    cluster | serverGroup | targetServerGroup
    "myapp" | "myapp" | "myapp-v000"
  }

  def "return success if retries limit hit"() {
    given:
    def stage = new Stage<>(pipeline, "stash", [getCommitsRemainingRetries: 0])

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
  }

  def "return success if there is no ancestor asg"() {
    given:
    String katoTasks = "[{\"resultObjects\": [" +
      "{\"messages\" : [ ], \"serverGroupNameByRegion\": {\"${region}\": \"${targetServerGroup}\"},\"serverGroupNames\": [\"${region}:${targetServerGroup}\"]}],\"status\": {\"completed\": true,\"failed\": false}}]"
    def katoMap = getObjectMapper().readValue(katoTasks, List)
    def contextMap = [application: app, account: account,
                      source     : [asgName: serverGroup, region: region, account: account], "deploy.server.groups": ["us-west-1": [targetServerGroup]], deploymentDetails: [[ami: "ami-foo", region: "us-east-1"], [ami: targetImageName, imageId: targetImage ,region: region]], "kato.tasks" : katoMap]
    def stage = setupGetCommits(contextMap, account, app, sourceImage, targetImage, region, cluster, serverGroup, 0, 0)

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.outputs.commits.size == 0

    where:
    app = "myapp"
    account = "test"
    region = "us-west-1"
    sourceImage = "ami-source"
    targetImage = "ami-target"
    targetImageName = "amiTargetName"
    jobState = 'SUCCESS'

    cluster | serverGroup | targetServerGroup
    "myapp" | null | "myapp-v000"
  }

  @Unroll
  def "return proper value for target ami for a deploy stage"() {
    given:
    def context = [deploymentDetails: [[imageId: imageId, region: contextRegion]]]

    when:
    def result = task.getTargetAmi(context, region)

    then:
    result == expectedAncestorAmi

    where:
    imageId   | contextRegion | region      | expectedAncestorAmi
    null      | "us-west-1"   | "us-west-1" | null
    "ami-123" | "us-west-1"   | "us-west-1" | "ami-123"
  }

  @Unroll
  def "retrieve build number from app version"() {
    when:
    def result = task.getBuildFromAppVersion(image);

    then:
    result == expectedBuild

    where:
    image         | expectedBuild
    null          | null
    ""            | ""
    "foo"         | "foo"
    "foo/bar"     | "bar"
    "foo/bar/bat" | "bat"
  }

  @Unroll
  def "add the ancestor and target build info to the result"() {
    given:
    String katoTasks = "[{\"resultObjects\": [" +
      "{\"ancestorServerGroupNameByRegion\": { \"${region}\":\"${serverGroup}\"}}," +
      "{\"messages\" : [ ], \"serverGroupNameByRegion\": {\"${region}\": \"${targetServerGroup}\"},\"serverGroupNames\": [\"${region}:${targetServerGroup}\"]}],\"status\": {\"completed\": true,\"failed\": false}}]"
    def katoMap = getObjectMapper().readValue(katoTasks, List)
    def contextMap = [application: app,
                      account: account,
                      source: [region: region, account: account],
                      deploymentDetails: [[imageId: targetImage, ami: targetImageName, region: region]],
                      "kato.tasks": katoMap]
    def stage = setupGetCommits(contextMap, account, app, sourceImage, targetImage, region, cluster, serverGroup)

    when:
    def result = task.execute(stage)

    then:
    result?.outputs?.buildInfo?.ancestor == ancestorBuild
    result?.outputs?.buildInfo?.target == targetBuild

    where:
    app = "myapp"
    account = "test"
    cluster = "myapp"
    region = "us-west-1"
    serverGroup = "myapp"
    targetServerGroup = "myapp-v000"
    sourceImage = "ami-source"
    targetImage = "ami-target"
    targetImageName = "amiTargetName"

    ancestorBuild | targetBuild
    "216"         | "217"
  }
}
