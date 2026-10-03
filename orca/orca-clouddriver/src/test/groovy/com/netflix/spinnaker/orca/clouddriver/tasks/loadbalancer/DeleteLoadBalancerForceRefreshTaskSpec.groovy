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

package com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage
import static java.net.HttpURLConnection.HTTP_ACCEPTED
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST

class DeleteLoadBalancerForceRefreshTaskSpec extends Specification {
  @Subject task = new DeleteLoadBalancerForceRefreshTask()
  def stage = stage()

  def config = [
    cloudProvider   : 'aws',
    regions         : ["us-west-1"],
    credentials     : "fzlem",
    loadBalancerName: 'flapjack-main-frontend'
  ]

  def regionalConfig = [
    cloudProvider   : 'gce',
    loadBalancerType: 'EXTERNAL_MANAGED',
    regions         : ["us-central1"],
    credentials     : "test-account",
    loadBalancerName: 'listener-a'
  ]

  def setup() {
    stage.context.putAll(config)
    task.cacheService = Mock(CloudDriverCacheService)
    task.mapper = new ObjectMapper()
  }

  static ResponseBody pendingBody(List<String> identifiers) {
    ResponseBody.create(
      MediaType.parse("application/json"),
      "{\"cachedIdentifiersByType\":{\"loadBalancers\":${identifiers.collect { "\"${it}\"" }}}}"
    )
  }

  static SpinnakerHttpException httpException(int statusCode) {
    def response = Response.error(
      statusCode,
      ResponseBody.create(MediaType.parse("application/json"), '{"message":"force cache failed"}')
    )
    def retrofit = new Retrofit.Builder()
      .baseUrl("http://clouddriver/")
      .addConverterFactory(JacksonConverterFactory.create())
      .build()
    new SpinnakerHttpException(response, retrofit)
  }

  void useRegionalContext() {
    stage.context = new HashMap(regionalConfig)
  }

  void "should force cache refresh server groups via oort when clusterName provided"() {
    when:
    task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate(stage.context.cloudProvider, DeleteLoadBalancerForceRefreshTask.REFRESH_TYPE, _) >> {
      String cloudProvider, String type, Map<String, Object> body ->

      assert body.loadBalancerName == config.loadBalancerName
      assert body.account == config.credentials
      assert body.region == "us-west-1"
      assert body.evict == true
    }
  }

  @Unroll
  void "succeeds for #cloudProvider deletes that send no regions"() {
    given:
    stage.context = [cloudProvider: cloudProvider, credentials: "fzlem", loadBalancerName: "lb", region: "r1"]

    when:
    def result = task.execute(stage)

    then:
    0 * task.cacheService._
    result.status == ExecutionStatus.SUCCEEDED

    where:
    cloudProvider << ["azure", "appengine", "cloudrun", "dcos"]
  }

  @Unroll
  void "maps regional #responseCase force-cache response to #expectedStatus"() {
    given:
    useRegionalContext()
    def refreshCall = Mock(Call)

    when:
    def result = task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate('gce', DeleteLoadBalancerForceRefreshTask.REFRESH_TYPE, _) >> {
      String cloudProvider, String type, Map<String, Object> body ->

      assert body.loadBalancerName == regionalConfig.loadBalancerName
      assert body.account == regionalConfig.credentials
      assert body.region == "us-central1"
      assert body.evict == true
      refreshCall
    }
    1 * refreshCall.execute() >> response
    result.status == expectedStatus

    where:
    responseCase               | response                                                                                                    || expectedStatus
    "200 complete"             | Response.success(null)                                                                                      || ExecutionStatus.SUCCEEDED
    "202 empty IDs"            | Response.success(HTTP_ACCEPTED, pendingBody([]))                                                             || ExecutionStatus.RUNNING
    // Clouddriver returns IDs only for rows an agent cached, so the deleted load balancer was written back.
    "202 re-cached target"     | Response.success(HTTP_ACCEPTED, pendingBody(["gce:loadBalancers:test-account:us-central1:listener-a"]))     || ExecutionStatus.RUNNING
    "202 other cached row"     | Response.success(HTTP_ACCEPTED, pendingBody(["gce:loadBalancers:test-account:us-central1:other-listener"])) || ExecutionStatus.SUCCEEDED
  }

  @Unroll
  void "retries regional refresh on retryable Clouddriver status #statusCode"() {
    given:
    useRegionalContext()
    def refreshCall = Mock(Call)
    task.cacheService.forceCacheUpdate('gce', 'LoadBalancer', _) >> refreshCall
    refreshCall.execute() >> { throw httpException(statusCode) }

    expect:
    task.execute(stage).status == ExecutionStatus.RUNNING

    where:
    statusCode << [429, 500, 503]
  }

  @Unroll
  void "fails regional refresh with context on terminal Clouddriver status #statusCode"() {
    given:
    useRegionalContext()
    def refreshCall = Mock(Call)
    task.cacheService.forceCacheUpdate('gce', 'LoadBalancer', _) >> refreshCall
    refreshCall.execute() >> { throw httpException(statusCode) }

    when:
    task.execute(stage)

    then:
    def error = thrown(IllegalStateException)
    error.message.contains(statusCode.toString())
    error.message.contains('listener-a')
    error.message.contains('us-central1')
    error.message.contains('test-account')

    where:
    statusCode << [HTTP_BAD_REQUEST, 404]
  }

  void "retries regional refresh on network failures"() {
    given:
    useRegionalContext()
    def refreshCall = Mock(Call)
    task.cacheService.forceCacheUpdate('gce', 'LoadBalancer', _) >> refreshCall
    // Retrofit2SyncCall reads the request off the call to build the SpinnakerNetworkException.
    refreshCall.request() >> new Request.Builder().url("http://clouddriver/cache/gce/LoadBalancer").build()
    refreshCall.execute() >> { throw new IOException("connection reset") }

    expect:
    task.execute(stage).status == ExecutionStatus.RUNNING
  }

  void "requires a region for regional deletes"() {
    given:
    useRegionalContext()
    stage.context.remove("regions")

    when:
    task.execute(stage)

    then:
    thrown(IllegalArgumentException)
    0 * task.cacheService._
  }

  void "reposts when a later region has not run the refresh yet"() {
    given:
    useRegionalContext()
    stage.context.regions = ["us-central1", "us-east1"]
    def refreshCall = Mock(Call)

    when:
    def result = task.execute(stage)

    then:
    2 * task.cacheService.forceCacheUpdate('gce', 'LoadBalancer', _) >> refreshCall
    1 * refreshCall.execute() >> Response.success(null)
    1 * refreshCall.execute() >> Response.success(HTTP_ACCEPTED, pendingBody([]))
    result.status == ExecutionStatus.RUNNING
    result.context.deleteRefreshState.completedTargets == ["test-account|us-central1|listener-a"]
  }

  void "refreshes every trusted deleted listener and preserves completed progress"() {
    given:
    useRegionalContext()
    stage.context."kato.last.task.id" = new TaskId("delete-task")
    stage.context."kato.tasks" = [[
      id: "delete-task",
      status: [completed: true, failed: false],
      resultObjects: [[deletedLoadBalancerNames: ["listener-a", "listener-b"]]],
    ]]

    when:
    def firstResult = task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate('gce', 'LoadBalancer', { it.loadBalancerName == "listener-a" }) >>
      Calls.response(null)
    1 * task.cacheService.forceCacheUpdate('gce', 'LoadBalancer', { it.loadBalancerName == "listener-b" }) >>
      Calls.response(Response.success(HTTP_ACCEPTED, pendingBody([])))
    firstResult.status == ExecutionStatus.RUNNING
    firstResult.context.deleteRefreshState.completedTargets == [
      "test-account|us-central1|listener-a"
    ]

    when:
    stage.context.putAll(firstResult.context)
    def secondResult = task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate('gce', 'LoadBalancer', { it.loadBalancerName == "listener-b" }) >>
      Calls.response(null)
    0 * task.cacheService._
    secondResult.status == ExecutionStatus.SUCCEEDED
  }

  void "ignores untrusted client listener names"() {
    given:
    useRegionalContext()
    stage.context.deletedLoadBalancerNames = ["untrusted-listener"]

    when:
    def result = task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate('gce', 'LoadBalancer', {
      it.loadBalancerName == regionalConfig.loadBalancerName
    }) >> Calls.response(null)
    0 * task.cacheService._
    result.status == ExecutionStatus.SUCCEEDED
  }

  void "ignores listener names from a different Kato task"() {
    given:
    useRegionalContext()
    stage.context."kato.last.task.id" = new TaskId("current-task")
    stage.context."kato.tasks" = [[
      id: "different-task",
      status: [completed: true, failed: false],
      resultObjects: [[deletedLoadBalancerNames: ["untrusted-listener"]]],
    ]]

    when:
    def result = task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate('gce', 'LoadBalancer', {
      it.loadBalancerName == regionalConfig.loadBalancerName
    }) >> Calls.response(null)
    0 * task.cacheService._
    result.status == ExecutionStatus.SUCCEEDED
  }
}
