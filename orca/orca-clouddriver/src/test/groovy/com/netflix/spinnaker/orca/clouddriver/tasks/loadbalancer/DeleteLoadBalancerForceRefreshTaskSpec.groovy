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

  void "should force cache refresh load balancer via clouddriver when clusterName provided"() {
  setup:
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
    1 * refreshCall.execute() >> Response.success(null)
    result.status == ExecutionStatus.SUCCEEDED
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

  void "accepts a pending refresh that reports the evicted identifiers"() {
    given:
    def refreshCall = Mock(Call)

    when:
    // A non-atomic agent scheduler answers every stored on-demand result with 202. Re-POSTing that
    // would keep the stage running until it timed out, failing a delete that already succeeded.
    def result = task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate('aws', 'LoadBalancer', _) >> refreshCall
    1 * refreshCall.execute() >> Response.success(
      HTTP_ACCEPTED,
      pendingBody(['aws:loadBalancers:fzlem:us-west-1:flapjack-main-frontend'])
    )
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
  void "keeps retrying on retryable status #statusCode"() {
    given:
    def refreshCall = Mock(Call)
    task.cacheService.forceCacheUpdate('aws', 'LoadBalancer', _) >> refreshCall
    refreshCall.execute() >> (statusCode == HTTP_ACCEPTED
      ? Response.success(HTTP_ACCEPTED, pendingBody([]))
      : Response.error(statusCode, ResponseBody.create(MediaType.parse("application/json"), "{}")))

    expect:
    task.execute(stage).status == ExecutionStatus.RUNNING

    where:
    // java.net.HttpURLConnection has no constant for 429.
    statusCode << [HTTP_ACCEPTED, 429, 500, 503]
  }

  void "fails on terminal client errors"() {
    given:
    def refreshCall = Mock(Call)
    task.cacheService.forceCacheUpdate('aws', 'LoadBalancer', _) >> refreshCall
    refreshCall.execute() >> Response.error(HTTP_BAD_REQUEST, ResponseBody.create(MediaType.parse("application/json"), "{}"))

    when:
    task.execute(stage)

    then:
    def error = thrown(IllegalStateException)
    error.message.contains('400')
    error.message.contains('flapjack-main-frontend')
    error.message.contains('us-west-1')
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
