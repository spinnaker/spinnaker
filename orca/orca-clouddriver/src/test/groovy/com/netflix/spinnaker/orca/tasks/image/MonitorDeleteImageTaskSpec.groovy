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

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.tasks.image.MonitorDeleteImageTask
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import spock.lang.Specification

class MonitorDeleteImageTaskSpec extends Specification {

  CloudDriverService cloudDriverService = Mock()

  def "should monitor image deletion"() {
    given:
    def context = [
      cloudProvider: "aws",
      credentials: "test",
      region: "us-east-1",
      imageIds: ["ami-123", "ami-321"]
    ]

    1 * cloudDriverService.getByAmiId("aws", "test", "us-east-1", "ami-123") >> { error(404) }
    1 * cloudDriverService.getByAmiId("aws", "test", "us-east-1",  "ami-321") >> { error(404) }

    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "deleteImage", context)
    def task = new MonitorDeleteImageTask(cloudDriverService)

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

    1 * cloudDriverService.getByAmiId("aws", "test", "us-east-1", "ami-123") >> { error(404) }
    1 * cloudDriverService.getByAmiId("aws", "test", "us-east-1",  "ami-321") >> { error(500) }

    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "deleteImage", context)
    def task = new MonitorDeleteImageTask(cloudDriverService)

    expect:
    task.execute(stage).status == ExecutionStatus.RUNNING
  }

  private void error(int status) {
    String url = "https://clouddriver";

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

    throw new SpinnakerHttpException(retrofit2Response, retrofit)
  }
}
