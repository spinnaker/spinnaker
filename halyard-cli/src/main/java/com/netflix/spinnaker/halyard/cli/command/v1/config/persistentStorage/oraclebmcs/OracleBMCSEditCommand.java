/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.persistentStorage.oraclebmcs;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.persistentStorage.AbstractPersistentStoreEditCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.oraclebmcs.OracleBMCSCommandProperties;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.PathExpandingConverter;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStore;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.OracleBMCSPersistentStore;

@Parameters(separators = "=")
public class OracleBMCSEditCommand extends AbstractPersistentStoreEditCommand<OracleBMCSPersistentStore> {
  protected String getPersistentStoreType() {
    return PersistentStore.PersistentStoreType.ORACLEBMCS.getId();
  }

  @Parameter(
          names = "--compartment-id",
          description = OracleBMCSCommandProperties.COMPARTMENT_ID_DESCRIPTION
  )
  private String compartmentId;

  @Parameter(
          names = "--user-id",
          description = OracleBMCSCommandProperties.USER_ID_DESCRIPTION
  )
  private String userId;

  @Parameter(
          names = "--fingerprint",
          description = OracleBMCSCommandProperties.FINGERPRINT_DESCRIPTION
  )
  private String fingerprint;

  @Parameter(
          names = "--ssh-private-key-file-path",
          converter = PathExpandingConverter.class,
          description = OracleBMCSCommandProperties.SSH_PRIVATE_KEY_FILE_PATH_DESCRIPTION
  )
  private String sshPrivateKeyFilePath;

  @Parameter(
          names = "--tenancy-id",
          description = OracleBMCSCommandProperties.TENANCY_ID_DESCRIPTION
  )
  private String tenancyId;

  @Parameter(
          names = "--region",
          description = OracleBMCSCommandProperties.REGION_DESCRIPTION
  )
  private String region;

  @Parameter(
          names = "--bucket-name",
          description = "The bucket name to store persistent state object in"
  )
  private String bucketName;

  @Parameter(
          names = "--namespace",
          description = "The namespace the bucket and objects should be created in"
  )
  private String namespace;

  @Override
  protected OracleBMCSPersistentStore editPersistentStore(OracleBMCSPersistentStore persistentStore) {
    persistentStore.setCompartmentId(isSet(compartmentId) ? compartmentId : persistentStore.getCompartmentId());
    persistentStore.setUserId(isSet(userId) ? userId : persistentStore.getUserId());
    persistentStore.setFingerprint(isSet(fingerprint) ? fingerprint : persistentStore.getFingerprint());
    persistentStore.setSshPrivateKeyFilePath(isSet(sshPrivateKeyFilePath) ? sshPrivateKeyFilePath : persistentStore.getSshPrivateKeyFilePath());
    persistentStore.setTenancyId(isSet(tenancyId) ? tenancyId : persistentStore.getTenancyId());
    persistentStore.setRegion(isSet(region) ? region : persistentStore.getRegion());
    persistentStore.setBucketName(isSet(bucketName) ? bucketName : persistentStore.getBucketName());
    persistentStore.setNamespace(isSet(namespace) ? namespace : persistentStore.getNamespace());

    return persistentStore;
  }
}
