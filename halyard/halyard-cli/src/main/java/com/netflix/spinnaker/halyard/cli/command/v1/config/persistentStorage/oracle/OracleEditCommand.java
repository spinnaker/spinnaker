/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.persistentStorage.oracle;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.persistentStorage.AbstractPersistentStoreEditCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.oracle.OracleCommandProperties;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStore;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.OraclePersistentStore;

@Parameters(separators = "=")
public class OracleEditCommand extends AbstractPersistentStoreEditCommand<OraclePersistentStore> {
  protected String getPersistentStoreType() {
    return PersistentStore.PersistentStoreType.ORACLE.getId();
  }

  @Parameter(
      names = "--compartment-id",
      description = OracleCommandProperties.COMPARTMENT_ID_DESCRIPTION)
  private String compartmentId;

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

  @Parameter(names = "--bucket-name", description = OracleCommandProperties.BUCKET_NAME_DESCRIPTION)
  private String bucketName;

  @Parameter(names = "--namespace", description = OracleCommandProperties.NAMESPACE_DESCRIPTION)
  private String namespace;

  @Override
  protected OraclePersistentStore editPersistentStore(OraclePersistentStore persistentStore) {
    persistentStore.setCompartmentId(
        isSet(compartmentId) ? compartmentId : persistentStore.getCompartmentId());
    persistentStore.setUserId(isSet(userId) ? userId : persistentStore.getUserId());
    persistentStore.setFingerprint(
        isSet(fingerprint) ? fingerprint : persistentStore.getFingerprint());
    persistentStore.setSshPrivateKeyFilePath(
        isSet(sshPrivateKeyFilePath)
            ? sshPrivateKeyFilePath
            : persistentStore.getSshPrivateKeyFilePath());
    persistentStore.setPrivateKeyPassphrase(
        isSet(privateKeyPassphrase)
            ? privateKeyPassphrase
            : persistentStore.getPrivateKeyPassphrase());
    persistentStore.setTenancyId(isSet(tenancyId) ? tenancyId : persistentStore.getTenancyId());
    persistentStore.setRegion(isSet(region) ? region : persistentStore.getRegion());
    persistentStore.setBucketName(isSet(bucketName) ? bucketName : persistentStore.getBucketName());
    persistentStore.setNamespace(isSet(namespace) ? namespace : persistentStore.getNamespace());

    return persistentStore;
  }
}
