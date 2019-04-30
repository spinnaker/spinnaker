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

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

import javax.annotation.Nonnull;
import java.util.Map;

public abstract class AbstractWaitForServiceTask extends AbstractCloudProviderAwareTask implements RetryableTask {
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
  public TaskResult execute(@Nonnull Stage stage) {
    String cloudProvider = getCloudProvider(stage);
    String account = stage.mapTo("/service.account", String.class);
    String region = stage.mapTo("/service.region", String.class);
    String serviceInstanceName = stage.mapTo("/service.instance.name", String.class);

    return TaskResult.ofStatus(oortStatusToTaskStatus(oortService.getServiceInstance(account, cloudProvider, region, serviceInstanceName)));
  }

  abstract protected ExecutionStatus oortStatusToTaskStatus(Map m);
}
