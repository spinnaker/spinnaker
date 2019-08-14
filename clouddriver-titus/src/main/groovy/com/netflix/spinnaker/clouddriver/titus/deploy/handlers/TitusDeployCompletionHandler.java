/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.titus.deploy.handlers;

import com.netflix.spinnaker.clouddriver.saga.flow.SagaCompletionHandler;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusUtils;
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.SubmitTitusJob;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusJobSubmitted;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TitusDeployCompletionHandler implements SagaCompletionHandler<TitusDeploymentResult> {

  private final AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  public TitusDeployCompletionHandler(AccountCredentialsProvider accountCredentialsProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
  }

  @Nullable
  @Override
  public TitusDeploymentResult handle(@NotNull Saga completedSaga) {
    final TitusDeployDescription description =
        completedSaga.getEvent(SubmitTitusJob.SubmitTitusJobCommand.class).getDescription();

    return TitusDeploymentResult.from(
        description,
        completedSaga.getEvent(TitusJobSubmitted.class),
        completedSaga.getLogs(),
        TitusUtils.getAccountId(accountCredentialsProvider, description.getAccount()));
  }
}
