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
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsAccountMapper;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Lazy;

@AllArgsConstructor
public class EcsCredentialsParser<T extends NetflixECSCredentials>
    implements CredentialsParser<ECSCredentialsConfig.Account, NetflixECSCredentials> {

  private final CompositeCredentialsRepository<AccountCredentials> compositeCredentialsRepository;
  @Lazy private final EcsAccountMapper ecsAccountMapper;
  private final CredentialsParser<CredentialsConfig.Account, NetflixAmazonCredentials>
      AmazonCredentialsParser;

  @Override
  public NetflixECSCredentials parse(ECSCredentialsConfig.@NotNull Account accountDefinition) {
    NetflixAmazonCredentials netflixAmazonCredentials =
        (NetflixAmazonCredentials)
            compositeCredentialsRepository.getCredentials(
                accountDefinition.getAwsAccount(), AmazonCloudProvider.ID);

    CredentialsConfig.Account account =
        EcsAccountBuilder.build(
            netflixAmazonCredentials, accountDefinition.getName(), EcsProvider.NAME);
    NetflixECSCredentials netflixECSCredentials =
        new NetflixAssumeRoleEcsCredentials(
            (NetflixAssumeRoleAmazonCredentials) AmazonCredentialsParser.parse(account),
            accountDefinition.getAwsAccount());
    //    ecsAccountMapper.addMapEntry(accountDefinition); To be implemented once
    // CredentialsRepository is implemented.
    return netflixECSCredentials;
  }
}
