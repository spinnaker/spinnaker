/*
 * Copyright 2017 Google, Inc.
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
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractEditProviderCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsProvider;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Parameters(separators = "=")
@Data
public class AwsEditProviderCommand extends AbstractEditProviderCommand<AwsAccount, AwsProvider> {
  String shortDescription = "Set provider-wide properties for the AWS provider";

  String longDescription =
      "The AWS provider requires a central \"Managing Account\" to authenticate on behalf of other "
          + "AWS accounts, or act as your sole, credential-based account. Since this configuration, as well as some defaults, span "
          + "all AWS accounts, it is generally required to edit the AWS provider using this command.";

  @Parameter(
      names = "--access-key-id",
      description =
          AwsCommandProperties.ACCESS_KEY_ID_DESCRIPTION
              + ". Note that if you are baking AMI's via Rosco, you may "
              + "also need to set the access key on the AWS bakery default options.")
  private String accessKeyId;

  @Parameter(
      names = "--secret-access-key",
      description =
          AwsCommandProperties.SECRET_KEY_DESCRIPTION
              + ". Note that if you are baking AMI's via Rosco, you may "
              + "also need to set the secret key on the AWS bakery default options.",
      password = true)
  private String secretAccessKey;

  protected String getProviderName() {
    return Provider.ProviderType.AWS.getName();
  }

  @Override
  protected Provider editProvider(AwsProvider provider) {
    provider.setAccessKeyId(isSet(accessKeyId) ? accessKeyId : provider.getAccessKeyId());
    provider.setSecretAccessKey(
        isSet(secretAccessKey) ? secretAccessKey : provider.getSecretAccessKey());
    return provider;
  }
}
