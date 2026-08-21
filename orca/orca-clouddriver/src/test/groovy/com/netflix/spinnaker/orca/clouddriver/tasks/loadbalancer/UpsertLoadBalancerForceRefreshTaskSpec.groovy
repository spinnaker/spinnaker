/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheStatusService
import com.netflix.spinnaker.orca.clouddriver.OortService
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.time.Duration
import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage
import static java.net.HttpURLConnection.HTTP_ACCEPTED
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST
import static java.net.HttpURLConnection.HTTP_OK

class UpsertLoadBalancerForceRefreshTaskSpec extends Specification {
  def cloudDriverCacheService = Mock(CloudDriverCacheService)
  def cloudDriverCacheStatusService = Mock(CloudDriverCacheStatusService)
  def oortService = Mock(OortService)

  @Subject
  def task = new UpsertLoadBalancerForceRefreshTask(
    cloudDriverCacheService,
    cloudDriverCacheStatusService,
    new ObjectMapper(),
    new NoSleepRetry(),
    oortService
  )

  def stage = stage()

  def config = [
    targets: [
      [credentials: "spinnaker", availabilityZones: ["us-west-1": []], name: "flapjack-frontend"]
    ]
  ]

  def setup() {
    stage.context.putAll(config)
  }

  void "should force cache refresh server groups via oort when name provided"() {
    when:
    1 * cloudDriverCacheService.forceCacheUpdate('aws', 'LoadBalancer', _) >> {
      String cloudProvider, String type, Map<String, Object> body ->
        assert cloudProvider == "aws"
        assert body.loadBalancerName == "flapjack-frontend"
        assert body.account == "spinnaker"
        assert body.region == "us-west-1"
        Calls.response(null)
    }

    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context.refreshState.hasRequested == true
    result.context.refreshState.allAreComplete == true
  }

  void "reposts when clouddriver accepts refresh without identifiers"() {
    given:
    String json = '{"cachedIdentifiersByType":{"loadBalancers":[]}}'
    cloudDriverCacheService.forceCacheUpdate('aws', 'LoadBalancer', _) >> {
      Calls.response(Response.success(HTTP_ACCEPTED, ResponseBody.create(MediaType.parse("application/json"), json)))
    }

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
    result.context.refreshState.hasRequested == false
    result.context.refreshState.allAreComplete == false
    result.context.refreshState.refreshIds == []
  }

  void "matches pending cache updates using clouddriver details"() {
    given:
    String refreshId = "gce:loadBalancers:spinnaker:us-west-1:flapjack-frontend"
    String json = """
      {"cachedIdentifiersByType":
         {"loadBalancers": ["${refreshId}"]}
      }
      """
    stage.context.cloudProvider = "gce"
    cloudDriverCacheService.forceCacheUpdate('gce', 'LoadBalancer', _) >> {
      Calls.response(Response.success(HTTP_ACCEPTED, ResponseBody.create(MediaType.parse("application/json"), json)))
    }

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING

    when:
    stage.context = new HashMap(result.context)
    stage.context.cloudProvider = "gce"
    result = task.execute(stage)

    then:
    1 * cloudDriverCacheStatusService.pendingForceCacheUpdates('gce', 'LoadBalancer') >> {
      Calls.response([[
        details: [
          provider: "gce",
          type: "loadBalancers",
          account: "spinnaker",
          region: "us-west-1",
          name: "flapjack-frontend",
        ]
      ]])
    }
    result.status == ExecutionStatus.RUNNING
    result.context.refreshState.seenPendingCacheUpdates == true
    result.context.refreshState.attempt == 0
  }

  def "checks for pending onDemand keys and awaits processing"() {
    String json = """
      {"cachedIdentifiersByType":
         {"loadBalancers": ["aws:loadBalancers:spinnaker:us-west-1:flapjack-frontend"]}
      }
      """
    // Create the forceCacheUpdate request
    when:
    1 * cloudDriverCacheService.forceCacheUpdate('aws', 'LoadBalancer', _) >> {
      Calls.response(Response.success(HTTP_ACCEPTED, ResponseBody.create(MediaType.parse("application/json"), json)))
    }

    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
    result.context.refreshState.hasRequested == true
    result.context.refreshState.allAreComplete == false
    result.context.refreshState.refreshIds == ["aws:loadBalancers:spinnaker:us-west-1:flapjack-frontend"]

    // checks for pending, receives empty list and retries
    when:
    1 * cloudDriverCacheStatusService.pendingForceCacheUpdates('aws', 'LoadBalancer') >> { Calls.response([]) }
    stage.context = result.context
    result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
    result.context.refreshState.attempt == 1
    result.context.refreshState.seenPendingCacheUpdates == false

    // sees a pending onDemand key for our load balancers
    when:
    1 * cloudDriverCacheStatusService.pendingForceCacheUpdates('aws', 'LoadBalancer') >> {
      Calls.response([[id: "aws:loadBalancers:spinnaker:us-west-1:flapjack-frontend"]])
    }

    stage.context = result.context
    result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
    result.context.refreshState.attempt == 1 // has not incremented
    result.context.refreshState.seenPendingCacheUpdates == true

    // onDemand key has been processed, task completes
    when:
    1 * cloudDriverCacheStatusService.pendingForceCacheUpdates('aws', 'LoadBalancer') >> { Calls.response([]) }
    stage.context = result.context
    result = task.execute(stage)

    then:
    result.context.refreshState.allAreComplete == true
    result.status == ExecutionStatus.SUCCEEDED
  }

  @Unroll
  void "reposts on retryable force-cache status #statusCode"() {
    given:
    def body = ResponseBody.create(MediaType.parse("application/json"), "[]")
    cloudDriverCacheService.forceCacheUpdate('aws', 'LoadBalancer', _) >> {
      Calls.response(Response.error(statusCode, body))
    }

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
    result.context.refreshState.hasRequested == false

    where:
    // java.net.HttpURLConnection has no constant for 429.
    statusCode << [429, 500, 503]
  }

  void "reposts on network failures"() {
    given:
    def refreshCall = Mock(Call)
    cloudDriverCacheService.forceCacheUpdate('aws', 'LoadBalancer', _) >> refreshCall
    // Retrofit2SyncCall converts the IO failure into a SpinnakerNetworkException, reading the
    // request off the call to do so.
    refreshCall.request() >> new Request.Builder().url("http://clouddriver/cache/aws/LoadBalancer").build()
    refreshCall.execute() >> { throw new IOException("connection reset") }

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
    result.context.refreshState.hasRequested == false
  }

  void "fails on terminal client errors"() {
    given:
    cloudDriverCacheService.forceCacheUpdate('aws', 'LoadBalancer', _) >> {
      Calls.response(Response.error(HTTP_BAD_REQUEST, ResponseBody.create(MediaType.parse("application/json"), "[]")))
    }

    when:
    task.execute(stage)

    then:
    def error = thrown(IllegalStateException)
    error.message.contains('400')
    error.message.contains('flapjack-frontend')
    error.message.contains('us-west-1')
  }

  void "waits for gce load balancers to become visible after cache refresh completes"() {
    given:
    stage.context.cloudProvider = "gce"
    stage.context = [
      cloudProvider: "gce",
      targets: [
        [credentials: "spinnaker", availabilityZones: ["us-west-1": []], name: "flapjack-frontend"]
      ],
      refreshState: [
        hasRequested: true,
        seenPendingCacheUpdates: true,
        attempt: 0,
        allAreComplete: true,
        refreshIds: ["gce:loadBalancers:spinnaker:us-west-1:flapjack-frontend"]
      ]
    ]

    when:
    def result = task.execute(stage)

    then:
    1 * oortService.getLoadBalancerDetails('gce', 'spinnaker', 'us-west-1', 'flapjack-frontend') >> Calls.response([])
    result.status == ExecutionStatus.RUNNING

    when:
    stage.context = new HashMap(config)
    stage.context.putAll(result.context)
    stage.context.cloudProvider = "gce"
    result = task.execute(stage)

    then:
    1 * oortService.getLoadBalancerDetails('gce', 'spinnaker', 'us-west-1', 'flapjack-frontend') >> Calls.response([[name: "flapjack-frontend"]])
    result.status == ExecutionStatus.SUCCEEDED
  }

  void "requires every gce target region to become visible"() {
    given:
    stage.context = [
      cloudProvider: "gce",
      targets: [
        [credentials: "spinnaker", availabilityZones: ["us-west-1": [], "us-east-1": []], name: "flapjack-frontend"]
      ],
      refreshState: [
        hasRequested: true,
        seenPendingCacheUpdates: true,
        attempt: 0,
        allAreComplete: true,
        refreshIds: []
      ]
    ]

    when:
    def result = task.execute(stage)

    then:
    1 * oortService.getLoadBalancerDetails('gce', 'spinnaker', 'us-west-1', 'flapjack-frontend') >> Calls.response([[name: "flapjack-frontend"]])
    1 * oortService.getLoadBalancerDetails('gce', 'spinnaker', 'us-east-1', 'flapjack-frontend') >> Calls.response([])
    result.status == ExecutionStatus.RUNNING
  }

  void "reposts when a later region accepts the refresh without identifiers"() {
    given:
    stage.context.targets = [
      [credentials: "spinnaker", availabilityZones: ["us-west-1": [], "us-east-1": []], name: "flapjack-frontend"]
    ]
    String json = '{"cachedIdentifiersByType":{"loadBalancers":[]}}'
    cloudDriverCacheService.forceCacheUpdate('aws', 'LoadBalancer', _) >>> [
      Calls.response(null),
      Calls.response(Response.success(HTTP_ACCEPTED, ResponseBody.create(MediaType.parse("application/json"), json)))
    ]

    when:
    def result = task.execute(stage)

    then:
    // The first region's completed refresh must not mask the second region's no-op.
    result.status == ExecutionStatus.RUNNING
    result.context.refreshState.hasRequested == false
    result.context.refreshState.allAreComplete == false
  }

  void "keeps waiting when the pending short circuit fires before a gce load balancer is visible"() {
    given:
    stage.context = [
      cloudProvider: "gce",
      targets: [
        [credentials: "spinnaker", availabilityZones: ["us-west-1": []], name: "flapjack-frontend"]
      ],
      refreshState: [
        hasRequested: true,
        seenPendingCacheUpdates: false,
        attempt: UpsertLoadBalancerForceRefreshTask.MAX_CHECK_FOR_PENDING,
        allAreComplete: false,
        refreshIds: ["gce:loadBalancers:spinnaker:us-west-1:flapjack-frontend"]
      ]
    ]

    when:
    def result = task.execute(stage)

    then:
    // Giving up on pending updates is not evidence the load balancer is cached, so the task must
    // still wait for the provider rather than report success.
    0 * cloudDriverCacheStatusService.pendingForceCacheUpdates(_, _)
    1 * oortService.getLoadBalancerDetails('gce', 'spinnaker', 'us-west-1', 'flapjack-frontend') >> Calls.response([])
    result.status == ExecutionStatus.RUNNING
  }

  void "short circuits to success for non-gce providers without consulting oort"() {
    given:
    stage.context = [
      cloudProvider: "aws",
      targets: [
        [credentials: "spinnaker", availabilityZones: ["us-west-1": []], name: "flapjack-frontend"]
      ],
      refreshState: [
        hasRequested: true,
        seenPendingCacheUpdates: false,
        attempt: UpsertLoadBalancerForceRefreshTask.MAX_CHECK_FOR_PENDING,
        allAreComplete: false,
        refreshIds: ["aws:loadBalancers:spinnaker:us-west-1:flapjack-frontend"]
      ]
    ]

    when:
    def result = task.execute(stage)

    then:
    0 * oortService.getLoadBalancerDetails(_, _, _, _)
    result.status == ExecutionStatus.SUCCEEDED
  }

  @Unroll
  void "backs off #expectedSeconds s when seenPending=#seenPending, complete=#complete, attempt=#attempt"() {
    given:
    stage.context.refreshState = [
      hasRequested: true,
      seenPendingCacheUpdates: seenPending,
      attempt: attempt,
      allAreComplete: complete,
      refreshIds: []
    ]

    expect:
    task.getDynamicBackoffPeriod(stage, Duration.ofSeconds(30)) == TimeUnit.SECONDS.toMillis(expectedSeconds)

    where:
    // The one-second poll exists only to short circuit quickly for load balancer types that never
    // report pending updates. Once that check is over, every remaining attempt waits on the
    // provider, which does not justify polling Oort once a second for the whole ten-minute timeout.
    seenPending | complete | attempt || expectedSeconds
    false       | false    | 0       || 1
    false       | false    | 2       || 1
    true        | false    | 0       || 5
    false       | true     | 0       || 5
    false       | false    | 3       || 5
  }

  static class NoSleepRetry extends RetrySupport {
    void sleep(long time) {}
  }
}
