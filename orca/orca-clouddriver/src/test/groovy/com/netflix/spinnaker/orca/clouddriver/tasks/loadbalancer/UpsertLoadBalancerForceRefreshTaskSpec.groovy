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
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheStatusService
import com.netflix.spinnaker.orca.clouddriver.OortService
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

  static ResponseBody pendingBody(List<String> identifiers) {
    ResponseBody.create(
      MediaType.parse("application/json"),
      "{\"cachedIdentifiersByType\":{\"loadBalancers\":${identifiers.collect { "\"${it}\"" }}}}"
    )
  }

  static SpinnakerHttpException httpException(int statusCode) {
    def response = Response.error(
      statusCode,
      ResponseBody.create(MediaType.parse("application/json"), '{"message":"visibility lookup failed"}')
    )
    def retrofit = new Retrofit.Builder()
      .baseUrl("http://oort/")
      .addConverterFactory(JacksonConverterFactory.create())
      .build()
    new SpinnakerHttpException(response, retrofit)
  }

  void useCompletedRegionalRefreshContext() {
    stage.context = [
      cloudProvider: "gce",
      loadBalancerType: "REGIONAL_EXTERNAL_NETWORK",
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
  }

  @Unroll
  void "maps #responseCase force-cache response to #expectedStatus"() {
    when:
    1 * cloudDriverCacheService.forceCacheUpdate('aws', 'LoadBalancer', _) >> {
      String cloudProvider, String type, Map<String, Object> body ->
        assert cloudProvider == "aws"
        assert body.loadBalancerName == "flapjack-frontend"
        assert body.account == "spinnaker"
        assert body.region == "us-west-1"
        response
    }

    def result = task.execute(stage)

    then:
    result.status == expectedStatus
    result.context.refreshState.hasRequested == expectedHasRequested
    result.context.refreshState.allAreComplete == expectedAllAreComplete
    result.context.refreshState.refreshIds == expectedRefreshIds
    result.context.refreshState.seenPendingCacheUpdates == false
    result.context.refreshState.attempt == 0

    where:
    responseCase        | response                                                                                                       || expectedStatus            | expectedHasRequested | expectedAllAreComplete | expectedRefreshIds
    "200 complete"      | Calls.response(null)                                                                                           || ExecutionStatus.SUCCEEDED  | true                 | true                   | []
    "202 empty IDs"     | Calls.response(Response.success(HTTP_ACCEPTED, pendingBody([])))                                               || ExecutionStatus.RUNNING    | false                | false                  | []
    "202 missing IDs"   | Calls.response(Response.success(HTTP_ACCEPTED, ResponseBody.create(MediaType.parse("application/json"), "{}"))) || ExecutionStatus.RUNNING    | false                | false                  | []
    "202 populated IDs" | Calls.response(Response.success(HTTP_ACCEPTED, pendingBody(["aws:loadBalancers:spinnaker:us-west-1:flapjack-frontend"]))) || ExecutionStatus.RUNNING | true | false | ["aws:loadBalancers:spinnaker:us-west-1:flapjack-frontend"]
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
      loadBalancerType: "REGIONAL_EXTERNAL_NETWORK",
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
    stage.context.loadBalancerType = "REGIONAL_EXTERNAL_NETWORK"
    result = task.execute(stage)

    then:
    1 * oortService.getLoadBalancerDetails('gce', 'spinnaker', 'us-west-1', 'flapjack-frontend') >> Calls.response([[name: "flapjack-frontend"]])
    result.status == ExecutionStatus.SUCCEEDED
  }

  void "requires every gce target region to become visible"() {
    given:
    stage.context = [
      cloudProvider: "gce",
      loadBalancerType: "REGIONAL_EXTERNAL_NETWORK",
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

  // HTTP upserts are named for URL maps, but Oort's load balancer cache is keyed by forwarding
  // rules, so only regional external network load balancers can use the visibility check.
  @Unroll
  void "completion decision: #completionCase"() {
    given:
    stage.context = [
      cloudProvider: cloudProvider,
      loadBalancerType: loadBalancerType,
      targets: [
        [credentials: "spinnaker", availabilityZones: ["us-west-1": []], name: targetName]
      ],
      refreshState: [
        hasRequested: true,
        seenPendingCacheUpdates: seenPending,
        attempt: attempt,
        allAreComplete: allAreComplete,
        refreshIds: ["${cloudProvider}:loadBalancers:spinnaker:us-west-1:${targetName}".toString()]
      ]
    ]

    when:
    def result = task.execute(stage)

    then:
    oortCalls * oortService.getLoadBalancerDetails(_, _, _, _) >> {
      String provider, String account, String region, String name ->
        assert provider == "gce"
        assert account == "spinnaker"
        assert region == "us-west-1"
        assert name == targetName
        Calls.response(oortDetails)
    }
    result.status == expectedStatus

    where:
    completionCase                          | cloudProvider | loadBalancerType            | targetName          | allAreComplete | seenPending | attempt                                                   | oortCalls | oortDetails                    || expectedStatus
    "gce HTTP all-complete"                 | "gce"         | "HTTP"                      | "flapjack-urlmap"   | true           | true        | 0                                                         | 0         | null                           || ExecutionStatus.SUCCEEDED
    "gce regional all-complete, Oort miss"  | "gce"         | "REGIONAL_EXTERNAL_NETWORK" | "flapjack-frontend" | true           | true        | 0                                                         | 1         | []                             || ExecutionStatus.RUNNING
    "gce regional all-complete, Oort hit"   | "gce"         | "REGIONAL_EXTERNAL_NETWORK" | "flapjack-frontend" | true           | true        | 0                                                         | 1         | [[name: "flapjack-frontend"]]  || ExecutionStatus.SUCCEEDED
    "non-gce pending short circuit"         | "aws"         | null                        | "flapjack-frontend" | false          | false       | UpsertLoadBalancerForceRefreshTask.MAX_CHECK_FOR_PENDING | 0         | null                           || ExecutionStatus.SUCCEEDED
  }

  @Unroll
  void "keeps running when regional visibility lookup fails with #failureCase"() {
    given:
    useCompletedRegionalRefreshContext()
    def request = new Request.Builder().url("http://oort/loadBalancers/gce/spinnaker/us-west-1/flapjack-frontend").build()
    def oortCall = Mock(Call)
    if (failureType == "http") {
      oortCall.execute() >> { throw httpException(statusCode) }
    } else if (failureType == "network") {
      oortCall.request() >> request
      oortCall.execute() >> { throw new IOException("connection reset") }
    } else {
      oortCall.execute() >> { throw new SpinnakerServerException(new IOException("upstream failure"), request) }
    }

    when:
    def result = task.execute(stage)

    then:
    1 * oortService.getLoadBalancerDetails(_, _, _, _) >> {
      String provider, String account, String region, String name ->
        assert provider == "gce"
        assert account == "spinnaker"
        assert region == "us-west-1"
        assert name == "flapjack-frontend"
        oortCall
    }
    0 * oortService._
    result.status == ExecutionStatus.RUNNING

    where:
    failureCase               | failureType | statusCode
    "Oort HTTP 429"           | "http"      | 429
    "Oort HTTP 500"           | "http"      | 500
    "Oort HTTP 503"           | "http"      | 503
    "SpinnakerNetworkException" | "network" | null
    "SpinnakerServerException"  | "server"  | null
  }

  @Unroll
  void "fails with context when regional visibility lookup returns Oort HTTP #statusCode"() {
    given:
    useCompletedRegionalRefreshContext()
    def oortCall = Mock(Call)
    oortCall.execute() >> { throw httpException(statusCode) }

    when:
    task.execute(stage)

    then:
    1 * oortService.getLoadBalancerDetails(_, _, _, _) >> {
      String provider, String account, String region, String name ->
        assert provider == "gce"
        assert account == "spinnaker"
        assert region == "us-west-1"
        assert name == "flapjack-frontend"
        oortCall
    }
    0 * oortService._
    def error = thrown(IllegalStateException)
    error.message.contains(statusCode.toString())
    error.message.contains("flapjack-frontend")
    error.message.contains("spinnaker")
    error.message.contains("us-west-1")

    where:
    statusCode << [HTTP_BAD_REQUEST, 404]
  }

  void "keeps waiting when the pending short circuit fires before a gce load balancer is visible"() {
    given:
    stage.context = [
      cloudProvider: "gce",
      loadBalancerType: "REGIONAL_EXTERNAL_NETWORK",
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
