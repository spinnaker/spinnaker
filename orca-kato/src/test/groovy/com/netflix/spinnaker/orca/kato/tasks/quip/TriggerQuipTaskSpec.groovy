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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.oort.InstanceService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.util.OperatingSystem
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class TriggerQuipTaskSpec extends Specification {

  @Subject task = new TriggerQuipTask()
  InstanceService instanceService = Mock(InstanceService)

  def setup() {
    task.instanceService = instanceService
    task.testing = true
    task.objectMapper = new ObjectMapper()
  }

  @Shared
  def patchResponse = '{ \"ref\": \"/tasks/93fa4\"}'
  @Shared
  def patchResponse2 = '{ \"ref\": \"/tasks/abcd\"}'
  @Shared
  def patchResponse3 = '{ \"ref\": \"/tasks/efghi\"}'
  @Shared
  Response badInstanceResponse = new Response('http://foo.com', 500, 'WTF', [], null)
  @Shared
  Response instanceResponse = new Response('http://foo.com', 200, 'OK', [], new TypedString(patchResponse))
  @Shared
  Response instanceResponse2 = new Response('http://foo2.com', 200, 'OK', [], new TypedString(patchResponse2))
  @Shared
  Response instanceResponse3 = new Response('http://foo3.com', 200, 'OK', [], new TypedString(patchResponse3))

  @Unroll
  def "successfully trigger quip on #instances.size() instance(s)"() {
    given:
    def pipe = new Pipeline.Builder()
      .withApplication(app)
      .build()
    def stage = new PipelineStage(pipe, 'triggerQuip', [
      "clusterName" : cluster,
      "account" : account,
      "region" : region,
      "application" : app,
      "baseOs" : OperatingSystem.ubuntu.toString(),
      "packageName" : app
    ])

    stage.context.instances = instances
    stage.context.patchVersion = "1.2"

    when:
    def result = task.execute(stage)

    then:
    instances.size() * instanceService.patchInstance(app, "1.2") >>> response
    stage.context.taskIds == dnsTaskMap
    result.status == ExecutionStatus.SUCCEEDED

    where:
    app = 'foo'
    cluster = 'foo-test'
    account = 'test'
    region = "us-east-1"

    instances | response | dnsTaskMap
    [["publicDnsName": "foo.com"]] | [instanceResponse] | ["foo.com" : "93fa4"]
    [["publicDnsName": "foo.com"], ["publicDnsName": "foo2.com"]] | [instanceResponse,instanceResponse2]  | ["foo.com" : "93fa4", "foo2.com" : "abcd"]
    [["publicDnsName": "foo.com"], ["publicDnsName": "foo2.com"], ["publicDnsName": "foo3.com"]] | [instanceResponse,instanceResponse2,instanceResponse3]  | ["foo.com" : "93fa4", "foo2.com" : "abcd", "foo3.com" : "efghi"]
  }

  @Unroll
  def 'retries'() {
    given:
    def pipe = new Pipeline.Builder()
      .withApplication(app)
      .build()
    def stage = new PipelineStage(pipe, 'triggerQuip', [
      "clusterName" : cluster,
      "account" : account,
      "region" : region,
      "application" : app,
      "baseOs" : OperatingSystem.ubuntu.toString(),
      "packageName" : app
    ])

    stage.context.instances = instances
    stage.context.patchVersion = "1.2"

    when:
    def result = task.execute(stage)

    then:
    response.size() * instanceService.patchInstance(app, "1.2") >>> response
    stage.context.taskIds == dnsTaskMap
    result.status == expectedResult

    where:
    app = 'foo'
    cluster = 'foo-test'
    account = 'test'
    region = "us-east-1"
    instances | response | dnsTaskMap | expectedResult
    [["publicDnsName": "foo.com"]] | [badInstanceResponse]*6 | [:] | ExecutionStatus.FAILED
    [["publicDnsName": "foo.com"], ["publicDnsName": "foo2.com"]] | [badInstanceResponse]*12 | [:] | ExecutionStatus.FAILED
    [["publicDnsName": "foo.com"], ["publicDnsName": "foo2.com"]] | (([badInstanceResponse]*5) << instanceResponse) << instanceResponse | [ "foo.com" : "93fa4", "foo2.com" :"93fa4"] | ExecutionStatus.SUCCEEDED
    [["publicDnsName": "foo.com"], ["publicDnsName": "foo2.com"]] | ([badInstanceResponse]*6) << instanceResponse | [ "foo2.com" :"93fa4"] | ExecutionStatus.FAILED

    [["publicDnsName": "foo.com"]] | [badInstanceResponse, instanceResponse ] | ["foo.com" : "93fa4"] | ExecutionStatus.SUCCEEDED
    [["publicDnsName": "foo.com"]] | [badInstanceResponse, badInstanceResponse, badInstanceResponse, instanceResponse ] | ["foo.com" : "93fa4"]| ExecutionStatus.SUCCEEDED
    [["publicDnsName": "foo.com"]] | [badInstanceResponse, badInstanceResponse, badInstanceResponse, badInstanceResponse, badInstanceResponse, instanceResponse ] | ["foo.com" : "93fa4"]| ExecutionStatus.SUCCEEDED

    [["publicDnsName": "foo.com"], ["publicDnsName": "foo2.com"]] | [badInstanceResponse, instanceResponse, instanceResponse2 ] | ["foo.com" : "93fa4", "foo2.com" : "abcd"]| ExecutionStatus.SUCCEEDED
    [["publicDnsName": "foo.com"], ["publicDnsName": "foo2.com"]] | [badInstanceResponse, instanceResponse, badInstanceResponse, instanceResponse2 ] | ["foo.com" : "93fa4", "foo2.com" : "abcd"]| ExecutionStatus.SUCCEEDED
    [["publicDnsName": "foo.com"], ["publicDnsName": "foo2.com"]] | ((([badInstanceResponse]*5) << instanceResponse) << badInstanceResponse) << instanceResponse2 | ["foo.com" : "93fa4", "foo2.com" : "abcd"]| ExecutionStatus.SUCCEEDED
  }

  @Unroll
  def 'missing configuration data'() {
    given:
    def pipe = new Pipeline.Builder()
      .withApplication(app)
      .build()
    def stage = new PipelineStage(pipe, 'triggerQuip', [
      "clusterName" : cluster,
      "account" : account,
      "region" : region,
      "application" : app,
      "baseOs" : OperatingSystem.ubuntu.toString(),
      "packageName" : packageName
    ])

    stage.context.instances = instances
    stage.context.patchVersion = patchVersion

    when:
    def result = task.execute(stage)

    then:
    0 * instanceService.patchInstance(app, patchVersion)
    stage.context.taskIds == [:]
    result.status ==  ExecutionStatus.FAILED

    where:
    cluster = 'foo-test'
    account = 'test'
    region = "us-east-1"
    app = "foo"
    packageName | patchVersion | instances
    null | "1.2" | [["publicDnsName": "foo.com"]]
    "bar" | null | [["publicDnsName": "foo.com"]]
    "bar" | "1.2" | null
  }
}
