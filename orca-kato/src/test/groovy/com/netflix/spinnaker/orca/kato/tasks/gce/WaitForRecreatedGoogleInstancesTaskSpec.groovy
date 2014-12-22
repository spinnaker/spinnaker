/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.orca.kato.tasks.gce

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class WaitForRecreatedGoogleInstancesTaskSpec extends Specification {

  def CREDENTIALS = "my-account-name"
  def REGION = "us-central1"
  def THE_PAST = 0
  def THE_FUTURE = Long.MAX_VALUE

  @Subject task = new WaitForRecreatedGoogleInstancesTask()

  def mapper = new OrcaObjectMapper()

  @Unroll
  void "should return RUNNING status when instance query throws exception"() {
    given:
      def pipeline = new Pipeline()
      def instanceId = 'roscoapp-dev-v000-z1ty'

      task.oortService = Stub(OortService) {
        getInstance(CREDENTIALS, REGION, instanceId) >> { throw new RetrofitError(null, null, null, null, null, null, null) }
      }

      and:
      def stage = new PipelineStage(pipeline, "whatever", [
        "credentials": CREDENTIALS,
        "region": REGION,
        "terminate.instance.ids": [instanceId],
        "launchTimes": [THE_PAST]
      ]).asImmutable()

    expect:
      task.execute(stage).status == ExecutionStatus.RUNNING
  }

  void "should query each instanceId and return SUCCEEDED status if they've all been recreated"() {
    given:
      def pipeline = new Pipeline()
      def instanceIds = ['roscoapp-dev-v000-z1ty', 'roscoapp-dev-v000-q8dm']
      task.objectMapper = mapper
      def response = new Response('oort', 200, 'ok', [], new TypedString("{\"launchTime\":$THE_FUTURE}"))
      task.oortService = Stub(OortService) {
        getInstance(CREDENTIALS, REGION, instanceIds[0]) >> response
        getInstance(CREDENTIALS, REGION, instanceIds[1]) >> response
      }

    and:
      def stage = new PipelineStage(pipeline, "whatever", [
        "credentials": CREDENTIALS,
        "region": REGION,
        "terminate.instance.ids": instanceIds,
        "launchTimes": [THE_PAST, THE_PAST]
      ]).asImmutable()

    expect:
      task.execute(stage).status == ExecutionStatus.SUCCEEDED
  }

  void "should return RUNNING status if any instance hasn't been recreated yet"() {
    given:
      def pipeline = new Pipeline()
      def instanceIds = ['roscoapp-dev-v000-z1ty', 'roscoapp-dev-v000-q8dm']
      task.objectMapper = mapper
      def response = new Response('oort', 200, 'ok', [], new TypedString("{\"launchTime\":$THE_PAST}"))
      task.oortService = Stub(OortService) {
        getInstance(CREDENTIALS, REGION, instanceIds[0]) >> response
      }

    and:
      def stage = new PipelineStage(pipeline, "whatever", [
        "credentials": CREDENTIALS,
        "region": REGION,
        "terminate.instance.ids": instanceIds,
        "launchTimes": [THE_PAST, THE_PAST]
      ]).asImmutable()

    expect:
      task.execute(stage).status == ExecutionStatus.RUNNING
  }
}
