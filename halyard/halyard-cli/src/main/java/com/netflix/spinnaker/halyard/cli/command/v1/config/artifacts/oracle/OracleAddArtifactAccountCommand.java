/*
 * Copyright (c) 2017, 2018, Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.oracle;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.account.AbstractAddArtifactAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.oracle.OracleCommandProperties;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.oracle.OracleArtifactAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactAccount;

@Parameters(separators = "=")
public class OracleAddArtifactAccountCommand extends AbstractAddArtifactAccountCommand {
  @Parameter(names = "--user-id", description = OracleCommandProperties.USER_ID_DESCRIPTION)
  private String userId;

  @Parameter(names = "--fingerprint", description = OracleCommandProperties.FINGERPRINT_DESCRIPTION)
  private String fingerprint;

  @Parameter(
      names = "--ssh-private-key-file-path",
      converter = LocalFileConverter.class,
      description = OracleCommandProperties.SSH_PRIVATE_KEY_FILE_PATH_DESCRIPTION)
  private String sshPrivateKeyFilePath;

  @Parameter(
      names = "--private-key-passphrase",
      description = OracleCommandProperties.PRIVATE_KEY_PASSPHRASE_DESCRIPTION,
      password = true)
  private String privateKeyPassphrase;

  @Parameter(names = "--tenancy-id", description = OracleCommandProperties.TENANCY_ID_DESCRIPTION)
  private String tenancyId;

  @Parameter(names = "--region", description = OracleCommandProperties.REGION_DESCRIPTION)
  private String region;

  @Parameter(names = "--namespace", description = OracleCommandProperties.NAMESPACE_DESCRIPTION)
  private String namespace;

  @Override
  protected ArtifactAccount buildArtifactAccount(String accountName) {
    OracleArtifactAccount account = new OracleArtifactAccount().setName(accountName);

    account.setUserId(userId);
    account.setFingerprint(fingerprint);
    account.setSshPrivateKeyFilePath(sshPrivateKeyFilePath);
    account.setPrivateKeyPassphrase(privateKeyPassphrase);
    account.setTenancyId(tenancyId);
    account.setRegion(region);
    account.setNamespace(namespace);

    return account;
  }

  @Override
  protected ArtifactAccount emptyArtifactAccount() {
    return new OracleArtifactAccount();
  }

  @Override
  protected String getArtifactProviderName() {
    return "oracle";
  }
}
