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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.bakery.api.Bake
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.RetrofitError
import retrofit.client.Response
import rx.Observable
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import static java.net.HttpURLConnection.HTTP_NOT_FOUND

class CompletedBakeTaskSpec extends Specification {

  @Subject task = new CompletedBakeTask()

  @Shared Pipeline pipeline = new Pipeline()

  @Shared notFoundError = RetrofitError.httpError(
    null,
    new Response("http://bakery", HTTP_NOT_FOUND, "Not Found", [], null),
    null,
    null
  )

  def "finds the AMI created by a bake"() {
    given:
    task.bakery = Stub(BakeryService) {
      lookupBake(region, bakeId) >> Observable.from(new Bake(id: bakeId, ami: ami))
    }

    and:
    def stage = new PipelineStage(pipeline, "bake", [region: region, status: new BakeStatus(resourceId: bakeId)])

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.outputs.ami == ami

    where:
    region = "us-west-1"
    bakeId = "b-5af233wjj78mwt2f420wt8ey3w"
    ami = "ami-280c3b6d"
  }

  def "fails if the bake is not found"() {
    given:
    task.bakery = Stub(BakeryService) {
      lookupBake(*_) >> { throw notFoundError }
    }

    and:
    def stage = new PipelineStage(pipeline, "bake", [region: region, status: new BakeStatus(resourceId: bakeId)])

    when:
    task.execute(stage)

    then:
    thrown(RetrofitError)

    where:
    region = "us-west-1"
    bakeId = "b-5af233wjj78mwt2f420wt8ey3w"
  }

}
