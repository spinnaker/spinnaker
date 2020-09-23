/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.deploy.handlers;

import com.netflix.spinnaker.clouddriver.orchestration.events.DeleteServerGroupEvent;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaCompletionHandler;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider;
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.DestroyTitusJob;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DestroyTitusJobDescription;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.stereotype.Component;

@Component
public class DestroyTitusJobCompletionHandler
    implements SagaCompletionHandler<DeleteServerGroupEvent> {

  @Nullable
  @Override
  public DeleteServerGroupEvent handle(@Nonnull Saga completedSaga) {
    final DestroyTitusJobDescription description =
        completedSaga.getEvent(DestroyTitusJob.DestroyTitusJobCommand.class).getDescription();

    return new DeleteServerGroupEvent(
        TitusCloudProvider.ID,
        // titus entity tags are created using the account name (and not the accountId)
        description.getAccount(),
        description.getRegion(),
        description.getServerGroupName());
  }
}
