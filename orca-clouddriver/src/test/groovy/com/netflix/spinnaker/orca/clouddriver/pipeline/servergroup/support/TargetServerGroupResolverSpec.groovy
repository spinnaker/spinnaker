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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class TargetServerGroupResolverSpec extends Specification {

  OortService oort = Mock(OortService)
  ObjectMapper mapper = new ObjectMapper()
  @Subject
  TargetServerGroupResolver subject = new TargetServerGroupResolver(oortService: oort, mapper: mapper)


  def "should resolve to target server groups"() {
    when:
      def tsgs = subject.resolveByParams(new TargetServerGroup.Params(
        cloudProvider: "abc",
        cluster: "test-app",
        credentials: "testCreds",
        locations: [new Location(type: Location.Type.REGION, value: "north-pole")],
        target: TargetServerGroup.Params.Target.current_asg,
      ))

    then:
      1 * oort.getTargetServerGroup("test", "testCreds", "test-app", "abc", "north-pole", "current_asg") >>
        new Response("clouddriver", 200, 'ok', [], new TypedString(mapper.writeValueAsString([
          name  : "test-app-v010",
          region: "north-pole",
          data  : 123,
        ])))
      tsgs.size() == 1
      tsgs[0].data == 123
      tsgs[0].getLocation()
      tsgs[0].getLocation().type == Location.Type.REGION
      tsgs[0].getLocation().value == "north-pole"

    when:
      tsgs = subject.resolveByParams(new TargetServerGroup.Params(
        cloudProvider: "gce",
        serverGroupName: "test-app-v010",
        credentials: "testCreds",
        locations: [new Location(type: Location.Type.REGION, value: "north-pole")]
      ))

    then:
      1 * oort.getServerGroup("test", "testCreds", "test-app", "test-app-v010", null, "gce") >>
        new Response("clouddriver", 200, 'ok', [], new TypedString(mapper.writeValueAsString([[
                                                                                                name  : "test-app-v010",
                                                                                                region: "north-pole",
                                                                                                data  : 123,
                                                                                                type  : "gce",
                                                                                              ]])))
      tsgs.size() == 1
      tsgs[0].data == 123
      tsgs[0].getLocation()
      tsgs[0].getLocation().type == Location.Type.REGION
      tsgs[0].getLocation().value == "north-pole"

    when: "null params returns empty list"
      tsgs = subject.resolveByParams(null)
    then:
      tsgs == []

    when: "non-null, empty params returns empty list"
      tsgs = subject.resolveByParams(new TargetServerGroup.Params())
    then:
      tsgs == []
  }

  def "should resolve target refs from previous DTSG stage"() {
    setup:
      TargetServerGroup want = new TargetServerGroup(name: "testTSG", region: "north-pole")
      TargetServerGroup decoy = new TargetServerGroup(name: "testTSG", region: "south-pole")

      Stage commonParent = Mock(Stage) {
        getId() >> "1"
      }

      Stage dtsgStage = Mock(Stage) {
        getType() >> DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE
        getId() >> "2"
        getParentStageId() >> "1"
        getContext() >> [targetReferences: [decoy, want]]
      }

      Stage stageLookingForRefs = Mock(Stage) {
        getId() >> "3"
        getParentStageId() >> "1"
        getContext() >> [region: "north-pole"]
        Execution e = Spy(Execution)
        getExecution() >> e
        e.stages >> [commonParent, dtsgStage, it]
      }

    when:
      def got = TargetServerGroupResolver.fromPreviousStage(stageLookingForRefs)

    then:
      got == want

    when:
      stageLookingForRefs = Mock(Stage) {
        getId() >> "3"
        getParentStageId() >> "1"
        getContext() >> [region: "east-1"] // doesn't exist.
        Execution e = Spy(Execution)
        getExecution() >> e
        e.stages >> [commonParent, dtsgStage, it]
      }
      TargetServerGroupResolver.fromPreviousStage(stageLookingForRefs)

    then:
      thrown(TargetServerGroup.NotFoundException)
  }

  @Unroll
  def "should retry on network + 429 errors"() {
    given:
    def invocationCount = 0
    def capturedResult

    when:
    try {
      capturedResult = subject.fetchWithRetries(Map, 10, 1) {
        invocationCount++
        throw exception
      }
    } catch (e) {
      capturedResult = e
    }

    then:
    capturedResult == expectNull ? null : exception
    invocationCount == expectedInvocationCount

    where:
    exception                                         || expectNull || expectedInvocationCount
    new IllegalStateException("should not retry")     || false      || 1
    retrofitError(RetrofitError.Kind.UNEXPECTED, 400) || false      || 1
    retrofitError(RetrofitError.Kind.HTTP, 500)       || false      || 1
    retrofitError(RetrofitError.Kind.HTTP, 404)       || true       || 1      // a 404 should short-circuit and return null
    retrofitError(RetrofitError.Kind.NETWORK, 0)      || false      || 10
    retrofitError(RetrofitError.Kind.HTTP, 429)       || false      || 10
  }

  RetrofitError retrofitError(RetrofitError.Kind kind, int status) {
    return new RetrofitError(
      null, null,
      kind != RetrofitError.Kind.NETWORK ? new Response("http://blah.com", status, "", [], null) : null,
      null, null, kind, null)
  }
}
