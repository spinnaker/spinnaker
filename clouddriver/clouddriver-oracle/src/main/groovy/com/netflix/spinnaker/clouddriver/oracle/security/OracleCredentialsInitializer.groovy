/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.security

import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.clouddriver.oracle.config.OracleConfigurationProperties
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import groovy.util.logging.Slf4j
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Slf4j
@Configuration
class OracleCredentialsInitializer  {

  @Bean
  List<? extends OracleNamedAccountCredentials> oracleNamedAccountCredentials(
    String clouddriverUserAgentApplicationName, //see CloudDriverConfig.clouddriverUserAgentApplicationName
    OracleConfigurationProperties oracleConfigurationProperties,
    AccountCredentialsRepository accountCredentialsRepository
  ) {
    synchronizeOracleAccounts(clouddriverUserAgentApplicationName, oracleConfigurationProperties, null, accountCredentialsRepository)
  }

  private List<? extends OracleNamedAccountCredentials> synchronizeOracleAccounts(
    String clouddriverUserAgentApplicationName,
    OracleConfigurationProperties oracleConfigurationProperties,
    CatsModule catsModule,
    AccountCredentialsRepository accountCredentialsRepository) {

    def (ArrayList<OracleConfigurationProperties.ManagedAccount> accountsToAdd, List<String> namesOfDeletedAccounts) =
    ProviderUtils.calculateAccountDeltas(accountCredentialsRepository,
      OracleNamedAccountCredentials.class,
      oracleConfigurationProperties.accounts)

    accountsToAdd.each { OracleConfigurationProperties.ManagedAccount managedAccount ->
      try {
        def oracleAccount = new OracleNamedAccountCredentials.Builder().name(managedAccount.name).
          environment(managedAccount.environment ?: managedAccount.name).
          accountType(managedAccount.accountType ?: managedAccount.name).
          requiredGroupMembership(managedAccount.requiredGroupMembership).
          compartmentId(managedAccount.compartmentId).
          userId(managedAccount.userId).
          fingerprint(managedAccount.fingerprint).
          sshPrivateKeyFilePath(managedAccount.sshPrivateKeyFilePath).
          privateKeyPassphrase(managedAccount.privateKeyPassphrase).
          tenancyId(managedAccount.tenancyId).
          region(managedAccount.region).
          build()

        accountCredentialsRepository.save(managedAccount.name, oracleAccount)
      } catch (e) {
        log.warn("Could not load account $managedAccount.name for Oracle", e)
      }
    }

    ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule)

    accountCredentialsRepository.all.findAll {
      it instanceof OracleNamedAccountCredentials
    } as List<OracleNamedAccountCredentials>
  }
}
