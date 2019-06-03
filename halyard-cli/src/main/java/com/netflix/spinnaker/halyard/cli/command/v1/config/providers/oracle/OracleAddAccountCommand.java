/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.oracle;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractAddAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.oracle.OracleAccount;

@Parameters(separators = "=")
public class OracleAddAccountCommand extends AbstractAddAccountCommand {
  protected String getProviderName() {
    return Provider.ProviderType.ORACLE.getName();
  }

  @Parameter(
      names = "--compartment-id",
      description = OracleCommandProperties.COMPARTMENT_ID_DESCRIPTION,
      required = true)
  private String compartmentId;

  @Parameter(
      names = "--user-id",
      description = OracleCommandProperties.USER_ID_DESCRIPTION,
      required = true)
  private String userId;

  @Parameter(
      names = "--fingerprint",
      description = OracleCommandProperties.FINGERPRINT_DESCRIPTION,
      required = true)
  private String fingerprint;

  @Parameter(
      names = "--ssh-private-key-file-path",
      converter = LocalFileConverter.class,
      description = OracleCommandProperties.SSH_PRIVATE_KEY_FILE_PATH_DESCRIPTION,
      required = true)
  private String sshPrivateKeyFilePath;

  @Parameter(
      names = "--private-key-passphrase",
      description = OracleCommandProperties.PRIVATE_KEY_PASSPHRASE_DESCRIPTION,
      password = true)
  private String privateKeyPassphrase;

  @Parameter(
      names = "--tenancyId",
      description = OracleCommandProperties.TENANCY_ID_DESCRIPTION,
      required = true)
  private String tenancyId;

  @Parameter(
      names = "--region",
      description = OracleCommandProperties.REGION_DESCRIPTION,
      required = true)
  private String region;

  @Override
  protected Account buildAccount(String accountName) {
    OracleAccount account = (OracleAccount) new OracleAccount().setName(accountName);
    account.setCompartmentId(compartmentId);
    account.setUserId(userId);
    account.setFingerprint(fingerprint);
    account.setSshPrivateKeyFilePath(sshPrivateKeyFilePath);
    account.setPrivateKeyPassphrase(privateKeyPassphrase);
    account.setTenancyId(tenancyId);
    account.setRegion(region);

    return account;
  }

  @Override
  protected Account emptyAccount() {
    return new OracleAccount();
  }
}
