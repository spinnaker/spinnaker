/*
 *  Copyright 2019 Pivotal Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.servicebroker;

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import java.util.Map;
import javax.annotation.Nonnull;

public abstract class AbstractWaitForServiceTask implements CloudProviderAware, RetryableTask {
  protected OortService oortService;

  public AbstractWaitForServiceTask(OortService oortService) {
    this.oortService = oortService;
  }

  @Override
  public long getBackoffPeriod() {
    return 10 * 1000L;
  }

  @Override
  public long getTimeout() {
    return 30 * 60 * 1000L;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    String cloudProvider = getCloudProvider(stage);
    String account = stage.mapTo("/service.account", String.class);
    String region = stage.mapTo("/service.region", String.class);
    String serviceInstanceName = stage.mapTo("/service.instance.name", String.class);

    return TaskResult.ofStatus(
        oortStatusToTaskStatus(
            Retrofit2SyncCall.execute(
                oortService.getServiceInstance(
                    account, cloudProvider, region, serviceInstanceName))));
  }

  protected abstract ExecutionStatus oortStatusToTaskStatus(Map m);
}
