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
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
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

  @Unroll
  void "maps #responseCase force-cache response to #expectedStatus"() {
    given:
    def refreshCall = Mock(Call)

    when:
    def result = task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate(stage.context.cloudProvider, DeleteLoadBalancerForceRefreshTask.REFRESH_TYPE, _) >> {
      String cloudProvider, String type, Map<String, Object> body ->

      assert body.loadBalancerName == config.loadBalancerName
      assert body.account == config.credentials
      assert body.region == "us-west-1"
      assert body.evict == true
      refreshCall
    }
    1 * refreshCall.execute() >> response
    result.status == expectedStatus

    where:
    responseCase        | response                                                                                                             || expectedStatus
    "200 complete"      | Response.success(null)                                                                                               || ExecutionStatus.SUCCEEDED
    "202 empty IDs"     | Response.success(HTTP_ACCEPTED, pendingBody([]))                                                                      || ExecutionStatus.RUNNING
    // A populated 202 means a non-atomic agent stored the eviction, so re-POSTing would never finish.
    "202 populated IDs" | Response.success(HTTP_ACCEPTED, pendingBody(["aws:loadBalancers:fzlem:us-west-1:flapjack-main-frontend"]))              || ExecutionStatus.SUCCEEDED
    "429 throttled"     | Response.error(429, ResponseBody.create(MediaType.parse("application/json"), "{}"))                                   || ExecutionStatus.RUNNING
    "500 server error"  | Response.error(500, ResponseBody.create(MediaType.parse("application/json"), "{}"))                                   || ExecutionStatus.RUNNING
    "503 server error"  | Response.error(503, ResponseBody.create(MediaType.parse("application/json"), "{}"))                                   || ExecutionStatus.RUNNING
  }

  void "retries until clouddriver accepts the refresh"() {
    given:
    def refreshCall = Mock(Call)

    when:
    def result = task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate('aws', 'LoadBalancer', _) >> refreshCall
    1 * refreshCall.execute() >> Response.success(HTTP_ACCEPTED, pendingBody([]))
    result.status == ExecutionStatus.RUNNING

    when:
    result = task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate('aws', 'LoadBalancer', _) >> refreshCall
    1 * refreshCall.execute() >> Response.success(null)
    result.status == ExecutionStatus.SUCCEEDED
  }

  void "reposts when a later region has not run the refresh yet"() {
    given:
    stage.context.regions = ["us-west-1", "us-east-1"]
    def refreshCall = Mock(Call)

    when:
    def result = task.execute(stage)

    then:
    2 * task.cacheService.forceCacheUpdate('aws', 'LoadBalancer', _) >> refreshCall
    1 * refreshCall.execute() >> Response.success(null)
    1 * refreshCall.execute() >> Response.success(HTTP_ACCEPTED, pendingBody([]))
    result.status == ExecutionStatus.RUNNING
  }

  @Unroll
  void "fails with context on terminal client status #statusCode"() {
    given:
    def refreshCall = Mock(Call)
    task.cacheService.forceCacheUpdate('aws', 'LoadBalancer', _) >> refreshCall
    refreshCall.execute() >> Response.error(statusCode, ResponseBody.create(MediaType.parse("application/json"), "{}"))

    when:
    task.execute(stage)

    then:
    def error = thrown(IllegalStateException)
    error.message.contains(statusCode.toString())
    error.message.contains('flapjack-main-frontend')
    error.message.contains('us-west-1')
    error.message.contains('fzlem')

    where:
    statusCode << [HTTP_BAD_REQUEST, 404]
  }

  void "retries on network failures"() {
    given:
    def refreshCall = Mock(Call)
    task.cacheService.forceCacheUpdate('aws', 'LoadBalancer', _) >> refreshCall
    // Retrofit2SyncCall reads the request off the call to build the SpinnakerNetworkException.
    refreshCall.request() >> new Request.Builder().url("http://clouddriver/cache/aws/LoadBalancer").build()
    refreshCall.execute() >> { throw new IOException("connection reset") }

    expect:
    task.execute(stage).status == ExecutionStatus.RUNNING
  }
}
