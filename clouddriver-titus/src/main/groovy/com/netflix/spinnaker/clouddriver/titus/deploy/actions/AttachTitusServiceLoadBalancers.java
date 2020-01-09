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

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper;
import com.netflix.spinnaker.clouddriver.event.EventMetadata;
import com.netflix.spinnaker.clouddriver.saga.SagaCommand;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.client.TitusLoadBalancerClient;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusLoadBalancerAttached;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AttachTitusServiceLoadBalancers extends AbstractTitusDeployAction
    implements SagaAction<AttachTitusServiceLoadBalancers.AttachTitusServiceLoadBalancersCommand> {

  private final TitusClientProvider titusClientProvider;

  @Autowired
  public AttachTitusServiceLoadBalancers(
      AccountCredentialsRepository accountCredentialsRepository,
      TitusClientProvider titusClientProvider,
      TitusClientProvider titusClientProvider1) {
    super(accountCredentialsRepository, titusClientProvider);
    this.titusClientProvider = titusClientProvider1;
  }

  @Nonnull
  @Override
  public Result apply(@Nonnull AttachTitusServiceLoadBalancersCommand command, @Nonnull Saga saga) {
    final TitusDeployDescription description = command.description;

    prepareDeployDescription(description);

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
                saga.addEvent(
                    TitusLoadBalancerAttached.builder()
                        .jobUri(jobUri)
                        .targetGroupArn(targetGroupArn)
                        .build());
              });

      saga.log("Load balancers applied");
    }

    return new Result();
  }

  @Builder(builderClassName = "AttachTitusServiceLoadBalancersCommandBuilder", toBuilder = true)
  @JsonDeserialize(
      builder =
          AttachTitusServiceLoadBalancersCommand.AttachTitusServiceLoadBalancersCommandBuilder
              .class)
  @JsonTypeName("attachTitusServiceLoadBalancersCommand")
  @Value
  public static class AttachTitusServiceLoadBalancersCommand implements SagaCommand {
    @Nonnull private TitusDeployDescription description;
    @Nonnull private String jobUri;
    @Nullable private TargetGroupLookupHelper.TargetGroupLookupResult targetGroupLookupResult;
    @NonFinal private EventMetadata metadata;

    @Override
    public void setMetadata(EventMetadata metadata) {
      this.metadata = metadata;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class AttachTitusServiceLoadBalancersCommandBuilder {}
  }
}
