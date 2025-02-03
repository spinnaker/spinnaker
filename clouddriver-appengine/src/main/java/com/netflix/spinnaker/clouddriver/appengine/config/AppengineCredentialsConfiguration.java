/*
 * Copyright 2020 Armory
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

package com.netflix.spinnaker.clouddriver.appengine.config;

import com.netflix.spinnaker.clouddriver.appengine.AppengineCloudProvider;
import com.netflix.spinnaker.clouddriver.appengine.AppengineJobExecutor;
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.CredentialsTypeBaseConfiguration;
import com.netflix.spinnaker.credentials.CredentialsTypeProperties;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader;
import com.netflix.spinnaker.credentials.poller.Poller;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import com.netflix.spinnaker.kork.configserver.ConfigFileService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppengineCredentialsConfiguration {
  private static final Logger log =
      LoggerFactory.getLogger(AppengineCredentialsConfiguration.class);

  @Bean
  @ConditionalOnMissingBean(
      value = AppengineNamedAccountCredentials.class,
      parameterizedContainer = CredentialsRepository.class)
  public CredentialsRepository<AppengineNamedAccountCredentials> appengineCredentialsRepository(
      CredentialsLifecycleHandler<AppengineNamedAccountCredentials> eventHandler) {
    return new MapBackedCredentialsRepository<>(AppengineCloudProvider.getID(), eventHandler);
  }

  @Bean
  public CredentialsTypeBaseConfiguration<
          AppengineNamedAccountCredentials, AppengineConfigurationProperties.ManagedAccount>
      appengineCredentialsProperties(
          ApplicationContext applicationContext,
          AppengineConfigurationProperties configurationProperties,
          AppengineJobExecutor jobExecutor,
          ConfigFileService configFileService,
          String clouddriverUserAgentApplicationName,
          ServiceClientProvider serviceClientProvider) {
    return new CredentialsTypeBaseConfiguration(
        applicationContext,
        CredentialsTypeProperties
            .<AppengineNamedAccountCredentials, AppengineConfigurationProperties.ManagedAccount>
                builder()
            .type(AppengineNamedAccountCredentials.CREDENTIALS_TYPE)
            .credentialsDefinitionClass(AppengineConfigurationProperties.ManagedAccount.class)
            .credentialsClass(AppengineNamedAccountCredentials.class)
            .credentialsParser(
                a -> {
                  try {
                    String gcloudPath = configurationProperties.getGcloudPath();
                    if (StringUtils.isEmpty(gcloudPath)) {
                      gcloudPath = "gcloud";
                    }
                    a.initialize(jobExecutor, gcloudPath, serviceClientProvider);

                    String jsonKey = configFileService.getContents(a.getJsonPath());
                    return new AppengineNamedAccountCredentials.Builder()
                        .name(a.getName())
                        .environment(
                            StringUtils.isEmpty(a.getEnvironment())
                                ? a.getName()
                                : a.getEnvironment())
                        .accountType(
                            StringUtils.isEmpty(a.getAccountType())
                                ? a.getName()
                                : a.getAccountType())
                        .project(a.getProject())
                        .jsonKey(jsonKey)
                        .applicationName(clouddriverUserAgentApplicationName)
                        .gcloudPath(gcloudPath)
                        .jsonPath(a.getJsonPath())
                        .requiredGroupMembership(a.getRequiredGroupMembership())
                        .permissions(a.getPermissions().build())
                        .serviceAccountEmail(a.getComputedServiceAccountEmail())
                        .localRepositoryDirectory(a.getLocalRepositoryDirectory())
                        .gitHttpsUsername(a.getGitHttpsUsername())
                        .gitHttpsPassword(a.getGitHttpsPassword())
                        .githubOAuthAccessToken(a.getGithubOAuthAccessToken())
                        .sshPrivateKeyFilePath(a.getSshPrivateKeyFilePath())
                        .sshPrivateKeyPassphrase(a.getSshPrivateKeyPassphrase())
                        .sshKnownHostsFilePath(a.getSshKnownHostsFilePath())
                        .sshTrustUnknownHosts(a.isSshTrustUnknownHosts())
                        .gcloudReleaseTrack(a.getGcloudReleaseTrack())
                        .services(a.getServices())
                        .versions(a.getVersions())
                        .omitServices(a.getOmitServices())
                        .omitVersions(a.getOmitVersions())
                        .cachingIntervalSeconds(a.getCachingIntervalSeconds())
                        .build();
                  } catch (Exception e) {
                    log.info(
                        String.format("Could not load account %s for App Engine", a.getName()), e);
                    return null;
                  }
                })
            .defaultCredentialsSource(configurationProperties::getAccounts)
            .build());
  }

  @Bean
  public CredentialsInitializerSynchronizable appengineCredentialsInitializerSynchronizable(
      AbstractCredentialsLoader<AppengineNamedAccountCredentials> loader) {
    final Poller<AppengineNamedAccountCredentials> poller = new Poller<>(loader);
    return new CredentialsInitializerSynchronizable() {
      @Override
      public void synchronize() {
        poller.run();
      }
    };
  }
}
