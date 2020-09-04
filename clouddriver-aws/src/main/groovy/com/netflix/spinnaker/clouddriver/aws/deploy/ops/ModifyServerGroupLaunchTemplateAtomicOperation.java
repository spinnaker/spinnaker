/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import static com.netflix.spinnaker.clouddriver.aws.deploy.ops.actions.PrepareModifyServerGroupLaunchTemplate.PrepareModifyServerGroupLaunchTemplateCommand;

import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.actions.ModifyServerGroupLaunchTemplate;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.actions.PrepareModifyServerGroupLaunchTemplate;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.actions.UpdateAutoScalingGroup;
import com.netflix.spinnaker.clouddriver.orchestration.sagas.AbstractSagaAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.sagas.SagaAtomicOperationBridge;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlow;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import java.util.List;
import javax.annotation.Nonnull;

public class ModifyServerGroupLaunchTemplateAtomicOperation
    extends AbstractSagaAtomicOperation<ModifyServerGroupLaunchTemplateDescription, Void> {
  public ModifyServerGroupLaunchTemplateAtomicOperation(
      ModifyServerGroupLaunchTemplateDescription description) {
    super(description);
  }

  @Nonnull
  @Override
  protected SagaFlow buildSagaFlow(List priorOutputs) {
    return new SagaFlow()
        .then(PrepareModifyServerGroupLaunchTemplate.class)
        .then(ModifyServerGroupLaunchTemplate.class)
        .then(UpdateAutoScalingGroup.class);
  }

  @Override
  protected void configureSagaBridge(
      @Nonnull SagaAtomicOperationBridge.ApplyCommandWrapper.ApplyCommandWrapperBuilder builder) {
    builder.initialCommand(
        PrepareModifyServerGroupLaunchTemplateCommand.builder().description(description).build());
  }

  @Override
  protected Void parseSagaResult(@Nonnull SagaAction.Result result) {
    return null;
  }

  public static class LaunchTemplateException extends IntegrationException {
    public LaunchTemplateException(String message, Throwable cause) {
      super(message, cause);
      setRetryable(true);
    }
  }
}
