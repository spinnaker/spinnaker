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

package com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit2.Response

import javax.annotation.Nonnull
import java.util.concurrent.TimeUnit

@Component
class DeleteLoadBalancerForceRefreshTask implements CloudProviderAware, RetryableTask {
  static final String REFRESH_TYPE = "LoadBalancer"

  @Autowired
  CloudDriverCacheService cacheService

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)

    String name = stage.context.loadBalancerName
    String vpcId = stage.context.vpcId ?: ''
    List<String> regions = stage.context.regions

    for (String region : regions) {
      def model = [loadBalancerName: name, region: region, account: account, vpcId: vpcId, evict: true] as Map
      Response response
      try {
        response = Retrofit2SyncCall.executeCall(cacheService.forceCacheUpdate(cloudProvider, REFRESH_TYPE, model))
      } catch (SpinnakerNetworkException | IOException e) {
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }

      int statusCode = response.code()
      if (statusCode == HttpURLConnection.HTTP_OK) {
        continue
      }

      if (statusCode == HttpURLConnection.HTTP_ACCEPTED || statusCode == 429 || statusCode >= 500) {
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }

      throw new IllegalStateException(
        "Force cache update for load balancer '${name}' in ${region} (${account}) failed with status ${statusCode}"
      )
    }

    return TaskResult.ofStatus(ExecutionStatus.SUCCEEDED)
  }

  @Override
  long getTimeout() {
    return TimeUnit.MINUTES.toMillis(10)
  }

  @Override
  long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(5)
  }
}
