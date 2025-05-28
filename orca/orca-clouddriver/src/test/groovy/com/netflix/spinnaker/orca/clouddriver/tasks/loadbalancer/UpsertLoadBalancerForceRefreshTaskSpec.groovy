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
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class UpsertLoadBalancerForceRefreshTaskSpec extends Specification {
  def cloudDriverCacheService = Mock(CloudDriverCacheService)
  def cloudDriverCacheStatusService = Mock(CloudDriverCacheStatusService)

  @Subject
  def task = new UpsertLoadBalancerForceRefreshTask(
    cloudDriverCacheService,
    cloudDriverCacheStatusService,
    new ObjectMapper(),
    new NoSleepRetry()
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
      String cloudProvider, String type, Map<String, ? extends Object> body ->
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

  def "checks for pending onDemand keys and awaits processing"() {
    String json = """
      {"cachedIdentifiersByType":
         {"loadBalancers": ["aws:loadBalancers:spinnaker:us-west-1:flapjack-frontend"]}
      }
      """
    // Create the forceCacheUpdate request
    when:
    1 * cloudDriverCacheService.forceCacheUpdate('aws', 'LoadBalancer', _) >> {
      Calls.response(Response.success(202, ResponseBody.create(MediaType.parse("application/json"), json)))
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

  static class NoSleepRetry extends RetrySupport {
    void sleep(long time) {}
  }
}
