/*
 * Copyright 2020 Adevinta.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 *
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.aws;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractEditFeaturesProviderCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsProvider;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Parameters(separators = "=")
@Data
public class AwsEditFeaturesCommand
    extends AbstractEditFeaturesProviderCommand<AwsAccount, AwsProvider> {
  String shortDescription = "Edit features for AWS provider";

  @Parameter(
      names = "--cloud-formation",
      description = "Enable CloudFormation support for AWS.",
      arity = 1,
      required = true)
  private Boolean cloudFormation;

  @Parameter(
      names = "--lambda",
      description = "Enable Lambda support for AWS.",
      arity = 1,
      required = true)
  private Boolean lambda;

  protected String getProviderName() {
    return Provider.ProviderType.AWS.getName();
  }

  @Override
  protected Provider editProvider(AwsProvider provider) {
    if (provider.getLambda() != null) {
      provider.getLambda().setEnabled(lambda);
    } else {
      provider.setLambda(new AwsProvider.Lambda(lambda));
    }
    if (provider.getFeatures() != null) {
      provider.getFeatures().getCloudFormation().setEnabled(cloudFormation);
      provider.getFeatures().getLambda().setEnabled(lambda);
    } else {
      provider.setFeatures(
          new AwsProvider.Features(
              new AwsProvider.Features.CloudFormation(cloudFormation),
              new AwsProvider.Features.Lambda(lambda)));
    }
    System.out.println(provider);
    return provider;
  }
}
