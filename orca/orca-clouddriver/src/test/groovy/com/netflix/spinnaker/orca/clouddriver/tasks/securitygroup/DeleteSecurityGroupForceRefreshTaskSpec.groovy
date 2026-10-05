/*
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup

import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class DeleteSecurityGroupForceRefreshTaskSpec extends Specification {
  @Subject task = new DeleteSecurityGroupForceRefreshTask()
  def stage = stage()

  def config = [
    cloudProvider     : 'gce',
    regions           : ['us-west-1', 'us-east-1'],
    credentials       : 'fzlem',
    vpcId             : 'vpc1',
    securityGroupName : 'foo'
  ]

  def setup() {
    stage.context.putAll(config)
    task.cacheService = Mock(CloudDriverCacheService)
  }

  void "should force cache refresh the deleted security group in every region"() {
    given:
    def westCall = refreshResponse()
    def eastCall = refreshResponse()

    when:
    def result = task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate('gce', DeleteSecurityGroupForceRefreshTask.REFRESH_TYPE, refreshBody('us-west-1')) >> westCall
    1 * task.cacheService.forceCacheUpdate('gce', DeleteSecurityGroupForceRefreshTask.REFRESH_TYPE, refreshBody('us-east-1')) >> eastCall
    0 * task.cacheService._
    westCall.isExecuted()
    eastCall.isExecuted()
    result.status == SUCCEEDED
  }

  void "should still refresh the remaining regions when one refresh fails"() {
    given:
    def failedCall = Calls.<ResponseBody>failure(new IOException("clouddriver unavailable"))
    def eastCall = refreshResponse()

    when:
    def result = task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate('gce', DeleteSecurityGroupForceRefreshTask.REFRESH_TYPE, refreshBody('us-west-1')) >> failedCall
    1 * task.cacheService.forceCacheUpdate('gce', DeleteSecurityGroupForceRefreshTask.REFRESH_TYPE, refreshBody('us-east-1')) >> eastCall
    failedCall.isExecuted()
    eastCall.isExecuted()
    result.status == SUCCEEDED
  }

  private Map refreshBody(String region) {
    [securityGroupName: config.securityGroupName, vpcId: config.vpcId, region: region, account: config.credentials, evict: true]
  }

  private static Call<ResponseBody> refreshResponse() {
    Calls.response(Response.success(200, ResponseBody.create(MediaType.parse("application/json"), "{}")))
  }
}
