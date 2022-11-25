/*
 * Copyright 2022 OpsMx
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

package com.netflix.spinnaker.clouddriver.cloudrun.config;

import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunJobExecutor;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.credentials.CredentialsTypeBaseConfiguration;
import com.netflix.spinnaker.credentials.CredentialsTypeProperties;
import com.netflix.spinnaker.kork.configserver.ConfigFileService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudrunCredentialsConfiguration {
  private static final Logger log = LoggerFactory.getLogger(CloudrunCredentialsConfiguration.class);

  @Bean
  public CredentialsTypeBaseConfiguration<
          CloudrunNamedAccountCredentials, CloudrunConfigurationProperties.ManagedAccount>
      cloudrunCredentialsProperties(
          ApplicationContext applicationContext,
          CloudrunConfigurationProperties configurationProperties,
          CloudrunJobExecutor jobExecutor,
          ConfigFileService configFileService,
          String clouddriverUserAgentApplicationName) {
    return new CredentialsTypeBaseConfiguration(
        applicationContext,
        CredentialsTypeProperties
            .<CloudrunNamedAccountCredentials, CloudrunConfigurationProperties.ManagedAccount>
                builder()
            .type(CloudrunNamedAccountCredentials.CREDENTIALS_TYPE)
            .credentialsDefinitionClass(CloudrunConfigurationProperties.ManagedAccount.class)
            .credentialsClass(CloudrunNamedAccountCredentials.class)
            .credentialsParser(
                a -> {
                  try {
                    String gcloudPath = configurationProperties.getGcloudPath();
                    if (StringUtils.isEmpty(gcloudPath)) {
                      gcloudPath = "gcloud";
                    }
                    a.initialize(jobExecutor, gcloudPath);
                    String jsonKey = configFileService.getContents(a.getJsonPath());
                    return new CloudrunNamedAccountCredentials.Builder()
                        .setName(a.getName())
                        .setEnvironment(
                            StringUtils.isEmpty(a.getEnvironment())
                                ? a.getName()
                                : a.getEnvironment())
                        .setAccountType(
                            StringUtils.isEmpty(a.getAccountType())
                                ? a.getName()
                                : a.getAccountType())
                        .setProject(a.getProject())
                        .setJsonKey(jsonKey)
                        .setApplicationName(clouddriverUserAgentApplicationName)
                        .setJsonPath(a.getJsonPath())
                        .setServiceAccountEmail(a.getServiceAccountEmail())
                        .setLocalRepositoryDirectory(a.getLocalRepositoryDirectory())
                        .setRequiredGroupMembership(a.getRequiredGroupMembership())
                        .setPermissions(a.getPermissions().build())
                        .build(jobExecutor);
                  } catch (Exception e) {
                    log.info(
                        String.format("Could not load account %s for Cloud Run", a.getName()), e);
                    return null;
                  }
                })
            .defaultCredentialsSource(configurationProperties::getAccounts)
            .build());
  }
}
