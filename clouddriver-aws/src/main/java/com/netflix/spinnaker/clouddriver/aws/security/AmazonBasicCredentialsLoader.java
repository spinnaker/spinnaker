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
 *
 */

package com.netflix.spinnaker.clouddriver.aws.security;

import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.util.CollectionUtils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.spinnaker.clouddriver.aws.security.config.AccountsConfiguration;
import com.netflix.spinnaker.clouddriver.aws.security.config.AmazonCredentialsParser;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.credentials.Credentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.definition.BasicCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class AmazonBasicCredentialsLoader<
        T extends AccountsConfiguration.Account, U extends NetflixAmazonCredentials>
    extends BasicCredentialsLoader<T, U> {
  protected final CredentialsConfig credentialsConfig;
  protected final AccountsConfiguration accountsConfig;
  protected final DefaultAccountConfigurationProperties defaultAccountConfigurationProperties;
  protected String defaultEnvironment;
  protected String defaultAccountType;

  public AmazonBasicCredentialsLoader(
      CredentialsDefinitionSource<T> definitionSource,
      CredentialsParser<T, U> parser,
      CredentialsRepository<U> credentialsRepository,
      CredentialsConfig credentialsConfig,
      AccountsConfiguration accountsConfig,
      DefaultAccountConfigurationProperties defaultAccountConfigurationProperties) {
    super(definitionSource, parser, credentialsRepository);
    this.credentialsConfig = credentialsConfig;
    this.accountsConfig = accountsConfig;
    this.defaultAccountConfigurationProperties = defaultAccountConfigurationProperties;
    this.defaultEnvironment =
        defaultAccountConfigurationProperties.getEnvironment() != null
            ? defaultAccountConfigurationProperties.getEnvironment()
            : defaultAccountConfigurationProperties.getEnv();
    this.defaultAccountType =
        defaultAccountConfigurationProperties.getAccountType() != null
            ? defaultAccountConfigurationProperties.getAccountType()
            : defaultAccountConfigurationProperties.getEnv();
    if (!StringUtils.isEmpty(credentialsConfig.getAccessKeyId())) {
      System.setProperty(
          SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY, credentialsConfig.getAccessKeyId());
    }
    if (!StringUtils.isEmpty(credentialsConfig.getSecretAccessKey())) {
      System.setProperty(
          SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY,
          credentialsConfig.getSecretAccessKey());
    }
  }

  @Override
  public void load() {
    if (CollectionUtils.isNullOrEmpty(accountsConfig.getAccounts())
        && (StringUtils.isEmpty(credentialsConfig.getDefaultAssumeRole()))) {
      accountsConfig.setAccounts(
          Collections.singletonList(
              new AccountsConfiguration.Account() {
                {
                  setName(defaultAccountConfigurationProperties.getEnv());
                  setEnvironment(defaultEnvironment);
                  setAccountType(defaultAccountType);
                }
              }));
      if (CollectionUtils.isNullOrEmpty(credentialsConfig.getDefaultRegions())) {
        List<Regions> regions =
            new ArrayList<>(
                Arrays.asList(
                    Regions.US_EAST_1, Regions.US_WEST_1, Regions.US_WEST_2, Regions.EU_WEST_1));
        credentialsConfig.setDefaultRegions(
            regions.stream()
                .map(
                    it ->
                        new CredentialsConfig.Region() {
                          {
                            setName(it.getName());
                          }
                        })
                .collect(Collectors.toList()));
      }
    }
    this.parse(definitionSource.getCredentialsDefinitions());
  }

  @Override
  protected void parse(Collection<T> definitions) {
    log.info("attempting to parse {} amazon accounts provided as input", definitions.size());
    Set<String> definitionNames = definitions.stream().map(T::getName).collect(Collectors.toSet());

    // TODO: make a change in BasicCredentialsLoader in kork to separate this out into a new method
    log.info(
        "removing all the accounts from the credentials repository that are not present in the provided input");
    credentialsRepository.getAll().stream()
        .map(Credentials::getName)
        .filter(name -> !definitionNames.contains(name))
        .peek(loadedDefinitions::remove)
        .forEach(credentialsRepository::delete);

    // adding this after the delete from credentials repository step. This is to ensure that if the
    // new input does not
    // contain any accounts, then that should be reflected in the credentials repository
    // appropriately
    if (definitionNames.size() == 0) {
      log.info("did not find any aws account definitions to parse");
      return;
    }

    List<U> toApply = new ArrayList<>();
    if (credentialsConfig.getLoadAccounts().isMultiThreadingEnabled()) {
      log.info(
          "Multi-threading is enabled for loading aws accounts. Using {} threads, with timeout: {}s",
          credentialsConfig.getLoadAccounts().getNumberOfThreads(),
          credentialsConfig.getLoadAccounts().getTimeoutInSeconds());
      toApply = multiThreadedParseAccounts(definitions);
    } else {
      log.info("Multi-threading is disabled. AWS accounts will be loaded serially");
      for (T definition : definitions) {
        if (!loadedDefinitions.containsKey(definition.getName())) {
          U cred = parser.parse(definition);
          if (cred != null) {
            toApply.add(cred);
            // Add to loaded definition now in case we trigger another parse before this one
            // finishes
            loadedDefinitions.put(definition.getName(), definition);
          }
        } else if (!loadedDefinitions.get(definition.getName()).equals(definition)) {
          U cred = parser.parse(definition);
          if (cred != null) {
            toApply.add(cred);
            loadedDefinitions.put(definition.getName(), definition);
          }
        }
      }
    }

    log.info("saving aws accounts in the credentials repository");
    Stream<U> stream = parallel ? toApply.parallelStream() : toApply.stream();
    stream.forEach(credentialsRepository::save);
    log.info("parsed and saved {} aws accounts", credentialsRepository.getAll().size());
  }

  /**
   * parses aws accounts using a configurable fixed thread pool.
   *
   * @param definitions - the list of aws accounts to parse
   * @return - a list of parsed aws accounts
   */
  private List<U> multiThreadedParseAccounts(Collection<T> definitions) {
    List<U> toApply = new ArrayList<>();
    final ExecutorService executorService =
        Executors.newFixedThreadPool(
            credentialsConfig.getLoadAccounts().getNumberOfThreads(),
            new ThreadFactoryBuilder()
                .setNameFormat(AmazonCredentialsParser.class.getSimpleName() + "-%d")
                .build());

    final ArrayList<Future<U>> futures = new ArrayList<>(definitions.size());
    for (T definition : definitions) {
      if (!loadedDefinitions.containsKey(definition.getName())
          || !loadedDefinitions.get(definition.getName()).equals(definition)) {
        futures.add(executorService.submit(() -> parser.parse(definition)));
      }
    }
    for (Future<U> future : futures) {
      try {
        U cred =
            future.get(credentialsConfig.getLoadAccounts().getTimeoutInSeconds(), TimeUnit.SECONDS);
        if (cred != null) {
          toApply.add(cred);
          // Add to loaded definition now in case we trigger another parse before this one finishes
          definitions.stream()
              .filter(t -> t.getName().equals(cred.getName()))
              .findFirst()
              .ifPresentOrElse(
                  definition -> loadedDefinitions.put(cred.getName(), definition),
                  () ->
                      log.warn(
                          "could not find the parsed aws account: '{}' in the input credential definitions.",
                          cred.getName()));
        }
      } catch (Exception e) {
        // failure to load an account should not prevent clouddriver from starting up.
        log.error("Failed to load aws account: ", e);
      }
    }
    try {
      // attempt to shutdown the executor service
      executorService.shutdownNow();
    } catch (Exception e) {
      log.error("Failed to shutdown the aws account loading executor service.", e);
    }

    return toApply;
  }
}
