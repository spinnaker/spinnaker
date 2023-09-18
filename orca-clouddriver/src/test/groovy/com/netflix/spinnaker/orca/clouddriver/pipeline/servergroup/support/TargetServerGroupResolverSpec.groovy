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
import com.google.gson.Gson
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerConversionException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.client.Client
import retrofit.client.Response
import retrofit.converter.ConversionException
import retrofit.converter.GsonConverter
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage
import static org.spockframework.util.Assert.fail

class TargetServerGroupResolverSpec extends Specification {

  private static final GsonConverter gsonConverter = new GsonConverter(new Gson())

  private static final SpinnakerRetrofitErrorHandler spinnakerRetrofitErrorHandler = SpinnakerRetrofitErrorHandler.newInstance()

  OortService oort = Mock(OortService)
  ObjectMapper mapper = new ObjectMapper()
  RetrySupport retrySupport = Spy(RetrySupport) {
    _ * sleep(_) >> { /* do nothing */ }
  }

  @Subject
  TargetServerGroupResolver subject = new TargetServerGroupResolver(
    oortService: oort,
    mapper: mapper,
    retrySupport: retrySupport
  )

  @Unroll
  def "resolveByParams(target) throws a ConversionError with an invalid response (#description)"() {
    given:
    Client client = Mock(Client)

    OortService oortService =
        new RestAdapter.Builder()
            .setEndpoint("clouddriver")
            .setClient(client)
            .setErrorHandler(spinnakerRetrofitErrorHandler)
            .build()
            .create(OortService.class);

    TargetServerGroupResolver targetServerGroupResolver = new TargetServerGroupResolver(
      oortService: oortService,
      mapper: mapper,
      retrySupport: retrySupport
    )

    when:
    def tsgs = targetServerGroupResolver.resolveByParams(new TargetServerGroup.Params(
      cloudProvider: "abc",
      cluster: "test-app",
      credentials: "testCreds",
      locations: [new Location(type: Location.Type.REGION, value: "north-pole")],
      target: TargetServerGroup.Params.Target.current_asg,
    ))

    then:
    1 * client.execute(_) >> new Response("clouddriver", 200, 'ok', [], new TypedString(responseBody))

    thrown(SpinnakerConversionException)

    where:
    // Another kind of invalid is something that deserializes into a map, but
    // from which it's not possible to construct a TargetServerGroup.  That's a
    // different test though, as it doesn't generate a conversion error.
    description         | responseBody
    "non-json response" | "non-json response"
    "not a map"         | "[ \"list-element\": 5 ]"
  }

  @Unroll
  def "resolveByParams(serverGroupName) throws a ConversionError with an invalid response (#description)"() {
    given:
    Client client = Mock(Client)

    OortService oortService =
        new RestAdapter.Builder()
            .setEndpoint("clouddriver")
            .setClient(client)
            .setErrorHandler(spinnakerRetrofitErrorHandler)
            .build()
            .create(OortService.class);

    TargetServerGroupResolver targetServerGroupResolver = new TargetServerGroupResolver(
      oortService: oortService,
      mapper: mapper,
      retrySupport: retrySupport
    )

    when:
    def tsgs = targetServerGroupResolver.resolveByParams(new TargetServerGroup.Params(
      cloudProvider: "gce",
      serverGroupName: "test-app-v010",
      credentials: "testCreds",
      locations: [new Location(type: Location.Type.REGION, value: "north-pole")]
    ))

    then:
    1 * client.execute(_) >> new Response("clouddriver", 200, 'ok', [], new TypedString(responseBody))

    thrown(SpinnakerConversionException)

    where:
    // Another kind of invalid is something that deserializes into a list, but
    // from which it's not possible to construct a TargetServerGroup from the
    // appropriate element.  That's a different test though, as it doesn't
    // generate a conversion error.
    description         | responseBody
    "non-json response" | "non-json response"
    "not a list"        | "{ \"some-property\": 5 }"
  }

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
      new ServerGroup([
        name  : "test-app-v010",
        region: "north-pole"
      ])
    tsgs.size() == 1
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
    1 * oort.getServerGroupsFromClusterTyped("test", "testCreds", "test-app", "test-app-v010", "gce") >>
      [new ServerGroup([name  : "test-app-v010",
                       region: "north-pole",
                       type  : "gce",
                       ])]
    tsgs.size() == 1
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

    StageExecutionImpl commonParent
    StageExecutionImpl dtsgStage
    StageExecutionImpl stageLookingForRefs
    pipeline {
      commonParent = stage {
        refId = "1"
        dtsgStage = stage {
          refId = "1<1"
          type = DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE
          context = [targetReferences: [decoy, want]]
        }
        stage {
          refId = "1<2"
          requisiteStageRefIds = ["1<1"]
          stageLookingForRefs = stage {
            refId = "1<2<1"
            context = [region: "north-pole"]
          }
        }
      }
    }

    when:
    def got = TargetServerGroupResolver.fromPreviousStage(stageLookingForRefs)

    then:
    got == want

    when:
    stageLookingForRefs.context = [region: "east-1"] // doesn't exist.
    TargetServerGroupResolver.fromPreviousStage(stageLookingForRefs)

    then:
    thrown(TargetServerGroup.NotFoundException)
  }

  def "should resolve target refs from directly preceding DTSG stage if there are more than one"() {
    setup:
    TargetServerGroup want = new TargetServerGroup(name: "i-want-this-one", region: "us-west-2")
    TargetServerGroup decoy = new TargetServerGroup(name: "not-this-one", region: "us-west-2")

    def pipeline = pipeline {
      stage {
        refId = "1"
        stage {
          refId = "1<1"
          type = DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE
          context = [targetReferences: [decoy]]
        }
        stage {
          refId = "1<2"
          requisiteStageRefIds = ["1<1"]
          context = [region: "us-west-2"]
        }
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
        stage {
          refId = "2<1"
          type = DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE
          context = [targetReferences: [want]]
        }
        stage {
          refId = "2<2"
          requisiteStageRefIds = ["2<1"]
          context = [region: "us-west-2"]
        }
      }
    }
    def stageLookingForRefs = pipeline.stageByRef("2<2")

    when:
    def got = TargetServerGroupResolver.fromPreviousStage(stageLookingForRefs)

    then:
    got == want
  }

  @Unroll
  def "should retry on non 404 errors"() {
    given:
    def invocationCount = 0
    def capturedResult

    when:
    try {
      capturedResult = subject.fetchWithRetries {
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
    exception                                                                       || expectNull || expectedInvocationCount
    new IllegalStateException("should retry")                                       || false      || TargetServerGroupResolver.NUM_RETRIES
    makeSpinnakerServerException(retrofitError(RetrofitError.Kind.UNEXPECTED, 400)) || false      || TargetServerGroupResolver.NUM_RETRIES
    makeSpinnakerServerException(retrofitError(RetrofitError.Kind.HTTP, 500))       || false      || TargetServerGroupResolver.NUM_RETRIES
    makeSpinnakerServerException(retrofitError(RetrofitError.Kind.HTTP, 404))       || true       || 1      // a 404 should short-circuit and return null
    makeSpinnakerServerException(retrofitError(RetrofitError.Kind.NETWORK, 0))      || false      || TargetServerGroupResolver.NUM_RETRIES
    makeSpinnakerServerException(retrofitError(RetrofitError.Kind.HTTP, 429))       || false      || TargetServerGroupResolver.NUM_RETRIES
  }

  Throwable makeSpinnakerServerException(RetrofitError retrofitError) {
    return spinnakerRetrofitErrorHandler.handleError(retrofitError)
  }

  RetrofitError retrofitError(RetrofitError.Kind kind, int status) {
    String url = "http://some-url"
    switch (kind) {
        case RetrofitError.Kind.NETWORK:
          return RetrofitError.networkError(url, new IOException("arbitrary exception"))
        case RetrofitError.Kind.HTTP:
        Response response =
          new Response(
            url,
            status,
            "arbitrary reason",
            List.of(),
            new TypedString("{ message: \"arbitrary message\" }"));
          return RetrofitError.httpError(url, response, gsonConverter, String.class)
        case RetrofitError.Kind.UNEXPECTED:
          return RetrofitError.unexpectedError(url, new RuntimeException("arbitrary exception"));
        default:
          fail("can't make RetrofitError of unknown kind: ${kind}")
    }
  }
}
