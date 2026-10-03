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
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.OortService
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

import java.time.Duration
import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage
import static java.net.HttpURLConnection.HTTP_ACCEPTED
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST

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

  static ResponseBody jsonBody(String json) {
    ResponseBody.create(MediaType.parse("application/json"), json)
  }

  static ResponseBody pendingBody(List<String> identifiers) {
    jsonBody("{\"cachedIdentifiersByType\":{\"loadBalancers\":${identifiers.collect { "\"${it}\"" }}}}")
  }

  /** Mirrors GoogleLoadBalancerProvider.GoogleLoadBalancerDetails, which has no account or region. */
  static Map visibleLoadBalancer(String name, String loadBalancerType = "REGIONAL_EXTERNAL_NETWORK") {
    [loadBalancerName: name, loadBalancerType: loadBalancerType]
  }

  static SpinnakerHttpException httpException(int statusCode) {
    def response = Response.error(statusCode, jsonBody('{"message":"request failed"}'))
    def retrofit = new Retrofit.Builder()
      .baseUrl("http://clouddriver/")
      .addConverterFactory(JacksonConverterFactory.create())
      .build()
    new SpinnakerHttpException(response, retrofit)
  }

  static Map regionalTarget(String name = "listener-a",
                            String loadBalancerType = "REGIONAL_EXTERNAL_NETWORK",
                            String account = "test-account",
                            String region = "us-central1") {
    [
      account          : account,
      region           : region,
      availabilityZones: [(region): []],
      loadBalancerType : loadBalancerType,
      loadBalancerName : name,
      name             : name,
    ]
  }

  static Map completedTargetState(Map target, String region = target.region, int targetIndex = 0) {
    [
      key                    : "${targetIndex}|${target.account}|${region}|${target.loadBalancerType}|${target.loadBalancerName}".toString(),
      account                : target.account,
      region                 : region,
      loadBalancerType       : target.loadBalancerType,
      loadBalancerName       : target.loadBalancerName,
      hasRequested           : true,
      seenPendingCacheUpdates: true,
      attempt                : 0,
      allAreComplete         : true,
      refreshIds             : [],
      observedRefreshIds     : [],
    ]
  }

  void useCompletedRegionalContext(Map target = regionalTarget()) {
    stage.context = [
      cloudProvider: "gce",
      targets      : [target],
      refreshState : [targetStates: [completedTargetState(target)]],
    ]
  }

  // Historical behavior for every provider and for existing GCE families.

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
    !result.context.refreshState.containsKey("targetStates")
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
      Calls.response(Response.success(202, jsonBody(json)))
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

  void "completes when an accepted refresh reports identifiers outside the loadBalancers namespace"() {
    given:
    stage.context.cloudProvider = "azure"

    when:
    def result = task.execute(stage)

    then:
    1 * cloudDriverCacheService.forceCacheUpdate('azure', 'LoadBalancer', _) >>
      Calls.response(Response.success(HTTP_ACCEPTED, jsonBody('{"cachedIdentifiersByType":{"azureLoadBalancers":["lb-key"]}}')))
    result.status == ExecutionStatus.SUCCEEDED
    result.context.refreshState.allAreComplete == true
  }

  void "short circuits existing gce families whose pending rows expose only key details"() {
    given:
    stage.context = [
      cloudProvider   : "gce",
      loadBalancerType: "INTERNAL_MANAGED",
      targets         : config.targets,
      refreshState    : [
        hasRequested           : true,
        seenPendingCacheUpdates: false,
        attempt                : UpsertLoadBalancerForceRefreshTask.MAX_CHECK_FOR_PENDING - 1,
        allAreComplete         : false,
        refreshIds             : ["gce:loadBalancers:spinnaker:us-west-1:flapjack-frontend"],
      ],
    ]

    when:
    def result = task.execute(stage)

    then:
    1 * cloudDriverCacheStatusService.pendingForceCacheUpdates('gce', 'LoadBalancer') >> Calls.response([[
      details: [provider: "gce", type: "loadBalancers", account: "spinnaker", region: "us-west-1", name: "flapjack-frontend"]
    ]])
    result.status == ExecutionStatus.RUNNING
    result.context.refreshState.seenPendingCacheUpdates == false

    when:
    stage.context.putAll(result.context)
    result = task.execute(stage)

    then:
    0 * cloudDriverCacheStatusService._
    0 * oortService._
    result.status == ExecutionStatus.SUCCEEDED
  }

  @Unroll
  void "never queries Oort for #cloudProvider #loadBalancerType"() {
    given:
    stage.context.cloudProvider = cloudProvider
    stage.context.loadBalancerType = loadBalancerType

    when:
    def result = task.execute(stage)

    then:
    1 * cloudDriverCacheService.forceCacheUpdate(cloudProvider, 'LoadBalancer', _) >> Calls.response(null)
    0 * oortService._
    result.status == ExecutionStatus.SUCCEEDED

    where:
    cloudProvider | loadBalancerType
    "gce"         | "NETWORK"
    "gce"         | "INTERNAL"
    "gce"         | "INTERNAL_MANAGED"
    "aws"         | "EXTERNAL_MANAGED"
  }

  @Unroll
  void "backs off #expectedSeconds s for #cloudProvider #loadBalancerType when seenPending=#seenPending, complete=#complete, attempt=#attempt"() {
    given:
    stage.context.cloudProvider = cloudProvider
    stage.context.loadBalancerType = loadBalancerType
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
    cloudProvider | loadBalancerType            | seenPending | complete | attempt || expectedSeconds
    "aws"         | null                        | false       | false    | 0       || 1
    "aws"         | null                        | false       | true     | 0       || 1
    "aws"         | null                        | false       | false    | 3       || 1
    "aws"         | null                        | true        | false    | 0       || 5
    "gce"         | "REGIONAL_EXTERNAL_NETWORK" | false       | false    | 0       || 1
    "gce"         | "REGIONAL_EXTERNAL_NETWORK" | false       | false    | 2       || 1
    "gce"         | "REGIONAL_EXTERNAL_NETWORK" | true        | false    | 0       || 5
    "gce"         | "REGIONAL_EXTERNAL_NETWORK" | false       | true     | 0       || 5
    "gce"         | "REGIONAL_EXTERNAL_NETWORK" | false       | false    | 3       || 5
  }

  // Regional families: EXTERNAL_MANAGED and REGIONAL_EXTERNAL_NETWORK on gce.

  @Unroll
  void "maps regional #responseCase force-cache response to #expectedStatus"() {
    given:
    stage.context = [cloudProvider: "gce", targets: [regionalTarget()]]

    when:
    def result = task.execute(stage)

    then:
    1 * cloudDriverCacheService.forceCacheUpdate('gce', 'LoadBalancer', [
      loadBalancerName: "listener-a",
      region          : "us-central1",
      account         : "test-account",
      loadBalancerType: "REGIONAL_EXTERNAL_NETWORK",
    ]) >> response
    oortCalls * oortService.getLoadBalancerDetails("gce", "test-account", "us-central1", "listener-a") >>
      Calls.response([visibleLoadBalancer("listener-a")])
    result.status == expectedStatus
    result.context.refreshState.targetStates*.hasRequested == [expectedHasRequested]
    result.context.refreshState.targetStates*.refreshIds == [expectedRefreshIds]

    where:
    responseCase        | response                                                                                               | oortCalls || expectedStatus            | expectedHasRequested | expectedRefreshIds
    "200 complete"      | Calls.response(null)                                                                                   | 1         || ExecutionStatus.SUCCEEDED | true                 | []
    "202 empty IDs"     | Calls.response(Response.success(HTTP_ACCEPTED, pendingBody([])))                                        | 0         || ExecutionStatus.RUNNING   | false                | []
    "202 missing IDs"   | Calls.response(Response.success(HTTP_ACCEPTED, jsonBody("{}")))                                         | 0         || ExecutionStatus.RUNNING   | false                | []
    "202 populated IDs" | Calls.response(Response.success(HTTP_ACCEPTED, pendingBody(["gce:loadBalancers:test-account:us-central1:listener-a"]))) | 0 || ExecutionStatus.RUNNING | true | ["gce:loadBalancers:test-account:us-central1:listener-a"]
  }

  @Unroll
  void "reposts regional refresh on retryable Clouddriver status #statusCode"() {
    given:
    stage.context = [cloudProvider: "gce", targets: [regionalTarget()]]
    def refreshCall = Mock(Call)
    cloudDriverCacheService.forceCacheUpdate('gce', 'LoadBalancer', _) >> refreshCall
    refreshCall.execute() >> { throw httpException(statusCode) }

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
    result.context.refreshState.targetStates*.hasRequested == [false]

    where:
    // java.net.HttpURLConnection has no constant for 429.
    statusCode << [429, 500, 503]
  }

  void "fails regional refresh on terminal Clouddriver status"() {
    given:
    stage.context = [cloudProvider: "gce", targets: [regionalTarget()]]
    def refreshCall = Mock(Call)
    cloudDriverCacheService.forceCacheUpdate('gce', 'LoadBalancer', _) >> refreshCall
    refreshCall.execute() >> { throw httpException(HTTP_BAD_REQUEST) }

    when:
    task.execute(stage)

    then:
    def error = thrown(IllegalStateException)
    error.message.contains('400')
    error.message.contains('listener-a')
    error.message.contains('us-central1')
  }

  void "reposts regional refresh on network failures"() {
    given:
    stage.context = [cloudProvider: "gce", targets: [regionalTarget()]]
    def refreshCall = Mock(Call)
    cloudDriverCacheService.forceCacheUpdate('gce', 'LoadBalancer', _) >> refreshCall
    // Retrofit2SyncCall converts the IO failure into a SpinnakerNetworkException, reading the
    // request off the call to do so.
    refreshCall.request() >> new Request.Builder().url("http://clouddriver/cache/gce/LoadBalancer").build()
    refreshCall.execute() >> { throw new IOException("connection reset") }

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
    result.context.refreshState.targetStates*.hasRequested == [false]
  }

  void "matches gce pending rows by their parsed key details"() {
    given:
    String refreshId = "gce:loadBalancers:test-account:us-central1:listener-a"
    Map targetContext = [cloudProvider: "gce", targets: [regionalTarget()]]
    stage.context = new HashMap(targetContext)
    cloudDriverCacheService.forceCacheUpdate('gce', 'LoadBalancer', _) >>
      Calls.response(Response.success(HTTP_ACCEPTED, pendingBody([refreshId])))

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING

    when:
    stage.context = new HashMap(targetContext)
    stage.context.putAll(result.context)
    result = task.execute(stage)

    then:
    1 * cloudDriverCacheStatusService.pendingForceCacheUpdates('gce', 'LoadBalancer') >> Calls.response([[
      details: [provider: "gce", type: "loadBalancers", account: "test-account", region: "us-central1", name: "listener-a"]
    ]])
    result.status == ExecutionStatus.RUNNING
    result.context.refreshState.targetStates*.seenPendingCacheUpdates == [true]
    result.context.refreshState.attempt == 0
  }

  void "waits for regional load balancers to become visible after the cache refresh completes"() {
    given:
    useCompletedRegionalContext()
    Map completedContext = new HashMap(stage.context)

    when:
    def result = task.execute(stage)

    then:
    1 * oortService.getLoadBalancerDetails('gce', 'test-account', 'us-central1', 'listener-a') >> Calls.response([])
    result.status == ExecutionStatus.RUNNING

    when:
    stage.context = new HashMap(completedContext)
    stage.context.putAll(result.context)
    result = task.execute(stage)

    then:
    1 * oortService.getLoadBalancerDetails('gce', 'test-account', 'us-central1', 'listener-a') >>
      Calls.response([visibleLoadBalancer("listener-a")])
    result.status == ExecutionStatus.SUCCEEDED
  }

  void "requires every regional target region to become visible"() {
    given:
    Map target = regionalTarget()
    target.availabilityZones = ["us-central1": [], "us-east1": []]
    stage.context = [
      cloudProvider: "gce",
      targets      : [target],
      refreshState : [targetStates: [
        completedTargetState(target, "us-central1"),
        completedTargetState(target, "us-east1"),
      ]],
    ]

    when:
    def result = task.execute(stage)

    then:
    1 * oortService.getLoadBalancerDetails('gce', 'test-account', 'us-central1', 'listener-a') >>
      Calls.response([visibleLoadBalancer("listener-a")])
    1 * oortService.getLoadBalancerDetails('gce', 'test-account', 'us-east1', 'listener-a') >> Calls.response([])
    result.status == ExecutionStatus.RUNNING
  }

  @Unroll
  void "requires exact name and family visibility for #targetType with #visibilityCase"() {
    given:
    useCompletedRegionalContext(regionalTarget("listener-a", targetType))

    when:
    def result = task.execute(stage)

    then:
    // Account and region scoping comes from the request path, so the lookup must use the target's.
    1 * oortService.getLoadBalancerDetails("gce", "test-account", "us-central1", "listener-a") >> Calls.response(details)
    0 * oortService._
    result.status == expectedStatus

    where:
    targetType                  | visibilityCase | details                                                     || expectedStatus
    "REGIONAL_EXTERNAL_NETWORK" | "exact match"  | [visibleLoadBalancer("listener-a", "REGIONAL_EXTERNAL_NETWORK")] || ExecutionStatus.SUCCEEDED
    "EXTERNAL_MANAGED"          | "exact match"  | [visibleLoadBalancer("listener-a", "EXTERNAL_MANAGED")]     || ExecutionStatus.SUCCEEDED
    "EXTERNAL_MANAGED"          | "wrong name"   | [visibleLoadBalancer("listener-b", "EXTERNAL_MANAGED")]     || ExecutionStatus.RUNNING
    "EXTERNAL_MANAGED"          | "wrong family" | [visibleLoadBalancer("listener-a", "INTERNAL_MANAGED")]     || ExecutionStatus.RUNNING
    "EXTERNAL_MANAGED"          | "absent"       | []                                                          || ExecutionStatus.RUNNING
  }

  @Unroll
  void "keeps running when regional visibility lookup fails with #failureCase"() {
    given:
    useCompletedRegionalContext()
    def request = new Request.Builder().url("http://oort/gce/loadBalancers/test-account/us-central1/listener-a").build()
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
    1 * oortService.getLoadBalancerDetails("gce", "test-account", "us-central1", "listener-a") >> oortCall
    0 * oortService._
    result.status == ExecutionStatus.RUNNING

    where:
    failureCase                 | failureType | statusCode
    "Oort HTTP 429"             | "http"      | 429
    "Oort HTTP 500"             | "http"      | 500
    "Oort HTTP 503"             | "http"      | 503
    "SpinnakerNetworkException" | "network"   | null
    "SpinnakerServerException"  | "server"    | null
  }

  @Unroll
  void "fails with context when regional visibility lookup returns Oort HTTP #statusCode"() {
    given:
    useCompletedRegionalContext()
    def oortCall = Mock(Call)
    oortCall.execute() >> { throw httpException(statusCode) }

    when:
    task.execute(stage)

    then:
    1 * oortService.getLoadBalancerDetails("gce", "test-account", "us-central1", "listener-a") >> oortCall
    0 * oortService._
    def error = thrown(IllegalStateException)
    error.message.contains(statusCode.toString())
    error.message.contains("listener-a")
    error.message.contains("test-account")
    error.message.contains("us-central1")

    where:
    statusCode << [HTTP_BAD_REQUEST, 404]
  }

  void "uses serialized plural producer output for target-local refresh and visibility"() {
    given:
    def producer = new UpsertLoadBalancersTask(
      kato: Stub(KatoService) {
        requestOperations("gce", _) >> new TaskId("task-id")
      }
    )
    def producerResult = producer.execute(stage {
      context = [
        cloudProvider: "gce",
        credentials: "stage-account",
        loadBalancerType: "HTTP",
        loadBalancers: [
          [
            account: "account-a",
            region: "us-central1",
            loadBalancerType: "EXTERNAL_MANAGED",
            name: "listener-a",
            urlMapName: "shared-map",
          ],
          [
            credentials: "account-b",
            region: "us-east1",
            loadBalancerType: "REGIONAL_EXTERNAL_NETWORK",
            name: "listener-b",
          ],
        ],
      ]
    })
    List<Map> serializedTargets = new ObjectMapper().readValue(
      new ObjectMapper().writeValueAsString(producerResult.context.targets),
      List
    ) as List<Map>
    stage.context = [
      cloudProvider: "gce",
      loadBalancerType: "HTTP",
      targets: serializedTargets,
    ]

    when:
    def result = task.execute(stage)

    then:
    1 * cloudDriverCacheService.forceCacheUpdate("gce", "LoadBalancer", [
      loadBalancerName: "listener-a",
      region: "us-central1",
      account: "account-a",
      loadBalancerType: "EXTERNAL_MANAGED",
    ]) >> Calls.response(null)
    1 * cloudDriverCacheService.forceCacheUpdate("gce", "LoadBalancer", [
      loadBalancerName: "listener-b",
      region: "us-east1",
      account: "account-b",
      loadBalancerType: "REGIONAL_EXTERNAL_NETWORK",
    ]) >> Calls.response(null)
    1 * oortService.getLoadBalancerDetails("gce", "account-a", "us-central1", "listener-a") >>
      Calls.response([visibleLoadBalancer("listener-a", "EXTERNAL_MANAGED")])
    1 * oortService.getLoadBalancerDetails("gce", "account-b", "us-east1", "listener-b") >>
      Calls.response([visibleLoadBalancer("listener-b", "REGIONAL_EXTERNAL_NETWORK")])
    0 * cloudDriverCacheService._
    0 * oortService._
    result.status == ExecutionStatus.SUCCEEDED
  }

  void "uses per-target refresh state for regional families"() {
    given:
    stage.context = [
      cloudProvider: "gce",
      targets: [
        regionalTarget("listener-a", "EXTERNAL_MANAGED", "account-a", "us-central1"),
        regionalTarget("listener-b", "REGIONAL_EXTERNAL_NETWORK", "account-b", "us-east1"),
      ],
    ]

    when:
    def result = task.execute(stage)

    then:
    1 * cloudDriverCacheService.forceCacheUpdate("gce", "LoadBalancer", {
      it.loadBalancerName == "listener-a" &&
        it.account == "account-a" &&
        it.region == "us-central1" &&
        it.loadBalancerType == "EXTERNAL_MANAGED"
    }) >> Calls.response(null)
    1 * cloudDriverCacheService.forceCacheUpdate("gce", "LoadBalancer", {
      it.loadBalancerName == "listener-b" &&
        it.account == "account-b" &&
        it.region == "us-east1" &&
        it.loadBalancerType == "REGIONAL_EXTERNAL_NETWORK"
    }) >> Calls.response(Response.success(
      HTTP_ACCEPTED,
      pendingBody(["gce:loadBalancers:account-b:us-east1:listener-b"])
    ))
    result.status == ExecutionStatus.RUNNING
    result.context.refreshState.targetStates*.hasRequested == [true, true]
    result.context.refreshState.targetStates*.allAreComplete == [true, false]
  }

  void "reposts when a later region accepts the refresh without identifiers"() {
    given:
    Map target = regionalTarget()
    target.availabilityZones = ["us-central1": [], "us-east1": []]
    stage.context = [cloudProvider: "gce", targets: [target]]
    cloudDriverCacheService.forceCacheUpdate('gce', 'LoadBalancer', _) >>> [
      Calls.response(null),
      Calls.response(Response.success(HTTP_ACCEPTED, pendingBody([])))
    ]

    when:
    def result = task.execute(stage)

    then:
    // The first region's completed refresh must not mask the second region's no-op.
    result.status == ExecutionStatus.RUNNING
    result.context.refreshState.targetStates*.hasRequested == [true, false]
    result.context.refreshState.allAreComplete == false
  }

  void "falls back to exact Oort verification when accepted identifiers never appear"() {
    given:
    Map targetContext = [cloudProvider: "gce", targets: [regionalTarget("listener-a", "EXTERNAL_MANAGED")]]
    stage.context = new HashMap(targetContext)

    when:
    def result = task.execute(stage)

    then:
    1 * cloudDriverCacheService.forceCacheUpdate("gce", "LoadBalancer", _) >>
      Calls.response(Response.success(
        HTTP_ACCEPTED,
        pendingBody(["gce:loadBalancers:test-account:us-central1:listener-a"])
      ))
    result.status == ExecutionStatus.RUNNING

    when:
    stage.context = new HashMap(targetContext)
    stage.context.putAll(result.context)
    result = task.execute(stage)

    then:
    1 * cloudDriverCacheStatusService.pendingForceCacheUpdates("gce", "LoadBalancer") >> Calls.response([])
    result.status == ExecutionStatus.RUNNING
    result.context.refreshState.attempt == 1

    when:
    stage.context = new HashMap(targetContext)
    stage.context.putAll(result.context)
    result = task.execute(stage)

    then:
    1 * cloudDriverCacheStatusService.pendingForceCacheUpdates("gce", "LoadBalancer") >> Calls.response([])
    result.status == ExecutionStatus.RUNNING
    result.context.refreshState.attempt == 2

    when:
    stage.context = new HashMap(targetContext)
    stage.context.putAll(result.context)
    result = task.execute(stage)

    then:
    1 * cloudDriverCacheStatusService.pendingForceCacheUpdates("gce", "LoadBalancer") >> Calls.response([])
    1 * oortService.getLoadBalancerDetails("gce", "test-account", "us-central1", "listener-a") >> Calls.response([])
    result.status == ExecutionStatus.RUNNING
    result.context.refreshState.allAreComplete == true

    when:
    stage.context = new HashMap(targetContext)
    stage.context.putAll(result.context)
    result = task.execute(stage)

    then:
    0 * cloudDriverCacheStatusService._
    1 * oortService.getLoadBalancerDetails("gce", "test-account", "us-central1", "listener-a") >>
      Calls.response([visibleLoadBalancer("listener-a", "EXTERNAL_MANAGED")])
    result.status == ExecutionStatus.SUCCEEDED
  }

  static class NoSleepRetry extends RetrySupport {
    void sleep(long time) {}
  }
}
