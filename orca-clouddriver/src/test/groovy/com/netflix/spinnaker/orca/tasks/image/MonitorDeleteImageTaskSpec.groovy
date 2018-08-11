/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.tasks.image

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.tasks.image.MonitorDeleteImageTask
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Specification

class MonitorDeleteImageTaskSpec extends Specification {
  def "should monitor image deletion"() {
    given:
    def context = [
      cloudProvider: "aws",
      credentials: "test",
      region: "us-east-1",
      imageIds: ["ami-123", "ami-321"]
    ]

    def oortService = Mock(OortService) {
      1 * getByAmiId("aws", "test", "us-east-1", "ami-123") >> { error(404) }
      1 * getByAmiId("aws", "test", "us-east-1",  "ami-321") >> { error(404) }
    }

    def stage = new Stage(Execution.newPipeline("orca"), "deleteImage", context)
    def task = new MonitorDeleteImageTask(oortService)

    expect:
    task.execute(stage).status == ExecutionStatus.SUCCEEDED
  }

  def "should keep running on 503"() {
    given:
    def context = [
      cloudProvider: "aws",
      credentials: "test",
      region: "us-east-1",
      imageIds: ["ami-123", "ami-321"]
    ]

    def oortService = Mock(OortService) {
      1 * getByAmiId("aws", "test", "us-east-1", "ami-123") >> { error(404) }
      1 * getByAmiId("aws", "test", "us-east-1",  "ami-321") >> { error(500) }
    }

    def stage = new Stage(Execution.newPipeline("orca"), "deleteImage", context)
    def task = new MonitorDeleteImageTask(oortService)

    expect:
    task.execute(stage).status == ExecutionStatus.RUNNING
  }

  private void error(int status) {
    throw RetrofitError.httpError(
      null,
      new Response("http://...", status, "...", [], null),
      null,
      null
    )
  }
}
