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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheStatusService
import okhttp3.ResponseBody
import okhttp3.MediaType
import retrofit2.mock.Calls
import retrofit2.Response
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.time.Clock
import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage
import static java.net.HttpURLConnection.HTTP_ACCEPTED
import static java.net.HttpURLConnection.HTTP_OK

class ServerGroupCacheForceRefreshTaskSpec extends Specification {

  def cacheStatusService = Mock(CloudDriverCacheStatusService)
  def cacheService = Mock(CloudDriverCacheService)

  @Subject
  def task = new ServerGroupCacheForceRefreshTask(
    cacheStatusService,
    cacheService,
    new ObjectMapper(),
    new NoopRegistry()
  )
  def stage = stage()

  def deployConfig = [
    "cloudProvider"       : "aws",
    "account.name"        : "fzlem",
    "deploy.server.groups": ["us-east-1": ["kato-main-v000"]]
  ]

  def setup() {
    stage.context.putAll(deployConfig)
    stage.startTime = 0
  }

  void "should force cache refresh server groups via clouddriver"() {
    setup:
    task.clock = Mock(Clock) {
      1 * millis() >> 0
    }
    Map expectations = [:]

    when:
    def result = task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate(stage.context.cloudProvider, ServerGroupCacheForceRefreshTask.REFRESH_TYPE, _) >> {
      String cloudProvider, String type, Map<String, ? extends Object> body ->
        expectations = body
        Calls.response(Response.success(202, ResponseBody.create(MediaType.parse("application/json"),"[]")))
    }
    expectations.serverGroupName == (deployConfig."deploy.server.groups"."us-east-1").get(0)
    expectations.account == deployConfig."account.name"
    expectations.region == "us-east-1"
    result.status == RUNNING
  }

  void "should auto-succeed if force cache refresh takes longer than 12 minutes"() {
    setup:
    task.clock = Mock(Clock) {
      2 * millis() >> TimeUnit.MINUTES.toMillis(12) + 1
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
    forceCacheUpdatedServerGroups.size() * task.cacheService.forceCacheUpdate("aws", "ServerGroup", _) >> {
      return Calls.response(Response.success(executionStatus == SUCCEEDED ? 200 : 202, ResponseBody.create(MediaType.parse("application/json"),"")))
    }
    0 * task.cacheService._
    0 * task.cacheStatusService._

    optionalTaskResult.present == !forceCacheUpdatedServerGroups.isEmpty()
    forceCacheUpdatedServerGroups.every {
      optionalTaskResult.get().status == executionStatus
      (optionalTaskResult.get().context."refreshed.server.groups" as Set<Map>)*.serverGroupName == expectedRefreshedServerGroups
    }
    stageData.refreshedServerGroups*.serverGroupName == expectedRefreshedServerGroups

    where:
    deployedServerGroups | refreshedServerGroups || executionStatus || forceCacheUpdatedServerGroups || expectedRefreshedServerGroups
    ["s-v001"]           | []                    || SUCCEEDED       || ["s-v001"]                    || []
    ["s-v001"]           | []                    || RUNNING         || ["s-v001"]                    || ["s-v001"]
    ["s-v001", "s-v002"] | []                    || RUNNING         || ["s-v001", "s-v002"]          || ["s-v001", "s-v002"]
    ["s-v001", "s-v002"] | ["s-v001"]            || RUNNING         || ["s-v002"]                    || ["s-v001", "s-v002"]
    ["s-v001"]           | ["s-v001"]            || null            || []                            || ["s-v001"]
  }

  @Unroll
  void "force cache refresh should return SUCCEEDED iff all results succeeded"() {
    given:
    def stageData = new ServerGroupCacheForceRefreshTask.StageData(
      deployServerGroups: [
        "us-west-1": ["s-v001", "s-v002", "s-v003"] as Set<String>
      ],
      refreshedServerGroups: []
    )

    when:
    def optionalTaskResult = task.performForceCacheRefresh("test", "aws", stageData)

    then:
    3 * task.cacheService.forceCacheUpdate("aws", "ServerGroup", _) >>> responseCodes.collect { responseCode ->
      return Calls.response(Response.success(responseCode, ResponseBody.create(MediaType.parse("application/json"),"")))
    }
    0 * task.cacheService._
    0 * task.cacheStatusService._

    optionalTaskResult.present
    optionalTaskResult.get().status == executionStatus

    where:
    responseCodes                                   || executionStatus
    [HTTP_OK      , HTTP_OK      , HTTP_OK      ]   || SUCCEEDED
    [HTTP_ACCEPTED, HTTP_ACCEPTED, HTTP_ACCEPTED]   || RUNNING
    [HTTP_OK      , HTTP_ACCEPTED, HTTP_OK      ]   || RUNNING
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
    def processingComplete = task.processPendingForceCacheUpdates("executionId", "test", "aws", stageData, 0)

    then:
    1 * task.cacheStatusService.pendingForceCacheUpdates("aws", "ServerGroup") >> { Calls.response(pendingForceCacheUpdates) }
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
