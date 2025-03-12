/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.security;

import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.AccountsConfiguration;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.names.EcsResource;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import com.netflix.spinnaker.moniker.Namer;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;

@AllArgsConstructor
public class EcsCredentialsParser<T extends NetflixECSCredentials>
    implements CredentialsParser<ECSCredentialsConfig.Account, NetflixECSCredentials> {

  private final ECSCredentialsConfig ecsCredentialsConfig;

  private final CompositeCredentialsRepository<AccountCredentials> compositeCredentialsRepository;

  @Qualifier("amazonCredentialsParser")
  private final CredentialsParser<AccountsConfiguration.Account, NetflixAmazonCredentials>
      amazonCredentialsParser;

  private final NamerRegistry namerRegistry;

  @Override
  public NetflixECSCredentials parse(ECSCredentialsConfig.@NotNull Account accountDefinition) {
    NetflixAmazonCredentials netflixAmazonCredentials =
        (NetflixAmazonCredentials)
            compositeCredentialsRepository.getCredentials(
                accountDefinition.getAwsAccount(), AmazonCloudProvider.ID);

    AccountsConfiguration.Account account =
        EcsAccountBuilder.build(
            netflixAmazonCredentials, accountDefinition.getName(), EcsProvider.NAME);
    NetflixECSCredentials netflixECSCredentials =
        new NetflixAssumeRoleEcsCredentials(
            (NetflixAssumeRoleAmazonCredentials) amazonCredentialsParser.parse(account),
            accountDefinition.getAwsAccount());

    // If no naming strategy is set at the account or provider
    // level then the NamerRegistry will fallback to Frigga
    String namingStrategy =
        StringUtils.firstNonBlank(
            accountDefinition.getNamingStrategy(), ecsCredentialsConfig.getDefaultNamingStrategy());

    Namer<EcsResource> namer = namerRegistry.getNamingStrategy(namingStrategy);
    NamerRegistry.lookup()
        .withProvider(EcsCloudProvider.ID)
        .withAccount(account.getName())
        .setNamer(EcsResource.class, namer);

    return netflixECSCredentials;
  }
}
