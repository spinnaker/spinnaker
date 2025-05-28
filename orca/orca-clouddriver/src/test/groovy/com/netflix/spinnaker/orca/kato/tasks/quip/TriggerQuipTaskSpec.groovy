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

package com.netflix.spinnaker.orca.kato.tasks.quip

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.mock.Calls

import java.nio.charset.Charset
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.InstanceService
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline

class TriggerQuipTaskSpec extends Specification {

  @Subject task = Spy(TriggerQuipTask)
  InstanceService instanceService = Mock(InstanceService)

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  def setup() {
    task.objectMapper = mapper
  }

  @Shared
  String app = 'foo'

  @Shared
  PipelineExecutionImpl pipe = pipeline {
    application = app
  }

  @Shared
  def versionStage = new StageExecutionImpl(pipe, "quickPatch", ["version": "1.2"]).with {
    pipe.stages << it
    return it
  }

  @Shared
  ResponseBody instanceResponse = mkResponseBody([ref: "/tasks/93fa4"])

  private ResponseBody mkResponseBody(Object body) {
    ResponseBody.create(MediaType.parse("application/json"),mapper.writeValueAsString(body).getBytes(Charset.forName("UTF8")))
  }

  @Unroll
  def "successfully trigger quip on #instances.size() instance(s)"() {
    given:
    def stage = new StageExecutionImpl(pipe, 'triggerQuip', [
      "clusterName" : cluster,
      "account"     : account,
      "region"      : region,
      "application" : app,
      "baseOs"      : "ubuntu",
      "package"     : app,
      "skipUpToDate": false,
      "instances"   : instances
    ])
    stage.parentStageId = versionStage.id

    instances.size() * task.createInstanceService(_) >> instanceService
//    def iterator = response.collect { return Calls.response(it) }.iterator()


    when:
    TaskResult result = task.execute(stage)

    then:
    instances.size() * instanceService.patchInstance(app, "1.2", "") >>> response.collect { Calls.response(it) }


    result.context.taskIds == dnsTaskMap
    result.context.instanceIds.sort() == instances.keySet().sort()
    result.context.skippedInstances == [:]
    result.context.remainingInstances == [:]
    result.status == ExecutionStatus.SUCCEEDED

    where:
    cluster = 'foo-test'
    account = 'test'
    region = "us-east-1"

    instances | response | dnsTaskMap
    ["i-1234": ["hostName": "foo.com"]] | [mkResponseBody([ref: "/tasks/93fa4"])] | ["foo.com": "93fa4"]
    ["i-1234": ["hostName": "foo.com"], "i-2345": ["hostName": "foo2.com"]] | [mkResponseBody([ref: "/tasks/93fa4"]), mkResponseBody([ref: "/tasks/abcd"])] | ["foo.com": "93fa4", "foo2.com": "abcd"]
    ["i-1234": ["hostName": "foo.com"], "i-2345": ["hostName": "foo2.com"], "i-3456": ["hostName": "foo3.com"]] | [mkResponseBody([ref: "/tasks/93fa4"]), mkResponseBody([ref: "/tasks/abcd"]), mkResponseBody([ref: "/tasks/efghi"])] | ["foo.com": "93fa4", "foo2.com": "abcd", "foo3.com": "efghi"]
  }

  def "checks versions and skips up to date instances in skipUpToDate mode"() {
    given:
    def stage = new StageExecutionImpl(pipe, 'triggerQuip', [
      "clusterName" : cluster,
      "account"     : account,
      "region"      : region,
      "application" : app,
      "baseOs"      : "ubuntu",
      "package"     : app,
      "version"     : "1.2",
      "skipUpToDate": true,
      "instances"   : instances
    ])
    stage.parentStageId = versionStage.id

    2 * task.createInstanceService(_) >> instanceService

    when:
    TaskResult result = task.execute(stage)

    then:
    2 * instanceService.getCurrentVersion(app) >>>  [
        Calls.response(currentVersions[0]),
        Calls.response(currentVersions[1])
    ]
    1 * instanceService.patchInstance(app, "1.2", "") >> Calls.response(patchResponse)

    result.context.instances == patchInstances
    result.context.skippedInstances == skipInstances
    result.context.instanceIds.sort() == ['i-1', 'i-2'].sort()
    result.context.remainingInstances == [:]

    where:
    cluster = 'foo-test'
    account = 'test'
    region = "us-east-1"

    patchInstances = ['i-1': [hostName: 'foo1.com']]
    skipInstances = ['i-2': [hostName: 'foo2.com']]
    instances = patchInstances + skipInstances
    currentVersions = [mkResponseBody([version: "1.1"]), mkResponseBody([version: "1.2"])]
    patchResponse = mkResponseBody(["ref": "/tasks/12345"])
  }

  @Unroll
  def "servers return errors, expect RUNNING"() {
    def stage = new StageExecutionImpl(pipe, 'triggerQuip', [
      "clusterName" : cluster,
      "account"     : account,
      "region"      : region,
      "application" : app,
      "baseOs"      : "ubuntu",
      "package"     : app,
      "version"     : "1.2",
      "skipUpToDate": false
    ])
    stage.parentStageId = versionStage.id

    stage.context.instances = instances
    instances.size() * task.createInstanceService(_) >> instanceService

    when:
    TaskResult result = task.execute(stage)

    then:
    throwException.each {
      // need to do this since I can't stick exceptions on the data table
      if (it) {
        1 * instanceService.patchInstance(app, patchVersion, "") >> {
          throw new SpinnakerServerException(new RuntimeException(""), new Request.Builder().url("http://some-url").build())
        }
      } else {
        1 * instanceService.patchInstance(app, patchVersion, "") >> Calls.response(instanceResponse)
      }
    }

    result.status == ExecutionStatus.RUNNING

    where:
    cluster = 'foo-test'
    account = 'test'
    region = "us-east-1"
    patchVersion = "1.2"
    instances | throwException
    ["i-1234": ["hostName": "foo.com"]] | [true]
    ["i-1234": ["hostName": "foo.com"], "i-2345": ["hostName": "foo2.com"]] | [false, true]
    ["i-1234": ["hostName": "foo.com"], "i-2345": ["hostName": "foo2.com"]] | [true, true]
  }

  @Unroll
  def 'missing configuration data'() {
    given:
    def stage = new StageExecutionImpl(pipe, 'triggerQuip', [
      "clusterName": cluster,
      "account"    : account,
      "region"     : region,
      "application": app,
      "baseOs"     : "ubuntu",
      "package"    : packageName
    ])

    stage.context.instances = instances
    stage.context.patchVersion = patchVersion

    when:
    task.execute(stage)

    then:
    0 * task.createInstanceService(_)
    0 * instanceService.patchInstance(app, patchVersion, "")
    thrown(RuntimeException)

    where:
    cluster = 'foo-test'
    account = 'test'
    region = "us-east-1"
    packageName | patchVersion | instances
    null | "1.2" | ["i-1234": ["hostName": "foo.com"]]
    "bar" | null | ["i-1234": ["hostName": "foo.com"]]
    "bar" | "1.2" | null
  }

  def "skipUpToDate with getVersion retries"() {
    def stage = new StageExecutionImpl(pipe, 'triggerQuip', [
      "clusterName" : cluster,
      "account"     : account,
      "region"      : region,
      "application" : app,
      "baseOs"      : "ubuntu",
      "package"     : app,
      "skipUpToDate": true
    ])
    stage.parentStageId = versionStage.id

    stage.context.instances = instances
    task.instanceVersionSleep = 1
    task.createInstanceService(_) >> instanceService

    when:
    TaskResult result = task.execute(stage)

    then:
    2 * instanceService.getCurrentVersion(app) >> {
      throw new SpinnakerNetworkException(new IOException("failed"), new Request.Builder().url("http://foo").build())
    } >> { return Calls.response(mkResponseBody([version: patchVersion])) }

    result.context.skippedInstances.keySet() == ["i-1234"] as Set

    where:
    cluster = 'foo-test'
    account = 'test'
    region = "us-east-1"
    patchVersion = "1.2"
    instances = ["i-1234": ["hostName": "foo.com"]]
  }
}
