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

import java.nio.charset.Charset
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.InstanceService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import retrofit.client.Client
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class TriggerQuipTaskSpec extends Specification {

  @Subject task = Spy(TriggerQuipTask)
  InstanceService instanceService = Mock(InstanceService)

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  def setup() {
    task.objectMapper = mapper
    task.retrofitClient = Stub(Client)
  }

  @Shared
  String app = 'foo'

  @Shared
  Pipeline pipe = Pipeline.builder()
    .withApplication(app)
    .build()

  @Shared
  def versionStage = new Stage<>(pipe, "quickPatch", ["version": "1.2"]).with {
    pipe.stages << it
    return it
  }

  @Shared
  Response instanceResponse = mkResponse([ref: "/tasks/93fa4"])
  @Shared
  Response instanceResponse2 = mkResponse([ref: "/tasks/abcd"])
  @Shared
  Response instanceResponse3 = mkResponse([ref: "/tasks/efghi"])

  private Response mkResponse(Object body) {
    new Response('http://foo.com', 200, 'OK', [], new TypedByteArray("application/json", mapper.writeValueAsString(body).getBytes(Charset.forName("UTF8"))))
  }

  @Unroll
  def "successfully trigger quip on #instances.size() instance(s)"() {
    given:
    def stage = new Stage<>(pipe, 'triggerQuip', [
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

    when:
    TaskResult result = task.execute(stage)

    then:
    instances.size() * instanceService.patchInstance(app, "1.2", "") >>> response
    result.stageOutputs.taskIds == dnsTaskMap
    result.stageOutputs.instanceIds.sort() == instances.keySet().sort()
    result.stageOutputs.skippedInstances == [:]
    result.stageOutputs.remainingInstances == [:]
    result.status == ExecutionStatus.SUCCEEDED

    where:
    cluster = 'foo-test'
    account = 'test'
    region = "us-east-1"

    instances | response | dnsTaskMap
    ["i-1234": ["hostName": "foo.com"]] | [instanceResponse] | ["foo.com": "93fa4"]
    ["i-1234": ["hostName": "foo.com"], "i-2345": ["hostName": "foo2.com"]] | [instanceResponse, instanceResponse2] | ["foo.com": "93fa4", "foo2.com": "abcd"]
    ["i-1234": ["hostName": "foo.com"], "i-2345": ["hostName": "foo2.com"], "i-3456": ["hostName": "foo3.com"]] | [instanceResponse, instanceResponse2, instanceResponse3] | ["foo.com": "93fa4", "foo2.com": "abcd", "foo3.com": "efghi"]
  }

  def "checks versions and skips up to date instances in skipUpToDate mode"() {
    given:
    def stage = new Stage<>(pipe, 'triggerQuip', [
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
    2 * instanceService.getCurrentVersion(app) >>> currentVersions
    1 * instanceService.patchInstance(app, "1.2", "") >> patchResponse

    result.stageOutputs.instances == patchInstances
    result.stageOutputs.skippedInstances == skipInstances
    result.stageOutputs.instanceIds.sort() == ['i-1', 'i-2'].sort()
    result.stageOutputs.remainingInstances == [:]

    where:
    cluster = 'foo-test'
    account = 'test'
    region = "us-east-1"

    patchInstances = ['i-1': [hostName: 'foo1.com']]
    skipInstances = ['i-2': [hostName: 'foo2.com']]
    instances = patchInstances + skipInstances
    currentVersions = [mkResponse([version: "1.1"]), mkResponse([version: "1.2"])]
    patchResponse = mkResponse(["ref": "/tasks/12345"])
  }

  @Unroll
  def "servers return errors, expect RUNNING"() {
    def stage = new Stage<>(pipe, 'triggerQuip', [
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
          throw new RetrofitError(null, null, null, null, null, null, null)
        }
      } else {
        1 * instanceService.patchInstance(app, patchVersion, "") >> instanceResponse
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
    def stage = new Stage<>(pipe, 'triggerQuip', [
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
    def stage = new Stage<>(pipe, 'triggerQuip', [
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
      throw RetrofitError.networkError('http://foo', new IOException('failed'))
    } >> mkResponse([version: patchVersion])

    result.stageOutputs.skippedInstances.keySet() == ["i-1234"] as Set

    where:
    cluster = 'foo-test'
    account = 'test'
    region = "us-east-1"
    patchVersion = "1.2"
    instances = ["i-1234": ["hostName": "foo.com"]]
  }
}
