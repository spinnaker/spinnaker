/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.security

import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.clouddriver.oraclebmcs.config.OracleBMCSConfigurationProperties
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope

@Slf4j
@Configuration
class OracleBMCSCredentialsInitializer implements CredentialsInitializerSynchronizable {

  @Bean
  List<? extends OracleBMCSNamedAccountCredentials> oracleBMCSNamedAccountCredentials(
    String clouddriverUserAgentApplicationName,
    OracleBMCSConfigurationProperties oracleBMCSConfigurationProperties,
    AccountCredentialsRepository accountCredentialsRepository
  ) {
    synchronizeOracleBMCSAccounts(clouddriverUserAgentApplicationName, oracleBMCSConfigurationProperties, null, accountCredentialsRepository)
  }

  @Override
  String getCredentialsSynchronizationBeanName() {
    return "synchronizeOracleBMCSAccounts"
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  List<? extends OracleBMCSNamedAccountCredentials> synchronizeOracleBMCSAccounts(String clouddriverUserAgentApplicationName,
                                                                                  OracleBMCSConfigurationProperties oracleBMCSConfigurationProperties,
                                                                                  CatsModule catsModule,
                                                                                  AccountCredentialsRepository accountCredentialsRepository) {

    def (ArrayList<OracleBMCSConfigurationProperties.ManagedAccount> accountsToAdd, List<String> namesOfDeletedAccounts) =
    ProviderUtils.calculateAccountDeltas(accountCredentialsRepository,
      OracleBMCSNamedAccountCredentials.class,
      oracleBMCSConfigurationProperties.accounts)

    accountsToAdd.each { OracleBMCSConfigurationProperties.ManagedAccount managedAccount ->
      try {
        def oracleBMCSAccount = new OracleBMCSNamedAccountCredentials.Builder().name(managedAccount.name).
          environment(managedAccount.environment ?: managedAccount.name).
          accountType(managedAccount.accountType ?: managedAccount.name).
          requiredGroupMembership(managedAccount.requiredGroupMembership).
          compartmentId(managedAccount.compartmentId).
          userId(managedAccount.userId).
          fingerprint(managedAccount.fingerprint).
          sshPrivateKeyFilePath(managedAccount.sshPrivateKeyFilePath).
          tenancyId(managedAccount.tenancyId).
          region(managedAccount.region).
          build()

        accountCredentialsRepository.save(managedAccount.name, oracleBMCSAccount)
      } catch (e) {
        log.warn("Could not load account $managedAccount.name for OracleBMCS", e)
      }
    }

    ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule)

    accountCredentialsRepository.all.findAll {
      it instanceof OracleBMCSNamedAccountCredentials
    } as List<OracleBMCSNamedAccountCredentials>
  }
}
