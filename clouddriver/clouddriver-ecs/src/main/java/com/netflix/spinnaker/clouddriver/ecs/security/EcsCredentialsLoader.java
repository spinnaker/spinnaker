/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.security;

import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.definition.BasicCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * ECSCredentialsConfig.Account only identifies which AWS account an ECS account is backed by; it
 * doesn't change when that AWS account's own definition changes (e.g. a new region is added).
 * BasicCredentialsLoader only re-parses a definition when it differs from what it last loaded, so
 * without this check an ECS account would never pick up changes to its underlying AWS account until
 * clouddriver is restarted. This loader additionally tracks the AWS credentials instance backing
 * each ECS account and forces a re-parse whenever that instance has been replaced.
 */
@Slf4j
public class EcsCredentialsLoader
    extends BasicCredentialsLoader<ECSCredentialsConfig.Account, NetflixECSCredentials> {

  private final CompositeCredentialsRepository<AccountCredentials> compositeCredentialsRepository;

  // Tracks the AWS credentials instance that backed each ECS account the last time it was
  // parsed, so a change to the underlying AWS account can be detected even though the ECS
  // account definition itself hasn't changed.
  private final Map<String, AccountCredentials> loadedAwsCredentials = new ConcurrentHashMap<>();

  public EcsCredentialsLoader(
      CredentialsDefinitionSource<ECSCredentialsConfig.Account> definitionSource,
      CredentialsParser<ECSCredentialsConfig.Account, NetflixECSCredentials> parser,
      CredentialsRepository<NetflixECSCredentials> credentialsRepository,
      CompositeCredentialsRepository<AccountCredentials> compositeCredentialsRepository) {
    super(definitionSource, parser, credentialsRepository);
    this.compositeCredentialsRepository = compositeCredentialsRepository;
  }

  @Override
  protected void parse(Collection<ECSCredentialsConfig.Account> definitions) {
    for (ECSCredentialsConfig.Account definition : definitions) {
      AccountCredentials awsCredentials;
      try {
        awsCredentials =
            compositeCredentialsRepository.getCredentials(
                definition.getAwsAccount(), AmazonCloudProvider.ID);
      } catch (SpinnakerException e) {
        // The referenced AWS account isn't loaded (yet); let the normal parse flow below
        // surface and retry this on the next load.
        continue;
      }

      AccountCredentials previouslyLoadedAwsCredentials =
          loadedAwsCredentials.put(definition.getName(), awsCredentials);
      if (previouslyLoadedAwsCredentials != null
          && previouslyLoadedAwsCredentials != awsCredentials) {
        log.info(
            "AWS account '{}' backing ECS account '{}' changed; forcing a re-parse",
            definition.getAwsAccount(),
            definition.getName());
        loadedDefinitions.remove(definition.getName());
      }
    }

    super.parse(definitions);
  }
}
