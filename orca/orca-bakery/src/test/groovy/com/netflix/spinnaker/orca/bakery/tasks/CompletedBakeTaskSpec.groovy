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

package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.web.selector.v2.SelectableService
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.bakery.BakerySelector
import com.netflix.spinnaker.orca.bakery.api.Bake
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.mock.Calls
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static java.net.HttpURLConnection.HTTP_NOT_FOUND

class CompletedBakeTaskSpec extends Specification {

  @Subject task = new CompletedBakeTask()

  @Shared PipelineExecutionImpl pipeline = pipeline()

  @Shared notFoundError = makeSpinnakerHttpException(HTTP_NOT_FOUND)

  def "finds the AMI and artifact created by a bake"() {
    given:
    def bakery = Stub(BakeryService) {
      lookupBake(region, bakeId) >> Calls.response(new Bake(id: bakeId, ami: ami, artifact: artifact))
    }

    task.bakerySelector = Mock(BakerySelector)
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    and:
    def stage = new StageExecutionImpl(pipeline, "bake", [region: region, status: new BakeStatus(resourceId: bakeId)])

    when:
    def result = task.execute(stage)

    then:
    1 * task.bakerySelector.select(_) >> selectedBakeryService
    result.status == ExecutionStatus.SUCCEEDED
    result.context.ami == ami
    result.context.artifacts[0].reference == ami

    where:
    region = "us-west-1"
    bakeId = "b-5af233wjj78mwt2f420wt8ey3w"
    ami = "ami-280c3b6d"
    artifact = Artifact.builder().reference(ami).build()
  }

  def "fails if the bake is not found"() {
    given:
    def bakery = Stub(BakeryService) {
      lookupBake(*_) >> { throw notFoundError }
    }

    task.bakerySelector = Mock(BakerySelector)
    def selectedBakeryService = Stub(SelectableService.SelectedService) {
      getService() >> bakery
      getConfig() >> [
        extractBuildDetails: false,
        allowMissingPackageInstallation: false,
        roscoApisEnabled: false
      ]
    }

    and:
    def stage = new StageExecutionImpl(pipeline, "bake", [region: region, status: new BakeStatus(resourceId: bakeId)])

    when:
    task.execute(stage)

    then:
    1 * task.bakerySelector.select(_) >> selectedBakeryService
    thrown(SpinnakerHttpException)

    where:
    region = "us-west-1"
    bakeId = "b-5af233wjj78mwt2f420wt8ey3w"
  }

  static SpinnakerHttpException makeSpinnakerHttpException(int status) {

    String url = "https://bakery";

    retrofit2.Response retrofit2Response =
        retrofit2.Response.error(
            status,
            ResponseBody.create(
                MediaType.parse("application/json"), "{ \"message\": \"arbitrary message\" }"))

    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build()

    return new SpinnakerHttpException(retrofit2Response, retrofit)}

}
