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
import com.netflix.spinnaker.kork.web.selector.v2.SelectableService
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.bakery.BakerySelector
import com.netflix.spinnaker.orca.bakery.api.Bake
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import retrofit.client.Response
import rx.Observable
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static java.net.HttpURLConnection.HTTP_NOT_FOUND

class CompletedBakeTaskSpec extends Specification {

  @Subject task = new CompletedBakeTask()

  @Shared Execution pipeline = pipeline()

  @Shared notFoundError = RetrofitError.httpError(
    null,
    new Response("http://bakery", HTTP_NOT_FOUND, "Not Found", [], null),
    null,
    null
  )

  def "finds the AMI and artifact created by a bake"() {
    given:
    def bakery = Stub(BakeryService) {
      lookupBake(region, bakeId) >> Observable.from(new Bake(id: bakeId, ami: ami, artifact: artifact))
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
    def stage = new Stage(pipeline, "bake", [region: region, status: new BakeStatus(resourceId: bakeId)])

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
    artifact = new Artifact(reference: ami)
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
    def stage = new Stage(pipeline, "bake", [region: region, status: new BakeStatus(resourceId: bakeId)])

    when:
    task.execute(stage)

    then:
    1 * task.bakerySelector.select(_) >> selectedBakeryService
    thrown(RetrofitError)

    where:
    region = "us-west-1"
    bakeId = "b-5af233wjj78mwt2f420wt8ey3w"
  }

}
