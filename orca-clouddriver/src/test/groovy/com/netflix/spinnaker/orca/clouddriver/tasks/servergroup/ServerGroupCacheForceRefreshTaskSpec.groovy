/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import java.time.Clock
import java.util.concurrent.TimeUnit
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED

class ServerGroupCacheForceRefreshTaskSpec extends Specification {

  @Subject
  def task = new ServerGroupCacheForceRefreshTask(objectMapper: new ObjectMapper())
  def stage = new PipelineStage(type: "whatever")

  def deployConfig = [
    "cloudProvider"       : "aws",
    "account.name"        : "fzlem",
    "deploy.server.groups": ["us-east-1": ["kato-main-v000"]]
  ]

  def setup() {
    stage.context.putAll(deployConfig)
    stage.startTime = 0
    task.oort = Mock(OortService)
  }

  void "should force cache refresh server groups via oort"() {
    setup:
    task.clock = Mock(Clock) {
      1 * millis() >> 0
    }
    Map expectations = [:]

    when:
    def result = task.execute(stage)

    then:
    1 * task.oort.forceCacheUpdate(stage.context.cloudProvider, ServerGroupCacheForceRefreshTask.REFRESH_TYPE, _) >> {
      String cloudProvider, String type, Map<String, ? extends Object> body ->
        expectations = body
        return new Response('oort', 202, 'ok', [], new TypedString("[]"))
    }
    expectations.serverGroupName == (deployConfig."deploy.server.groups"."us-east-1").get(0)
    expectations.account == deployConfig."account.name"
    expectations.region == "us-east-1"
    result.status == RUNNING
  }

  void "should auto-succeed if force cache refresh takes longer than 12 minutes"() {
    setup:
    task.clock = Mock(Clock) {
      1 * millis() >> TimeUnit.MINUTES.toMillis(12) + 1
    }

    expect:
    task.execute(stage).status == SUCCEEDED
  }

  @Unroll
  void "should only force cache refresh server groups that haven't been refreshed yet"() {
    given:
    def stageData = new ServerGroupCacheForceRefreshTask.StageData(
      deployServerGroups: [
        "us-west-1": deployedServerGroups as Set<String>
      ],
      refreshedServerGroups: refreshedServerGroups.collect {
        [asgName: it, serverGroupName: it, region: "us-west-1", account: "test"]
      }
    )

    when:
    def optionalTaskResult = task.performForceCacheRefresh("test", "aws", stageData)

    then:
    forceCacheUpdatedServerGroups.size() * task.oort.forceCacheUpdate("aws", "ServerGroup", _) >> {
      return new Response('', executionStatus == SUCCEEDED ? 200 : 202, 'ok', [], new TypedString(""))
    }
    0 * task.oort._

    optionalTaskResult.present == !forceCacheUpdatedServerGroups.isEmpty()
    forceCacheUpdatedServerGroups.every {
      optionalTaskResult.get().status == executionStatus
      (optionalTaskResult.get().stageOutputs."refreshed.server.groups" as Set<Map>)*.serverGroupName == expectedRefreshedServerGroups
    }
    stageData.refreshedServerGroups*.serverGroupName == expectedRefreshedServerGroups

    where:
    deployedServerGroups | refreshedServerGroups || executionStatus || forceCacheUpdatedServerGroups || expectedRefreshedServerGroups
    ["s-v001"]           | []                    || SUCCEEDED       || ["s-v001"]                    || ["s-v001"]
    ["s-v001"]           | []                    || RUNNING         || ["s-v001"]                    || ["s-v001"]
    ["s-v001", "s-v002"] | []                    || RUNNING         || ["s-v001", "s-v002"]          || ["s-v001", "s-v002"]
    ["s-v001", "s-v002"] | ["s-v001"]            || RUNNING         || ["s-v002"]                    || ["s-v001", "s-v002"]
    ["s-v001"]           | ["s-v001"]            || null            || []                            || ["s-v001"]
  }

  @Unroll
  void "should only complete when all deployed server groups have been processed"() {
    given:
    def stageData = new ServerGroupCacheForceRefreshTask.StageData(
      deployServerGroups: [
        "us-west-1": deployedServerGroups as Set<String>
      ]
    )

    when:
    def processingComplete = task.processPendingForceCacheUpdates("test", "aws", stageData, 0,)

    then:
    1 * task.oort.pendingForceCacheUpdates("aws", "ServerGroup") >> { return pendingForceCacheUpdates }
    processingComplete == expectedProcessingComplete

    where:
    deployedServerGroups | pendingForceCacheUpdates                      || expectedProcessingComplete
    ["s-v001"]           | [pFCU("s-v001", 1, 1)]                        || true    // cacheTime && processedTime > startTime
    ["s-v001", "s-v002"] | [pFCU("s-v001", 1, 1), pFCU("s-v002", 1, 1)]  || true    // cacheTime && processedTime > startTime
    ["s-v001", "s-v002"] | [pFCU("s-v001", 1, 1), pFCU("s-v002", 1, -1)] || false   // cacheTime > startTime, processedTime < startTime
    ["s-v001"]           | [pFCU("s-v001", -1, 1)]                       || false   // cacheTime < startTime, processedTime > startTime
    ["s-v001"]           | [pFCU("s-v001", 1, -1)]                       || false   // cacheTime > startTime, processedTime < startTime
    ["s-v001"]           | []                                            || false   // no pending force cache update
  }

  @Unroll
  void "should correctly extract `zone` from `zones` in StageData"() {
    given:
    def objectMapper = new ObjectMapper()
    def json = objectMapper.writeValueAsString([
      "deploy.server.groups"   : ["us-west-1": ["s-v001"]],
      "refreshed.server.groups": [
        [serverGroup: "s-v001"]
      ],
      zones: zones,
      zone: zone
    ])

    when:
    def stageData = objectMapper.readValue(json, ServerGroupCacheForceRefreshTask.StageData)

    then:
    stageData.zone == expectedZone

    where:
    zones              | zone    || expectedZone
    ["zone1"]          | null    || "zone1"
    ["zone1", "zone2"] | "zone3" || "zone3"
    null               | null    || null
  }

  static Map pFCU(String serverGroupName, long cacheTime, long processedTime) {
    return [
      cacheTime    : cacheTime,
      processedTime: processedTime,
      details      : [serverGroup: serverGroupName, region: "us-west-1", account: "test"]
    ]
  }
}
