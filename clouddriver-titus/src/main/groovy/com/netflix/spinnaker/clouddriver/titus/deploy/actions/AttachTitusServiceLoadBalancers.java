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
package com.netflix.spinnaker.clouddriver.titus.deploy.actions;

import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper;
import com.netflix.spinnaker.clouddriver.saga.SagaCommand;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.client.TitusLoadBalancerClient;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusLoadBalancerAttached;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AttachTitusServiceLoadBalancers
    implements SagaAction<AttachTitusServiceLoadBalancers.AttachTitusServiceLoadBalancersCommand> {

  private final TitusClientProvider titusClientProvider;

  @Autowired
  public AttachTitusServiceLoadBalancers(TitusClientProvider titusClientProvider) {
    this.titusClientProvider = titusClientProvider;
  }

  @NotNull
  @Override
  public Result apply(@NotNull AttachTitusServiceLoadBalancersCommand command, @NotNull Saga saga) {
    final TitusDeployDescription description = command.description;

    TitusLoadBalancerClient loadBalancerClient =
        titusClientProvider.getTitusLoadBalancerClient(
            description.getCredentials(), description.getRegion());
    if (loadBalancerClient == null) {
      // TODO(rz): This definitely doesn't seem like something to casually skip?
      saga.log("Unable to create load balancing client in target account/region");
      return new Result();
    }

    TargetGroupLookupHelper.TargetGroupLookupResult targetGroups =
        command.getTargetGroupLookupResult();

    if (targetGroups != null) {
      String jobUri = command.getJobUri();

      targetGroups
          .getTargetGroupARNs()
          .forEach(
              targetGroupArn -> {
                loadBalancerClient.addLoadBalancer(jobUri, targetGroupArn);
                saga.log("Attached %s to %s", targetGroupArn, jobUri);
                saga.addEvent(new TitusLoadBalancerAttached(jobUri, targetGroupArn));
              });

      saga.log("Load balancers applied");
    }

    return new Result();
  }

  @EqualsAndHashCode(callSuper = true)
  @Getter
  public static class AttachTitusServiceLoadBalancersCommand extends SagaCommand {
    @Nonnull private final TitusDeployDescription description;
    @Nonnull private final String jobUri;
    @Nullable private final TargetGroupLookupHelper.TargetGroupLookupResult targetGroupLookupResult;

    public AttachTitusServiceLoadBalancersCommand(
        @Nonnull TitusDeployDescription description,
        @Nonnull String jobUri,
        @Nullable TargetGroupLookupHelper.TargetGroupLookupResult targetGroupLookupResult) {
      super();
      this.description = description;
      this.jobUri = jobUri;
      this.targetGroupLookupResult = targetGroupLookupResult;
    }
  }
}
